package org.niord.core.publication.series.legacy;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.niord.core.mailinglist.MailingListTrigger;
import org.niord.core.publication.series.PublicWindowSource;
import org.niord.core.publication.series.PublicationIssue;
import org.niord.core.publication.series.PublicationSeries;
import org.niord.core.publication.series.SeriesCadence;
import org.niord.core.service.BaseService;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * B5.7. The assertions that must hold before the cutover flip, and the audit
 * that must be READ before it.
 *
 * Run against the imported estate, not against a fixture. Everything here is
 * about the shape of ~1,077 real rows, and a fixture of three cannot be wrong in
 * the ways this is looking for.
 *
 * The pass EXITS NON-ZERO on violation rather than warning, because the thing it
 * guards is one-way: after B7.1 flips publicAuthority, a wrong window or a
 * colliding id is serving the public, and the fix is a migration rather than an
 * edit.
 */
@ApplicationScoped
public class CutoverPreflightService extends BaseService {

    @Inject
    Logger log;

    /**
     * A tag named the way the weekly convention names them.
     *
     * nm-w27-2026, nm-pt-w27-2026 -- and the double-week forms the estate also
     * carries. Matched loosely on purpose: this is a report for a human to read,
     * and a miss here is a mailing list that silently stops going out, while a
     * false positive is one line for somebody to dismiss.
     */
    private static final Pattern WEEKLY_TAG =
            Pattern.compile("tag=[\"']?(nm-(pt-)?w\\d{1,2}(-\\d{1,2})?-\\d{4})", Pattern.CASE_INSENSITIVE);

    /** One thing that is wrong, named so somebody can act on it. */
    public record Violation(String code, String subject, String detail) {
    }

    /** What the pre-flight found. Empty problems means cleared to flip. */
    public record Preflight(List<Violation> violations, List<TriggerHit> triggerAudit,
                            Map<String, Integer> counts) {

        public boolean isClear() {
            return violations.isEmpty();
        }
    }

    /** A mailing-list trigger that names a weekly tag by hand. */
    public record TriggerHit(String mailingListId, String triggerType, String matchedTag,
                             String messageQuery) {
    }

    /** Runs every assertion and the audit. Reads only. */
    public Preflight run() {
        List<Violation> violations = new ArrayList<>();
        Map<String, Integer> counts = new LinkedHashMap<>();

        List<PublicationIssue> imported = em.createQuery(
                        "SELECT i FROM PublicationIssue i WHERE i.legacyPublicationId IS NOT NULL",
                        PublicationIssue.class)
                .getResultList();
        counts.put("importedIssues", imported.size());

        assertOneCurrentIssuePerSeries(imported, violations, counts);
        assertCadencedIssuesDeriveTheirWindow(imported, violations);
        assertIdSpaceDoesNotCollide(violations, counts);

        List<TriggerHit> audit = auditTriggers();
        counts.put("triggersNamingAWeeklyTag", audit.size());

        log.info("cutover pre-flight: {} violation(s), {} trigger(s) naming a weekly tag",
                violations.size(), audit.size());
        return new Preflight(violations, audit, counts);
    }

    /**
     * I-18: a series has at most one issue with publicTo IS NULL.
     *
     * That issue is the current one. Two of them means two editions are current
     * at once, which on the public site reads as the archive having forked.
     */
    private void assertOneCurrentIssuePerSeries(List<PublicationIssue> imported,
                                                List<Violation> violations,
                                                Map<String, Integer> counts) {
        Map<String, List<PublicationIssue>> openEnded = new LinkedHashMap<>();
        for (PublicationIssue i : imported) {
            if (i.getPublicTo() == null && i.getSeries() != null) {
                openEnded.computeIfAbsent(i.getSeries().getSeriesId(), k -> new ArrayList<>()).add(i);
            }
        }
        counts.put("seriesWithACurrentIssue", openEnded.size());

        openEnded.forEach((seriesId, issues) -> {
            if (issues.size() > 1) {
                violations.add(new Violation("I18_TWO_CURRENT_ISSUES", seriesId,
                        issues.size() + " issues carry publicTo IS NULL: "
                                + issues.stream().map(PublicationIssue::getPublicId).toList()
                                + ". Two current editions on one series is the archive forking in "
                                + "public."));
            }
        });
    }

    /**
     * R8: the newest imported issue of a cadenced series derives its window.
     *
     * A cadenced issue left MANUAL is not capped when its successor publishes, so
     * the first native publish leaves two current editions on the public site --
     * the failure I-18 above describes, arriving by a different route.
     */
    private void assertCadencedIssuesDeriveTheirWindow(List<PublicationIssue> imported,
                                                       List<Violation> violations) {
        for (PublicationIssue i : imported) {
            PublicationSeries s = i.getSeries();
            boolean cadenced = s != null && s.getCadence() != null
                    && s.getCadence() != SeriesCadence.NONE;
            if (cadenced && i.getPublicWindowSource() != PublicWindowSource.DERIVED) {
                violations.add(new Violation("R8_CADENCED_ISSUE_IS_MANUAL", i.getPublicId(),
                        "series '" + s.getSeriesId() + "' has a cadence, so this issue's window must "
                                + "be DERIVED; MANUAL means the first native publish will not cap it."));
            }
        }
    }

    /**
     * X-1: publicId is unique across the new and the legacy id spaces.
     *
     * They share one keyspace by design -- an imported issue keeps the legacy
     * publicationId verbatim, which is what keeps citations resolving. A
     * collision would make one id mean two documents, and the resolver would
     * answer with whichever it found first.
     */
    private void assertIdSpaceDoesNotCollide(List<Violation> violations, Map<String, Integer> counts) {
        List<String> collisions = em.createQuery(
                        "SELECT i.publicId FROM PublicationIssue i GROUP BY i.publicId "
                                + "HAVING COUNT(i) > 1", String.class)
                .getResultList();
        collisions.forEach(id -> violations.add(new Violation("X1_PUBLIC_ID_COLLIDES", id,
                "more than one issue carries this publicId; one id would mean two documents")));

        // A publicId that is NOT an import must not reuse a legacy publicationId.
        List<String> reused = em.createQuery(
                        "SELECT i.publicId FROM PublicationIssue i WHERE i.legacyPublicationId IS NULL "
                                + "AND EXISTS (SELECT 1 FROM Publication p WHERE p.publicationId = i.publicId)",
                        String.class)
                .getResultList();
        reused.forEach(id -> violations.add(new Violation("X1_NATIVE_ID_REUSES_LEGACY", id,
                "a natively created issue carries an id that a legacy publication also uses")));

        counts.put("idCollisions", collisions.size() + reused.size());
    }

    /**
     * The trigger audit. A REPORT, not a rewrite -- closes G-12.
     *
     * B4.4 cured `publication=` in mailing-list triggers. Nothing audits the
     * triggers that name a TAG. After C8 no new nm-wNN-YYYY tag is minted, so a
     * mailing list keyed on the naming convention silently stops matching and
     * nobody is told: the failure is a mailing that does not go out, which
     * nobody notices until somebody asks why they stopped receiving it.
     *
     * What to do with each hit is the user's call, made before B7.1. This only
     * makes sure the question is asked.
     */
    public List<TriggerHit> auditTriggers() {
        List<TriggerHit> hits = new ArrayList<>();

        List<MailingListTrigger> triggers = em.createQuery(
                        "SELECT t FROM MailingListTrigger t WHERE t.messageQuery IS NOT NULL",
                        MailingListTrigger.class)
                .getResultList();

        for (MailingListTrigger t : triggers) {
            var m = WEEKLY_TAG.matcher(t.getMessageQuery());
            if (m.find()) {
                hits.add(new TriggerHit(
                        t.getMailingList() == null ? null : t.getMailingList().getMailingListId(),
                        t.getType() == null ? null : t.getType().name(),
                        m.group(1),
                        t.getMessageQuery()));
            }
        }
        return hits;
    }
}
