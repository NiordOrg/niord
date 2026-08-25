package org.niord.core.publication.series;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.service.BaseService;
import org.niord.core.user.User;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * The issue transitions other than publish: create, retro-create, new edition,
 * amend, retire, reactivate, delete.
 *
 * Two of these carry decisions worth stating rather than inferring.
 *
 * RETIRE deliberately leaves the file and the public window in place. A retired
 * issue is withdrawn from the workflow, not from history: its PDF stays at its
 * link because people have cited it, and its window stays because it still
 * occupies its bracket. Clearing either would leave the predecessor uncapped and
 * break the chain behind it.
 *
 * NEW EDITION is a distinct, audited action rather than an edit. It sets
 * supersedes and caps the predecessor in ONE transaction, because the
 * alternative -- edit the fields, then remember to cap -- is where step three
 * gets forgotten and two current editions reach the public download site at
 * once. It is also the only write path supersedes has: without it the column
 * and its audit action are unreachable by any API call.
 */
@ApplicationScoped
public class IssueLifecycleService extends BaseService {

    @Inject
    IssueAuditService audit;

    /** A transition was refused. Carries the wire code so callers do not invent one. */
    public static class TransitionRefusedException extends RuntimeException {
        private final String code;

        public TransitionRefusedException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    // ================================================================= create

    /** T0. A new issue. publicId is minted at CREATE and immutable for life. */
    @Transactional
    public PublicationIssue create(PublicationSeries series, Date intervalFrom,
                                   IntervalBoundSource fromSource, User actor) {
        PublicationIssue issue = newIssue(series, intervalFrom, fromSource);
        em.persist(issue);
        audit.created(issue, actor, "CREATED");
        return issue;
    }

    /**
     * Retro-create: an issue for a period that was missed.
     *
     * Only meaningful where issues tile. An IN_FORCE_AT_CUTOFF series has no
     * missing period to recover, because its issues overlap -- asking for one is
     * a category error rather than an unusual request.
     */
    @Transactional
    public PublicationIssue retroCreate(PublicationSeries series, Date intervalFrom, Date intervalTo,
                                        User actor) {
        if (series.getTimeRelation() != TimeRelation.PUBLISHED_IN_INTERVAL) {
            throw new TransitionRefusedException("RETRO_CREATE_NOT_APPLICABLE",
                    "this series' issues overlap rather than tile, so there is no missing period to recover");
        }
        PublicationIssue issue = newIssue(series, intervalFrom, IntervalBoundSource.RECOVERED);
        issue.setIntervalTo(intervalTo);
        issue.setCutoffReconstructed(true);
        em.persist(issue);
        audit.created(issue, actor, "CREATED_RETROACTIVELY");
        return issue;
    }

    /**
     * A new edition of an in-force publication, superseding an existing one.
     *
     * OQ-10. One transaction: the link and the cap together. Skydeomraader has
     * two editions in 2020 and three in 2022, so this fires predictably rather
     * than exceptionally.
     */
    @Transactional
    public PublicationIssue newEdition(PublicationIssue predecessor, Date intervalFrom, User actor) {
        if (predecessor.getStatus() != IssueStatus.PUBLISHED) {
            throw new TransitionRefusedException("PREDECESSOR_NOT_PUBLISHED",
                    "only a published issue can be superseded");
        }

        PublicationSeries series = predecessor.getSeries();
        PublicationIssue edition = newIssue(series, intervalFrom, IntervalBoundSource.MANUAL);
        edition.setSupersedes(predecessor);
        em.persist(edition);

        // The cap, in the SAME transaction. Separated, this is the step that gets
        // forgotten, and forgetting it puts two current editions on the site.
        if (predecessor.getPublicTo() == null) {
            predecessor.setPublicTo(new Date(System.currentTimeMillis()));
            em.merge(predecessor);
        }

        audit.created(edition, actor, "CREATED_NEW_EDITION");
        audit.supersededBy(predecessor, edition, actor);
        return edition;
    }

    private PublicationIssue newIssue(PublicationSeries series, Date intervalFrom, IntervalBoundSource source) {
        PublicationIssue issue = new PublicationIssue();
        issue.setSeries(series);
        // Minted at create, immutable for life: it is what message HTML cites.
        issue.setPublicId(UUID.randomUUID().toString());
        issue.setRepoPath("publications/" + issue.getPublicId());
        issue.setStatus(IssueStatus.OPEN);
        issue.setIntervalFrom(intervalFrom);
        issue.setIntervalFromSource(source);

        // One desc row per CONFIGURED language, from the moment of create, each
        // with a suggested name.
        //
        // Creating them lazily on first write leaves an issue in a state where a
        // language the series declares has nowhere to put its file name or link,
        // and the failure then surfaces as "no such language" at upload time
        // rather than as a missing row at create time.
        //
        // The name is SUGGESTED, not final. It is derived from the provisional
        // interval start because there is no stamped cut-off yet -- the real one
        // arrives at publish, and an admin may override it before then. A blank
        // name is not an option: the column is NOT NULL precisely because a
        // nameless issue is unfindable in every list that shows it.
        for (String lang : series.getLanguages()) {
            PublicationIssueDesc desc = issue.createDesc(lang);
            desc.setName(suggestName(series, lang, intervalFrom));
        }
        return issue;
    }


    /**
     * A provisional name for a new issue.
     *
     * Falls back through the series pattern, then the series name, then the
     * series id. Every step is a real answer rather than a placeholder, because
     * a name reading "untitled" is what ends up on a published document when
     * nobody noticed it was never set.
     */
    static String suggestName(PublicationSeries series, String lang, Date basis) {
        PublicationSeriesDesc seriesDesc = series.getDescs().stream()
                .filter(d -> lang.equals(d.getLang()))
                .findFirst().orElse(null);

        if (seriesDesc != null && seriesDesc.getNameSuggestionPattern() != null
                && !seriesDesc.getNameSuggestionPattern().isBlank() && basis != null) {
            try {
                java.time.ZoneId zone = series.cutoffZone();
                return org.niord.core.publication.series.resolve.IssueNaming.expand(
                        seriesDesc.getNameSuggestionPattern(),
                        org.niord.core.publication.series.resolve.IssueNaming.derive(basis, null, zone, null));
            } catch (RuntimeException e) {
                // A pattern that cannot expand is a series-validation problem,
                // not a reason to refuse to create an issue.
            }
        }
        if (seriesDesc != null && seriesDesc.getName() != null && !seriesDesc.getName().isBlank()) {
            return seriesDesc.getName();
        }
        return series.getSeriesId();
    }

    // ================================================================= amend

    /**
     * T2. Re-render a published issue in place.
     *
     * The public window and publishedAt are untouched: an amendment corrects the
     * document, it does not republish it. What changes is the file, and the old
     * bytes are archived first.
     */
    @Transactional
    public PublicationIssue amend(PublicationIssue issue, User actor, String reason,
                                  List<String> archivePaths) {
        if (issue.getStatus() != IssueStatus.PUBLISHED) {
            throw new TransitionRefusedException("ISSUE_NOT_PUBLISHED",
                    "only a published issue can be amended");
        }
        audit.amended(issue, actor, reason, archivePaths);
        return em.merge(issue);
    }

    // ============================================================ retire / reactivate

    /**
     * T3. Withdraw an issue from the workflow, leaving history intact.
     *
     * The file stays at its link and the window stays open to whatever it was:
     * people have cited this document, and it still occupies its bracket in the
     * chain. Clearing either would leave the issue before it uncapped.
     */
    @Transactional
    public PublicationIssue retire(PublicationIssue issue, User actor, String reason) {
        if (issue.getStatus() != IssueStatus.PUBLISHED) {
            throw new TransitionRefusedException("ISSUE_NOT_PUBLISHED",
                    "only a published issue can be retired");
        }
        Date fileBefore = issue.getPublicTo();

        issue.setStatus(IssueStatus.RETIRED);
        issue.setRetiredAt(new Date());
        issue.setRetiredBy(actor);
        issue.setRetiredReason(reason);

        // Explicitly unchanged, so a later reader can see it was a decision.
        issue.setPublicTo(fileBefore);

        audit.retired(issue, actor, reason);
        return em.merge(issue);
    }

    /** T4. Put a retired issue back. */
    @Transactional
    public PublicationIssue reactivate(PublicationIssue issue, User actor, String reason) {
        if (issue.getStatus() != IssueStatus.RETIRED) {
            throw new TransitionRefusedException("ISSUE_NOT_RETIRED",
                    "only a retired issue can be reactivated");
        }
        issue.setStatus(IssueStatus.PUBLISHED);
        issue.setRetiredAt(null);
        issue.setRetiredBy(null);
        issue.setRetiredReason(null);
        audit.reactivated(issue, actor, reason);
        return em.merge(issue);
    }

    // ================================================================= delete

    /**
     * T5. An issue may be deleted only while nothing has depended on it.
     *
     * DM-Q3 restated: C7's literal "no publicId" can never be true, because
     * publicId is minted at create. The real test is that it was never stamped
     * and never published -- which preserves C7's intent exactly.
     */
    @Transactional
    public void deleteIssue(PublicationIssue issue, User actor) {
        if (issue.getStatus() != IssueStatus.OPEN
                || issue.getCutoffStampedAt() != null
                || issue.getPublishedAt() != null) {
            throw new TransitionRefusedException("ISSUE_NOT_DELETABLE",
                    "an issue that has been stamped or published cannot be deleted; retire it instead");
        }
        em.createQuery("DELETE FROM IssueMember m WHERE m.issue = :i").setParameter("i", issue).executeUpdate();
        em.createQuery("DELETE FROM IssueOverride o WHERE o.issue = :i").setParameter("i", issue).executeUpdate();
        em.createQuery("DELETE FROM IssueAuditEntry a WHERE a.issue = :i").setParameter("i", issue).executeUpdate();
        em.remove(em.contains(issue) ? issue : em.merge(issue));
    }

    /** S4 / X-5. A series may be deleted only when it has no issues at all. */
    @Transactional
    public void deleteSeries(PublicationSeries series) {
        Long issues = em.createQuery(
                        "SELECT COUNT(i) FROM PublicationIssue i WHERE i.series = :s", Long.class)
                .setParameter("s", series).getSingleResult();
        if (issues > 0) {
            throw new TransitionRefusedException("SERIES_HAS_ISSUES",
                    "the series has " + issues + " issue(s); retire it instead of deleting it");
        }
        em.remove(em.contains(series) ? series : em.merge(series));
    }
}
