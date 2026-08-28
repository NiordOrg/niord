package org.niord.core.publication.series;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.niord.core.publication.series.resolve.GapDetection;
import org.niord.core.publication.series.resolve.GapSynthesis;
import org.niord.core.publication.series.vo.GapDetectionVo;
import org.niord.core.publication.series.vo.IssueListResultVo;
import org.niord.core.publication.series.vo.PublicationIssueDescVo;
import org.niord.core.publication.series.vo.SystemPublicationIssueVo;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One series' issue list: the rows that exist, the periods that do not, and
 * whether anybody looked.
 *
 * It is a service rather than a method on the issues endpoint because two
 * screens ask the same question. The issue list wants the rows; the dashboard
 * timeline wants a cell per recent period across many series at once. Built
 * twice, the named gap week in the list and the missing cell in the strip drift
 * apart, and they are the same fact.
 *
 * "now" is a parameter, never a clock read inside. Which period is UPCOMING and
 * whether a series is dormant are both answers about the calendar, and a service
 * that reads the clock itself cannot be asked what the list looked like at the
 * moment a report was rendered.
 */
@ApplicationScoped
public class IssueListService {

    @Inject
    EntityManager em;

    /**
     * The merged list for one series.
     *
     * Real rows come from the database in order; pseudo-rows are synthesized and
     * merged into the same sequence by sort key, so a caller pages one list
     * rather than reconciling two.
     */
    public IssueListResultVo forSeries(PublicationSeries series, Date now) {
        return build(series, issuesOf(series), now);
    }

    /**
     * The same list, over issues somebody already has.
     *
     * Split from the query so the answer can be checked without a database. The
     * interesting behaviour here is entirely about what the JSON says -- an
     * absent gapCount, a reason code, a pseudo-row carrying no publicId -- and a
     * test that needs MySQL to see it does not run on the build machine, which
     * is how the last set of assertions about this feature stopped running.
     *
     * @param issues ordered NEWEST first by effective cut-off
     */
    public static IssueListResultVo build(PublicationSeries series, List<PublicationIssue> issues,
                                          Date now) {
        ZoneId zone = series.cutoffZone();
        String cadence = series.getCadence() == null ? null : series.getCadence().name();

        // The newest effective cut-off, which the query has already sorted to the
        // front. Dormancy and the period length are both measured around it
        // rather than around today: a YEARLY series' period depends on which year
        // it is being counted in.
        Date newest = issues.isEmpty() ? null : issues.get(0).effectiveCutoff();
        long periodMillis = GapDetection.periodMillisOf(cadence, zone, newest == null ? now : newest);
        boolean dormant = GapDetection.isDormant(newest, now, periodMillis);

        GapDetection.Gate gate = GapDetection.gate(series.getTimeRelation(), cadence,
                series.getStatus() == SeriesStatus.ACTIVE, dormant, periodMillis);

        List<GapSynthesis.Row> pseudo = GapSynthesis.synthesize(gate, series.getSeriesId(),
                ascending(issues), periodMillis, zone, patternsOf(series),
                series.getFirstIssueStartsAt(), now);

        List<SystemPublicationIssueVo> rows = new ArrayList<>();
        for (PublicationIssue issue : issues) {
            rows.add(issue.toVo(SystemPublicationIssueVo.class));
        }
        for (GapSynthesis.Row row : pseudo) {
            rows.add(toVo(row, series));
        }
        rows.sort(NEWEST_FIRST);

        IssueListResultVo out = new IssueListResultVo();
        out.setPublicationSeriesId(series.getSeriesId());
        out.setData(rows);
        out.setTotal(issues.size());
        out.setGapDetection(gateVo(gate));
        // Absent, not zero, when the gate is closed. Zero is a finding about every
        // period; a closed gate is the absence of a finding, and the two must not
        // arrive at a screen looking identical.
        out.setGapCount(gate.enabled()
                ? (int) pseudo.stream().filter(r -> r.kind() == GapSynthesis.RowKind.MISSING).count()
                : null);
        return out;
    }

    /**
     * Newest first, and the order is TOTAL.
     *
     * Rows sharing a cut-off -- a retire-and-republish pair -- would otherwise
     * swap places between requests and the list would appear to reshuffle itself
     * while nobody touched it. A row with no cut-off at all sorts last rather than
     * first: it is an issue whose period is unknown, not the most recent one.
     */
    private static final Comparator<SystemPublicationIssueVo> NEWEST_FIRST =
            Comparator.comparing(SystemPublicationIssueVo::getSortKey,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(v -> v.getPublicId() == null ? "" : v.getPublicId(),
                            Comparator.reverseOrder());

    private List<PublicationIssue> issuesOf(PublicationSeries series) {
        // Ordered in the database rather than in Java: a series with a decade of
        // weeklies has ~500 rows.
        //
        // On the COALESCE rather than on cutoffStampedAt alone. An OPEN issue has
        // no stamp, so ordering by the stamp sorted the issue being worked on to
        // the BOTTOM of its own series -- below issues published years earlier.
        return em.createQuery(
                        "SELECT i FROM PublicationIssue i WHERE i.series = :s "
                                + "ORDER BY COALESCE(i.cutoffStampedAt, i.intervalTo) DESC, i.publicId DESC",
                        PublicationIssue.class)
                .setParameter("s", series)
                .getResultList();
    }

    /** What the synthesizer needs, oldest first, as it expects. */
    private static List<GapSynthesis.Issue> ascending(List<PublicationIssue> newestFirst) {
        List<GapSynthesis.Issue> out = new ArrayList<>();
        for (int i = newestFirst.size() - 1; i >= 0; i--) {
            PublicationIssue issue = newestFirst.get(i);
            Date cutoff = issue.effectiveCutoff();
            if (cutoff == null) {
                // No bound at all: it cannot anchor a gap, and including it would
                // let the arithmetic run off a null.
                continue;
            }
            out.add(new GapSynthesis.Issue(issue.getPublicId(), cutoff, cutoffSourceOf(issue),
                    issue.getIntervalFrom(), issue.getStatus() == IssueStatus.OPEN));
        }
        return out;
    }

    /**
     * How firm an issue's cut-off is, for a neighbouring gap row to inherit.
     *
     * A stamped cut-off is the publish action's own record and outranks whatever
     * the nominal bound says, which is why it is checked first.
     *
     * Package-visible because the draft endpoint inherits the same marker when it
     * chains an interval off an issue's close. The list and the draft describe the
     * same bound, and two answers about how firm it is would show one marker in the
     * table and a different one in the form the row opens.
     */
    static IntervalBoundSource cutoffSourceOf(PublicationIssue issue) {
        if (issue.getCutoffStampedAt() != null) {
            return IntervalBoundSource.STAMPED;
        }
        if (issue.getIntervalToSource() == null || issue.getIntervalToSource().isBlank()) {
            return IntervalBoundSource.NOMINAL;
        }
        try {
            return IntervalBoundSource.valueOf(issue.getIntervalToSource());
        } catch (IllegalArgumentException e) {
            return IntervalBoundSource.NOMINAL;
        }
    }

    /** The naming patterns per language the series declares. */
    private static Map<String, GapSynthesis.Patterns> patternsOf(PublicationSeries series) {
        Map<String, GapSynthesis.Patterns> out = new LinkedHashMap<>();
        for (PublicationSeriesDesc desc : series.getDescs()) {
            if (desc.getLang() == null || desc.getLang().isBlank()) {
                continue;
            }
            out.put(desc.getLang(),
                    new GapSynthesis.Patterns(desc.getNameSuggestionPattern(), desc.getFileNamePattern()));
        }
        return out;
    }

    /**
     * A pseudo-row in the shape of a real one.
     *
     * It is the same type on purpose. A separate type would let a client render
     * one list from two arrays and get the interleaving wrong, and every field a
     * gap genuinely has -- interval, bounds, provenance -- means the same thing
     * here as it does on a real row. The fields it does NOT have are absent from
     * the JSON rather than null, so "no publicId" cannot be read as a value.
     */
    private static SystemPublicationIssueVo toVo(GapSynthesis.Row row, PublicationSeries series) {
        SystemPublicationIssueVo vo = new SystemPublicationIssueVo();
        vo.setSeriesId(row.seriesId());
        vo.setPseudo(row.kind().name());
        vo.setComputedStatus(row.kind().name());
        vo.setIntervalFrom(row.intervalFrom());
        vo.setIntervalTo(row.intervalTo());
        vo.setIntervalFromSource(row.intervalFromSource() == null ? null : row.intervalFromSource().name());
        vo.setIntervalToSource(row.intervalToSource() == null ? null : row.intervalToSource().name());
        // The period's end IS its effective cut-off. Nothing stamped it, which is
        // what intervalToSource says.
        vo.setEffectiveCutoff(row.intervalTo());
        vo.setSortKey(row.sortKey());
        vo.setPrecedingPublicId(row.precedingPublicId());
        vo.setFollowingPublicId(row.followingPublicId());

        List<PublicationIssueDescVo> suggested = new ArrayList<>();
        for (Map.Entry<String, GapSynthesis.Suggestion> e : row.suggestions().entrySet()) {
            PublicationIssueDescVo desc = new PublicationIssueDescVo();
            desc.setLang(e.getKey());
            desc.setName(e.getValue().name());
            desc.setFileName(e.getValue().fileName());
            suggested.add(desc);
        }
        vo.setSuggestedDescs(suggested.isEmpty() ? null : suggested);

        // memberCount stays null. A period nobody published has no membership, and
        // a 0 here would read as "resolved, empty" -- which is a different claim.
        return vo;
    }

    private static GapDetectionVo gateVo(GapDetection.Gate gate) {
        GapDetectionVo vo = new GapDetectionVo();
        vo.setEnabled(gate.enabled());
        vo.setReasonCode(gate.code() == null ? null : gate.code().name());
        vo.setReason(gate.reason());
        return vo;
    }

}
