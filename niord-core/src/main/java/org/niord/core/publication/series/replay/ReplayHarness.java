package org.niord.core.publication.series.replay;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.niord.core.publication.series.ContentMode;
import org.niord.core.publication.series.IssueMember;
import org.niord.core.publication.series.MemberResolutionService;
import org.niord.core.publication.series.PublicationIssue;
import org.niord.core.publication.series.PublicationIssueDesc;
import org.niord.core.publication.series.PublicationSeries;
import org.niord.core.publication.series.criteria.CriteriaResolver;
import org.niord.core.publication.series.resolve.Interval;
import org.niord.core.publication.series.resolve.ResolvedCriteria;
import org.niord.core.publication.series.resolve.TimeRelation;

import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * B6.1. Re-resolves every imported issue and diffs against what it froze.
 *
 * The frozen {@link IssueMember} rows are the legacy tag, captured verbatim at
 * import. Re-resolving asks: would the new engine, given this issue's own
 * window and criteria, produce that same list today? Where it would not, the
 * difference has to be one of the measured {@link DivergenceClass}es.
 *
 * <h2>Two things this deliberately does not do</h2>
 *
 * <b>It does not read the series' timeRelation or aliveAtCutoff.</b> It reads
 * the issue's own {@code snapshotTimeRelation} and {@code snapshotAliveAtCutoff}
 * -- which is the entire reason B5.4a2 recorded them per issue. At least one
 * series spans both the blank/sticky era and the phase era, and 122 of its
 * issues need the opposite setting from the series row. Resolving those from
 * the series would produce a diff on every one of them and attribute it to the
 * engine rather than to the era.
 *
 * <b>It does not go through the search REST layer.</b> That layer day-snaps the
 * interval and forces PUBLISHED-only, so a replay through it would be measuring
 * the API's rounding rather than the predicate. The publish path resolves
 * in-process for the same reason, and a replay that resolved differently from
 * publish would be checking something nobody ships.
 */
@ApplicationScoped
public class ReplayHarness {

    @Inject
    EntityManager em;

    @Inject
    MemberResolutionService resolver;

    /** Replays every imported issue. */
    @Transactional
    public ReplayReport replayAll() {
        return replay(em.createQuery(
                        "SELECT i FROM PublicationIssue i WHERE i.series.importSource IS NOT NULL "
                                + "ORDER BY i.publicId", PublicationIssue.class)
                .getResultList());
    }

    /** Replays one series, for the per-series view B6.3 reports. */
    @Transactional
    public ReplayReport replaySeries(String seriesId) {
        return replay(em.createQuery(
                        "SELECT i FROM PublicationIssue i WHERE i.series.seriesId = :s "
                                + "ORDER BY i.publicId", PublicationIssue.class)
                .setParameter("s", seriesId)
                .getResultList());
    }

    private ReplayReport replay(List<PublicationIssue> issues) {
        ReplayReport report = new ReplayReport();
        for (PublicationIssue issue : issues) {
            replayOne(issue, report);
        }
        return report;
    }

    private void replayOne(PublicationIssue issue, ReplayReport report) {
        ReplayReport.SkipReason skip = skipReasonFor(issue);
        if (skip != null) {
            report.recordSkip(issue.getPublicId(), skip);
            return;
        }

        Set<String> frozen = frozenMembers(issue);
        Set<String> resolved = resolver.resolve(
                        criteriaOf(issue), new Interval(issue.getIntervalFrom(), cutoffOf(issue)))
                .members();

        Set<String> missing = new LinkedHashSet<>(frozen);
        missing.removeAll(resolved);
        Set<String> extra = new LinkedHashSet<>(resolved);
        extra.removeAll(frozen);

        if (missing.isEmpty() && extra.isEmpty()) {
            report.recordIdentical();
        } else {
            report.recordDiff(new ReplayReport.IssueDiff(
                    issue.getPublicId(),
                    issue.getSeries() == null ? null : issue.getSeries().getSeriesId(),
                    missing, extra));
        }
    }

    /**
     * Whether this issue can be compared at all.
     *
     * Order matters only for what gets reported: an issue with no membership
     * semantics AND a hand-replaced file is really the former, because there was
     * never a member list for the file to have been generated from.
     */
    private ReplayReport.SkipReason skipReasonFor(PublicationIssue issue) {
        PublicationSeries series = issue.getSeries();

        // Roughly 48 publications are an uploaded file, an external link, or
        // nothing at all. A null criteria document means NO QUERY, which is a
        // different thing from an empty one: resolving it would either raise or
        // match the entire corpus, and both would be reported as a divergence.
        if (series == null
                || series.getContentMode() != ContentMode.GENERATED_FROM_QUERY
                || series.getCriteria() == null) {
            return ReplayReport.SkipReason.NO_MEMBERSHIP_SEMANTICS;
        }

        // C6. A file somebody uploaded by hand was never generated from a member
        // list, so "reproducible from the member list" is not a property it has.
        // Comparing against it measures nothing.
        if (issue.getDescs() != null
                && issue.getDescs().stream().anyMatch(PublicationIssueDesc::isFileSourceSticky)) {
            return ReplayReport.SkipReason.FILE_REPLACED_BY_HAND;
        }

        if (issue.getIntervalFrom() == null) {
            return ReplayReport.SkipReason.NO_INTERVAL;
        }
        if (cutoffOf(issue) == null) {
            return ReplayReport.SkipReason.NO_CUTOFF;
        }
        if (issue.getSnapshotTimeRelation() == null || issue.getSnapshotAliveAtCutoff() == null) {
            return ReplayReport.SkipReason.NO_SNAPSHOT_HEADER;
        }
        return null;
    }

    /**
     * The issue's OWN criteria resolution -- see the class comment.
     *
     * Falls back to the series only where the issue carries no snapshot, which
     * means a row written before B5.4a2 or by something other than the importer.
     * Falling back rather than skipping keeps those comparable; the snapshot is
     * a correction to the series value, not a precondition for having one.
     */
    private ResolvedCriteria criteriaOf(PublicationIssue issue) {
        PublicationSeries series = issue.getSeries();

        // Read from the ISSUE and never from the series. The importer derives the
        // snapshot header from each release's own legacy filter, and a series
        // spanning two filter eras carries one answer on the series row while 122
        // of its issues need the other. Falling back to the series here would
        // replay those issues under the wrong relation and report the difference
        // as a defect in the engine. A row without a header is not comparable,
        // and saying so is better than comparing it wrongly.
        if (issue.getSnapshotTimeRelation() == null || issue.getSnapshotAliveAtCutoff() == null) {
            throw new IllegalStateException("issue " + issue.getPublicId()
                    + " carries no snapshot header (timeRelation / aliveAtCutoff); the replay reads the "
                    + "issue's own header and never the series' row");
        }
        TimeRelation relation = TimeRelation.valueOf(issue.getSnapshotTimeRelation());
        boolean aliveAtCutoff = issue.getSnapshotAliveAtCutoff();

        // The SERIES' document, deliberately -- not EffectiveCriteria. A replay
        // reproduces what the legacy engine produced, and a criteriaOverride is a
        // decision somebody made afterwards, in this system. Applying it here
        // would diff the legacy output against a document legacy never had, and
        // report the difference as a defect in the replay.
        return CriteriaResolver.resolve(series.getCriteria(), relation, aliveAtCutoff,
                CriteriaResolver.NO_DOMAINS);
    }

    /**
     * The window's end.
     *
     * The stamped cut-off where there is one, and the recovered cut-off
     * otherwise -- B5.4b's cascade put a value there for all 1,077 imported
     * issues, 994 from the legacy updated stamp and 83 from the next tag.
     */
    private Date cutoffOf(PublicationIssue issue) {
        return issue.getCutoffStampedAt();
    }

    /** The legacy tag, as the import froze it. */
    private Set<String> frozenMembers(PublicationIssue issue) {
        return new LinkedHashSet<>(em.createQuery(
                        "SELECT m.messageUid FROM IssueMember m WHERE m.issue = :i "
                                + "ORDER BY m.sortIndex", String.class)
                .setParameter("i", issue)
                .getResultList());
    }
}
