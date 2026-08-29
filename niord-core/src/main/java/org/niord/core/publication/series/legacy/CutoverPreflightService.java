/*
 * Copyright 2026 Danish Emergency Management Agency.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.niord.core.publication.series.legacy;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.niord.core.mailinglist.MailingListTrigger;
import org.niord.core.publication.series.IssueListService;
import org.niord.core.publication.series.IssueStatus;
import org.niord.core.publication.series.PublicWindowSource;
import org.niord.core.publication.series.PublicationIssue;
import org.niord.core.publication.series.PublicationSeries;
import org.niord.core.publication.series.SeriesCadence;
import org.niord.core.publication.series.SeriesKind;
import org.niord.core.publication.series.replay.ShadowDiffRun;
import org.niord.core.publication.series.replay.ShadowDiffService;
import org.niord.core.publication.series.vo.IssueListResultVo;
import org.niord.core.service.BaseService;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The assertions that must hold before the cutover flip, and the audit
 * that must be READ before it.
 *
 * Run against the imported estate, not against a fixture. Everything here is
 * about the shape of ~1,077 real rows, and a fixture of three cannot be wrong in
 * the ways this is looking for.
 *
 * The pass EXITS NON-ZERO on violation rather than warning, because the thing it
 * guards is one-way: once the flip has moved publicAuthority, a wrong window or a
 * colliding id is serving the public, and the fix is a migration rather than an
 * edit.
 */
@ApplicationScoped
public class CutoverPreflightService extends BaseService {

    @Inject
    Logger log;

    @Inject
    ShadowDiffService shadowDiff;

    /**
     * A tag named the way the weekly convention names them.
     *
     * nm-w27-2026, nm-pt-w27-2026, and the double-week forms the estate also
     * carries.
     *
     * NOT anchored on "tag=". Twelve of the fifteen live triggers express
     * themselves in messageFilter, which is a script expression -- a tag appears
     * there quoted, not as a query parameter. Anchoring on tag= was how the first
     * version of this audit came back clean having read a fifth of the triggers.
     *
     * Matched loosely on purpose: a miss is a mailing list that silently stops
     * going out, a false positive is one line for somebody to dismiss.
     */
    private static final Pattern WEEKLY_TAG = Pattern.compile(
            "(nm-(?:pt-)?w\\d{1,2}(?:-\\d{1,2})?-\\d{4})", Pattern.CASE_INSENSITIVE);

    /** One thing that is wrong, named so somebody can act on it. */
    public record Violation(String code, String subject, String detail) {
    }

    /**
     * What the rehearsal checklist reads about ONE series, in one row.
     *
     * Three separate steps of the checklist ask about the same series -- the
     * shadow diff's verdict, how many periods the archive is missing, and what
     * kind of publication it is -- and reading them from three endpoints is how a
     * sheet gets ticked from three different moments. One row, one request.
     *
     * NONE OF IT IS A VIOLATION. A gap is a fact about the archive that predates
     * this system, and readiness is the flip's own precondition, refused at the
     * flip. Folding either into `clear` would stop the pre-flight passing on an
     * estate that is in exactly the state everybody expects.
     */
    public record SeriesRow(String seriesId, String status, String kind,
                            int consecutiveGreen, long runs, long skipped, boolean exempt,
                            boolean meetsCutoverPrecondition,
                            Integer gapCount, boolean gapDetectionEnabled, String gapReasonCode) {
    }

    /** What the pre-flight found. Empty problems means cleared to flip. */
    public record Preflight(List<Violation> violations, List<TriggerHit> triggerAudit,
                            Map<String, Integer> counts, Map<String, SeriesRow> series) {

        public boolean isClear() {
            return violations.isEmpty();
        }
    }

    /** A mailing-list trigger that names a weekly tag by hand. */
    public record TriggerHit(String mailingListId, String triggerType, String field,
                             String matchedTag, String expression) {
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

        assertEverySeriesHasAnOwner(violations, counts);
        assertOneCurrentIssuePerSeries(imported, violations, counts);
        assertCadencedIssuesDeriveTheirWindow(imported, violations);
        assertUnpublishedIssuesCarryNoStamp(imported, violations, counts);
        assertIdSpaceDoesNotCollide(violations, counts);
        assertMembershipIsUnique(violations, counts);

        List<TriggerHit> audit = auditTriggers();
        counts.put("triggersNamingAWeeklyTag", audit.size());

        Map<String, SeriesRow> series = describeSeries(counts, new Date());

        log.info("cutover pre-flight: {} violation(s), {} trigger(s) naming a weekly tag, "
                        + "{} series described", violations.size(), audit.size(), series.size());
        return new Preflight(violations, audit, counts, series);
    }

    /**
     * The per-series sheet: the diff's verdict, the missing periods, and the kind.
     *
     * Steps 4 and 5 of the rehearsal read these, and both were previously only
     * answerable by calling two other endpoints and counting rows in a third. A
     * checklist ticked from three moments is a checklist that can be ticked
     * against three different states of the estate.
     *
     * The gap count is the ISSUE LIST'S OWN, computed by the same builder the
     * list uses rather than by a second walk over the intervals. That matters more
     * than the duplication it saves: the coverage rule -- a double-week issue
     * leaves nothing uncovered, an OPEN newest issue suppresses the rows after it
     * -- is the whole reason the numbers on the sheet and the rows on the screen
     * ever agreed, and a second implementation of it is a second answer.
     *
     * Absent, not zero, where gap detection did not run: a closed gate is the
     * absence of a finding, and reporting it as "0 gaps" claims something nobody
     * checked. The reason code travels beside it so the sheet says which.
     */
    private Map<String, SeriesRow> describeSeries(Map<String, Integer> counts, Date now) {
        List<PublicationSeries> all = em.createNamedQuery(
                        "PublicationSeries.findAllOrdered", PublicationSeries.class)
                .getResultList();

        // One query for every issue, grouped in memory, rather than one per
        // series: the estate is ~1,100 issues over ~50 series, and the pre-flight
        // is run inside a cutover window where a minute of round trips is a minute
        // nobody has.
        Map<String, List<PublicationIssue>> issuesBySeries = new LinkedHashMap<>();
        for (PublicationIssue i : em.createQuery(
                        "SELECT i FROM PublicationIssue i ORDER BY "
                                + "COALESCE(i.cutoffStampedAt, i.intervalTo) DESC, i.publicId DESC",
                        PublicationIssue.class).getResultList()) {
            if (i.getSeries() != null) {
                issuesBySeries.computeIfAbsent(i.getSeries().getSeriesId(), k -> new ArrayList<>()).add(i);
            }
        }

        // The same grouping the shadow-diff endpoint does, from one query, so
        // readiness here and readiness there are the same computation over the
        // same runs.
        Map<String, List<ShadowDiffRun>> runsBySeries = new LinkedHashMap<>();
        for (ShadowDiffRun run : shadowDiff.all()) {
            String key = run.getSeriesId() == null ? "(unmapped)" : run.getSeriesId();
            runsBySeries.computeIfAbsent(key, k -> new ArrayList<>()).add(run);
        }

        Map<String, SeriesRow> out = new LinkedHashMap<>();
        Map<String, Integer> kinds = new LinkedHashMap<>();
        for (SeriesKind kind : SeriesKind.values()) {
            // Every kind is named whether or not the estate has one. A kind absent
            // from the report and a kind with no series read alike on a sheet, and
            // step 5 is ticked by comparing the three numbers against expected ones.
            kinds.put(kind.name(), 0);
        }
        int totalGaps = 0;

        for (PublicationSeries s : all) {
            ShadowDiffService.Readiness readiness = ShadowDiffService.readinessOf(
                    runsBySeries.getOrDefault(s.getSeriesId(), List.of()), s);

            IssueListResultVo list = IssueListService.build(s,
                    issuesBySeries.getOrDefault(s.getSeriesId(), List.of()), now);

            SeriesKind kind = s.getKind() == null ? SeriesKind.SCHEDULED : s.getKind();
            kinds.merge(kind.name(), 1, Integer::sum);
            if (list.getGapCount() != null) {
                totalGaps += list.getGapCount();
            }

            out.put(s.getSeriesId(), new SeriesRow(
                    s.getSeriesId(),
                    s.getStatus() == null ? null : s.getStatus().name(),
                    kind.name(),
                    readiness.consecutiveGreen(), readiness.runs(), readiness.skipped(),
                    readiness.exempt(), readiness.ready(),
                    list.getGapCount(),
                    list.getGapDetection() != null && list.getGapDetection().isEnabled(),
                    list.getGapDetection() == null ? null : list.getGapDetection().getReasonCode()));
        }

        counts.put("series", all.size());
        kinds.forEach((kind, n) -> counts.put("seriesOfKind" + kind, n));
        counts.put("uncoveredPeriods", totalGaps);
        return out;
    }

    /**
     * One message is in one issue once, and one override names it once.
     *
     * The data model specifies both as UNIQUE constraints and neither exists in
     * the schema. Adding a unique constraint to a populated table is a claim about
     * the DATA, not the schema: one duplicate and the ALTER fails and takes the
     * deploy with it. So the claim is measured here first, over the whole estate,
     * on the checklist an admin already runs before cutover.
     *
     * Counted rather than only flagged, because zero is the finding. "No
     * duplicates" is what licenses the constraint; a count of zero says the
     * question was asked, where a missing violation could equally mean nobody
     * looked.
     *
     * A duplicate is a real defect independently of the constraint: two rows for
     * one message in one issue print it twice in the report, and the member count
     * an editor reads disagrees with what comes out.
     */
    /**
     * Every publication names the desk that owns it.
     *
     * A VIOLATION, not a note, and the count must read zero before the flip. The
     * owner decides three things at once -- which admin list the publication
     * appears on, who may change it, and the timezone its cut-offs are reckoned in
     * -- so a row without one is a publication nobody is responsible for whose
     * schedule is read in whatever zone the server happens to be set to. After the
     * flip it is also what the public reads.
     *
     * Counted even when it is zero. An absent number and a number that is zero
     * read alike on a checklist, and this is a line somebody ticks.
     */
    private void assertEverySeriesHasAnOwner(List<Violation> violations, Map<String, Integer> counts) {
        reportOwnerless(em.createQuery(
                        "SELECT s.seriesId FROM PublicationSeries s WHERE s.domain IS NULL",
                        String.class)
                .getResultList(), violations, counts);
    }

    /** The wire code for a publication that belongs to no domain. */
    public static final String SERIES_WITHOUT_OWNER = "SERIES_WITHOUT_OWNER";

    /** The counts key the checklist reads, whatever the number turns out to be. */
    public static final String OWNERLESS_COUNT = "seriesWithoutOwner";

    /**
     * The finding, separated from the query that feeds it.
     *
     * SO IT CAN BE ASSERTED AT ALL. The owner column is NOT NULL wherever V13
     * could apply it -- which includes the test database -- so the real query can
     * only ever return an empty list there, and a test driving the whole pass
     * would assert that zero findings look right. That is a test of nothing. What
     * matters is the shape of a finding when there IS one: the code a checklist
     * greps for, the key the count lives under, and a message that tells whoever
     * reads it what to do. Those are asserted here with the count supplied.
     *
     * Package-visible rather than public: this is a seam for the test, not a
     * second way to raise a pre-flight violation.
     */
    static void reportOwnerless(List<String> ownerlessSeriesIds,
                                List<Violation> violations, Map<String, Integer> counts) {
        counts.put(OWNERLESS_COUNT, ownerlessSeriesIds.size());
        for (String seriesId : ownerlessSeriesIds) {
            violations.add(new Violation(SERIES_WITHOUT_OWNER, seriesId,
                    "the publication names no domain. Every publication belongs to exactly one -- "
                            + "the desk that lists and administers it, and the only source of the "
                            + "timezone its cut-offs are read in. Assign one before the flip."));
        }
    }

    private void assertMembershipIsUnique(List<Violation> violations, Map<String, Integer> counts) {
        List<Object[]> memberDupes = em.createQuery(
                        "SELECT m.issue.id, m.messageUid, COUNT(m) FROM IssueMember m "
                                + "GROUP BY m.issue.id, m.messageUid HAVING COUNT(m) > 1",
                        Object[].class)
                .getResultList();
        counts.put("duplicateMemberships", memberDupes.size());

        for (Object[] row : memberDupes) {
            violations.add(new Violation("X2_DUPLICATE_MEMBERSHIP", String.valueOf(row[0]),
                    "message " + row[1] + " is a member of this issue " + row[2] + " times. It "
                            + "prints twice, and the member count disagrees with the document."));
        }

        List<Object[]> overrideDupes = em.createQuery(
                        "SELECT o.issue.id, o.messageUid, COUNT(o) FROM IssueOverride o "
                                + "GROUP BY o.issue.id, o.messageUid HAVING COUNT(o) > 1",
                        Object[].class)
                .getResultList();
        counts.put("duplicateOverrides", overrideDupes.size());

        for (Object[] row : overrideDupes) {
            violations.add(new Violation("X2_DUPLICATE_OVERRIDE", String.valueOf(row[0]),
                    "message " + row[1] + " carries " + row[2] + " overrides on this issue. Which "
                            + "one applies is whichever the query returned first."));
        }
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
     * An OPEN issue carries no cut-off stamp, and none dated before its own interval.
     *
     * cutoffStampedAt means "the publish action recorded this instant". An OPEN
     * issue has not been published, so there is no such instant, and a value here
     * is evidence of something that did not happen.
     *
     * It reached the estate through the recovery cascade, which read `updated` --
     * a column every row has. On a never-published issue that is when the row was
     * CREATED, and the thing that created it was the release of the issue BEFORE
     * it. So all four OPEN issues took their predecessor's release instant, to
     * the millisecond, and three of them ended up dated before their own interval
     * had opened -- the 2027 firing-areas issue by a full year.
     *
     * The second assertion is the one that generalises: whatever a cut-off's
     * provenance, an issue whose period closed before it began is not a date
     * anybody can act on, and it silently mis-sorts the row and mis-anchors every
     * gap measured from it.
     */
    private void assertUnpublishedIssuesCarryNoStamp(List<PublicationIssue> imported,
                                                     List<Violation> violations,
                                                     Map<String, Integer> counts) {
        int open = 0;
        for (PublicationIssue i : imported) {
            if (i.getStatus() == IssueStatus.OPEN) {
                open++;
                if (i.getCutoffStampedAt() != null) {
                    violations.add(new Violation("OPEN_ISSUE_IS_STAMPED", i.getPublicId(),
                            "an OPEN issue carries cutoffStampedAt " + i.getCutoffStampedAt()
                                    + ". Nothing published it, so no release instant exists; the "
                                    + "value is its predecessor's, and it sorts and anchors gaps "
                                    + "on a date at which this issue did not exist."));
                }
            }

            Date cutoff = i.getCutoffStampedAt();
            if (cutoff != null && i.getIntervalFrom() != null
                    && cutoff.before(i.getIntervalFrom())) {
                violations.add(new Violation("CUTOFF_PRECEDES_ITS_INTERVAL", i.getPublicId(),
                        "the cut-off " + cutoff + " falls before this issue's interval opens at "
                                + i.getIntervalFrom() + ". A period cannot close before it "
                                + "begins, whatever the cut-off's provenance."));
            }
        }
        counts.put("openIssues", open);
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
     * The trigger audit. A REPORT, not a rewrite.
     *
     * SCANS EVERY FIELD A TRIGGER CAN EXPRESS ITSELF IN, not just messageQuery.
     * The first version read messageQuery alone -- and on the live estate TWELVE
     * of the fifteen triggers carry no messageQuery at all, putting their logic in
     * messageFilter instead. It would have reported a clean audit having looked at
     * a fifth of the triggers, which is the failure mode this report exists to
     * prevent: silence that reads as success.
     *
     * The `publication=` filter in mailing-list triggers has been cured. Nothing audits the
     * triggers that name a TAG. After C8 no new nm-wNN-YYYY tag is minted, so a
     * mailing list keyed on the naming convention silently stops matching and
     * nobody is told: the failure is a mailing that does not go out, which
     * nobody notices until somebody asks why they stopped receiving it.
     *
     * What to do with each hit is the user's call, made before the flip. This only
     * makes sure the question is asked.
     */
    public List<TriggerHit> auditTriggers() {
        List<TriggerHit> hits = new ArrayList<>();

        // Every trigger, not only those with a messageQuery.
        List<MailingListTrigger> triggers = em.createQuery(
                        "SELECT t FROM MailingListTrigger t", MailingListTrigger.class)
                .getResultList();

        for (MailingListTrigger t : triggers) {
            String listId = t.getMailingList() == null
                    ? null : t.getMailingList().getMailingListId();
            String type = t.getType() == null ? null : t.getType().name();

            scan(hits, listId, type, "messageQuery", t.getMessageQuery());
            scan(hits, listId, type, "messageFilter", t.getMessageFilter());

            // A script can name a tag too, and the path is the only part of it
            // stored here -- so a hit on the path is a prompt to open the script,
            // not a finding about its contents.
            if (t.getScriptResourcePaths() != null) {
                t.getScriptResourcePaths().forEach(
                        path -> scan(hits, listId, type, "scriptResourcePath", path));
            }
        }
        return hits;
    }

    /** Records a hit if the expression names a weekly tag. */
    private void scan(List<TriggerHit> hits, String listId, String type,
                      String field, String expression) {
        if (expression == null || expression.isBlank()) {
            return;
        }
        var m = WEEKLY_TAG.matcher(expression);
        if (m.find()) {
            hits.add(new TriggerHit(listId, type, field, m.group(1), expression));
        }
    }
}
