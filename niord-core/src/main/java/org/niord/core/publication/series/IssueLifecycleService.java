package org.niord.core.publication.series;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.niord.core.publication.series.resolve.IssueNaming;
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

    @Inject
    IssueShape shape;

    /** A transition was refused. Carries the wire code so callers do not invent one. */
    public static class TransitionRefusedException extends RuntimeException {
        private final String code;
        private final List<SeriesValidator.FieldError> fieldErrors;

        public TransitionRefusedException(String code, String message) {
            this(code, message, List.of());
        }

        /**
         * A refusal that knows which fields failed.
         *
         * A form cannot render "seven rules fail" against anything. The rules
         * already say which field each belongs to, and dropping that on the way
         * out left the client with one sentence and no way to put a message
         * beside the control that caused it.
         */
        public TransitionRefusedException(String code, String message,
                                          List<SeriesValidator.FieldError> fieldErrors) {
            super(message);
            this.code = code;
            this.fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
        }

        public String code() {
            return code;
        }

        public List<SeriesValidator.FieldError> fieldErrors() {
            return fieldErrors;
        }
    }

    /** The one reason rule, for every action that changes what the public reads. */
    public static final int MIN_REASON = 3;
    public static final int MAX_REASON = 512;

    /**
     * A reason somebody can read, or a refusal.
     *
     * The floor is what separates a reason from a keystroke: "x" records that
     * somebody typed something, which is worse than nothing because it looks
     * like a decision was explained. The ceiling keeps a trail readable.
     */
    public static String requireReason(String reason, String why) {
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.length() < MIN_REASON || trimmed.length() > MAX_REASON) {
            throw new TransitionRefusedException("REASON_REQUIRED",
                    why + " -- in between " + MIN_REASON + " and " + MAX_REASON + " characters");
        }
        return trimmed;
    }

    // ================================================================= create

    /** T0. A new issue. publicId is minted at CREATE and immutable for life. */
    @Transactional
    public PublicationIssue create(PublicationSeries series, Date intervalFrom,
                                   IntervalBoundSource fromSource, User actor) {
        return create(series, intervalFrom, fromSource, null, actor);
    }

    /**
     * T0, with the close the caller already reviewed.
     *
     * The draft shows both bounds and lets an admin move either, so the create has
     * to be able to take both. Without this the interval that was on the screen
     * had to be re-applied by a second call through the edit endpoint -- two
     * writes, two audit entries, and a window in between where the issue carried a
     * period nobody chose. A null close still means "derive the nominal one".
     */
    @Transactional
    public PublicationIssue create(PublicationSeries series, Date intervalFrom,
                                   IntervalBoundSource fromSource, Date intervalTo, User actor) {
        // The same refusal the draft makes, in the same words: an interval that
        // ends before it starts selects nothing, and the issue would publish EMPTY
        // rather than fail.
        if (intervalFrom != null && intervalTo != null && !intervalFrom.before(intervalTo)) {
            throw new TransitionRefusedException("INTERVAL_INVERTED",
                    "the period would close at " + intervalTo + ", at or before it opens at "
                            + intervalFrom + ". An interval that ends before it starts selects "
                            + "nothing, and the issue would publish empty rather than fail.");
        }
        assertNoOverlap(series, intervalFrom, null);
        PublicationIssue issue = newIssue(series, intervalFrom, fromSource, intervalTo,
                intervalTo == null ? null : IntervalBoundSource.MANUAL);
        em.persist(issue);
        audit.created(issue, actor, "CREATED");
        return issue;
    }

    /**
     * A new issue may not claim a period a released one already covered.
     *
     * Only where issues TILE. An IN_FORCE_AT_CUTOFF series' issues overlap by
     * construction -- the 2026 and 2027 firing-area editions share thirty-one of
     * their thirty-two members -- so asking whether they overlap is a category
     * error rather than a violation.
     *
     * Public because the DRAFT asks the same question before an admin presses
     * create. One rule, called from both places: a draft that answered "this is
     * fine" from its own copy of the test and then hit a refusal on save would be
     * worse than no preview at all.
     *
     * The test is against the released neighbour's own close, not against the
     * cadence. A week published EARLY closes early, and the next issue opening at
     * that earlier instant is the chain working: it is exactly where the previous
     * one ended. What is refused is an interval that starts BEFORE a released
     * neighbour closed, because the content between those two instants has
     * already gone out in that neighbour, and a second issue claiming it would
     * publish the same messages twice under two names.
     */
    public void assertNoOverlap(PublicationSeries series, Date intervalFrom, PublicationIssue ignoring) {
        if (series == null || intervalFrom == null
                || series.getTimeRelation() != TimeRelation.PUBLISHED_IN_INTERVAL) {
            return;
        }
        List<PublicationIssue> released = em.createQuery(
                        "SELECT i FROM PublicationIssue i WHERE i.series = :s AND i.status IN :st "
                                + "ORDER BY i.id DESC", PublicationIssue.class)
                .setParameter("s", series)
                .setParameter("st", List.of(IssueStatus.PUBLISHED, IssueStatus.RETIRED))
                .getResultList();

        for (PublicationIssue other : released) {
            if (ignoring != null && other.getId() != null && other.getId().equals(ignoring.getId())) {
                continue;
            }
            Date closed = other.effectiveCutoff();
            Date opened = other.getIntervalFrom();
            // Strictly inside the neighbour's period: at its close is the chain,
            // at or before its open is a period that ended before this one began.
            if (closed != null && intervalFrom.before(closed)
                    && (opened == null || !intervalFrom.before(opened))) {
                throw new TransitionRefusedException("ISSUE_INTERVAL_OVERLAP",
                        "a period starting " + intervalFrom + " falls inside '" + other.getPublicId()
                                + "', which was released covering up to " + closed + ". Those messages "
                                + "have already gone out; an issue claiming them again would publish "
                                + "them twice under two names.");
            }
        }
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
        assertNoOverlap(series, intervalFrom, null);
        // The close comes in with the request: a recovered period is one somebody
        // worked out, and its end is RECOVERED for the same reason its start is --
        // nothing stamped it and no cadence derived it.
        PublicationIssue issue = newIssue(series, intervalFrom, IntervalBoundSource.RECOVERED,
                intervalTo, intervalTo == null ? null : IntervalBoundSource.RECOVERED);
        issue.setCutoffReconstructed(true);
        em.persist(issue);
        audit.created(issue, actor, "CREATED_RETROACTIVELY");
        return issue;
    }

    /**
     * A new edition of an in-force publication, superseding an existing one.
     *
     * The LINK is made here, where it cannot be forgotten; the WINDOW changes
     * hands in the publish that takes over, where the successor becomes
     * readable. Skydeomraader has two editions in 2020 and three in 2022, so
     * this fires predictably rather than exceptionally.
     */
    @Transactional
    public PublicationIssue newEdition(PublicationIssue predecessor, Date intervalFrom, User actor) {
        if (predecessor.getStatus() != IssueStatus.PUBLISHED) {
            throw new TransitionRefusedException("PREDECESSOR_NOT_PUBLISHED",
                    "only a published issue can be superseded");
        }

        PublicationSeries series = predecessor.getSeries();
        assertNoOverlap(series, intervalFrom, predecessor);
        PublicationIssue edition = newIssue(series, intervalFrom, IntervalBoundSource.MANUAL);
        edition.setSupersedes(predecessor);
        em.persist(edition);

        // The LINK is made here, in the same transaction as the issue, so it can
        // never be forgotten. The CAP is not, and that is deliberate: this edition
        // is OPEN and nobody can read it yet, so closing the predecessor's window
        // now would leave the download site with no current edition at all until
        // somebody publishes. The cap happens where the successor becomes public
        // -- the publish transaction caps the predecessor at the new stamp minus
        // one millisecond, for every series and both time relations, so the two
        // windows meet exactly and neither overlaps nor gaps.
        audit.created(edition, actor, "CREATED_NEW_EDITION");
        audit.supersededBy(predecessor, edition, actor);
        return edition;
    }

    private PublicationIssue newIssue(PublicationSeries series, Date intervalFrom, IntervalBoundSource source) {
        return newIssue(series, intervalFrom, source, null, null);
    }

    /**
     * T0, in one place.
     *
     * @param intervalTo the close, where the caller already knows it; null lets the
     *                   cadence derive the nominal one
     */
    private PublicationIssue newIssue(PublicationSeries series, Date intervalFrom,
                                      IntervalBoundSource source,
                                      Date intervalTo, IntervalBoundSource toSource) {
        // A one-off holds exactly one issue, and refusing the second is what
        // makes its kind a fact rather than a description of the current row
        // count. A publication that turns out to keep appearing is an
        // UNSCHEDULED series -- and saying so should be a decision somebody
        // makes, not a side effect of an upload.
        //
        // Every path that reaches here is a person asking: the automatic
        // successor in IssuePublishService requires a cadence, and a one-off
        // has none, so it can never arrive at this check.
        if (series != null && series.isOneOff()) {
            Long existing = em.createQuery(
                            "SELECT COUNT(i) FROM PublicationIssue i WHERE i.series = :s", Long.class)
                    .setParameter("s", series)
                    .getSingleResult();
            if (existing > 0) {
                throw new TransitionRefusedException("SERIES_IS_ONE_OFF",
                        "'" + series.getSeriesId() + "' is a one-off and already has its issue. "
                                + "If this publication is going to keep appearing, change its kind "
                                + "to UNSCHEDULED first -- that is a decision about the publication "
                                + "rather than about this upload.");
            }
        }

        PublicationIssue issue = new PublicationIssue();
        issue.setSeries(series);
        // Minted at create, immutable for life: it is what message HTML cites.
        issue.setPublicId(UUID.randomUUID().toString());
        issue.setRepoPath("publications/" + issue.getPublicId());
        issue.setStatus(IssueStatus.OPEN);
        issue.setIntervalFrom(intervalFrom);
        issue.setIntervalFromSource(source);
        issue.setIntervalTo(intervalTo);
        issue.setIntervalToSource(toSource == null ? null : toSource.name());

        // One desc row per CONFIGURED language, from the moment of create.
        //
        // Creating them lazily on first write leaves an issue in a state where a
        // language the series declares has nowhere to put its file name or link,
        // and the failure then surfaces as "no such language" at upload time
        // rather than as a missing row at create time.
        for (String lang : series.getLanguages()) {
            issue.createDesc(lang);
        }

        // The nominal close, the numbers and the per-language names, all from the
        // one derivation the draft answers with. The name is SUGGESTED, not final:
        // it renders the period the issue closes in, the real cut-off arrives at
        // publish, and an admin may type over it before then.
        shape.apply(issue, series, new Date());
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
    static String suggestName(PublicationSeries series, String lang, IssueNaming.Numbers numbers) {
        PublicationSeriesDesc seriesDesc = series.getDescs().stream()
                .filter(d -> lang.equals(d.getLang()))
                .findFirst().orElse(null);

        if (seriesDesc != null && seriesDesc.getNameSuggestionPattern() != null
                && !seriesDesc.getNameSuggestionPattern().isBlank() && numbers != null) {
            try {
                return IssueNaming.expand(seriesDesc.getNameSuggestionPattern(), numbers);
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
        // Retiring takes a document off the public list that people may be
        // reading; the trail must say why, in words. Reactivating restores the
        // state it was already published in and asks for none.
        requireReason(reason, "retiring removes this issue from the public list; it must say why");
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
