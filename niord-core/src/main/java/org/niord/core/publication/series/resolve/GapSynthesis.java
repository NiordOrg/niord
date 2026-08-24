package org.niord.core.publication.series.resolve;

import org.niord.core.publication.series.IntervalBoundSource;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The periods a series has no issue for, as rows.
 *
 * GapDetection answers "which periods are missing" as bare intervals. This turns
 * that into the rows two different screens render -- the issue list's MISSING and
 * UPCOMING pseudo-rows and the dashboard's timeline cells -- and it is ONE
 * synthesizer on purpose. The named gap week in the list and the missing cell in
 * the strip are the same fact, and two producers of one fact drift.
 *
 * A pseudo-row has no entity. It carries no publicId, and its interval bounds are
 * COMPUTED rather than read from columns, because there is no row that could have
 * stored them. That is why each bound carries its own source: a reader has to be
 * able to tell a stamped cut-off from one inferred from the cadence.
 */
public final class GapSynthesis {

    /** What a synthesized row is. A real row is neither. */
    public enum RowKind {
        /** A period that tiled between two issues and was never created. */
        MISSING,
        /** The period being worked toward: its cut-off has not passed yet. */
        UPCOMING
    }

    /**
     * One synthesized row.
     *
     * suggestedNames is per language rather than one string, because a series
     * declares the languages it publishes in and a retro-create prefilled in only
     * one of them is how an issue gets created half-named.
     */
    public record Row(RowKind kind,
                      String seriesId,
                      Date intervalFrom,
                      Date intervalTo,
                      IntervalBoundSource intervalFromSource,
                      IntervalBoundSource intervalToSource,
                      String precedingPublicId,
                      String followingPublicId,
                      long sortKey,
                      Map<String, String> suggestedNames) {
    }

    /**
     * What the synthesizer needs to know about a real issue, and nothing more.
     *
     * effectiveCutoff is the stamped cut-off where there is one and the nominal
     * bound where there is not -- the same coalesce the rest of the feature uses
     * to bound an interval.
     */
    public record Issue(String publicId, Date effectiveCutoff, IntervalBoundSource cutoffSource) {
    }

    private GapSynthesis() {
    }

    /**
     * The rows for one series.
     *
     * Returns EMPTY whenever the gate is closed, and a caller must not read that
     * as "no gaps". The gate's reason is the answer in that case. A caller that
     * reports the empty list as a clean bill of health is asserting something
     * nobody checked, which is why every caller reports gate state alongside the
     * rows rather than the count alone.
     *
     * @param issues ordered ASCENDING by effective cut-off
     * @param namePatterns nameSuggestionPattern per language; may be empty
     */
    public static List<Row> synthesize(GapDetection.Gate gate,
                                       String seriesId,
                                       List<Issue> issues,
                                       long periodMillis,
                                       ZoneId zone,
                                       Map<String, String> namePatterns,
                                       Date now) {
        List<Row> out = new ArrayList<>();
        if (gate == null || !gate.enabled() || periodMillis <= 0 || issues == null || issues.isEmpty()) {
            return out;
        }

        // Between consecutive issues, GapDetection owns the arithmetic -- including
        // the tolerance for a release that drifted by hours. Re-deriving it here
        // would be a second answer to a question that already has one.
        List<Date> cutoffs = issues.stream()
                .map(Issue::effectiveCutoff)
                .filter(Objects::nonNull)
                .toList();
        for (GapDetection.Gap gap : GapDetection.gaps(gate, cutoffs, periodMillis)) {
            out.add(row(RowKind.MISSING, seriesId, gap.from(), gap.to(),
                    latestAtOrBefore(issues, gap.from()), earliestAfter(issues, gap.to()),
                    zone, namePatterns));
        }

        // After the newest issue. GapDetection.gaps looks only BETWEEN cut-offs, so
        // without this a series that simply stopped shows no gap at all -- which is
        // the one case somebody is actually looking for. Periods whose cut-off has
        // already passed are MISSING; the first that has not is UPCOMING, and there
        // is only ever one of those.
        Issue newest = issues.get(issues.size() - 1);
        if (newest.effectiveCutoff() != null && now != null) {
            Date from = newest.effectiveCutoff();
            Issue preceding = newest;
            // Bounded by the dormancy gate rather than by a literal: a series nobody
            // has published to for DORMANCY_PERIODS is already gated off above, so
            // this cannot run away.
            while (true) {
                Date to = GapDetection.nextCutoff(from, periodMillis);
                boolean passed = to.getTime() <= now.getTime();
                out.add(row(passed ? RowKind.MISSING : RowKind.UPCOMING, seriesId, from, to,
                        preceding, null, zone, namePatterns));
                if (!passed) {
                    break;
                }
                from = to;
                preceding = null;
            }
        }

        out.sort((a, b) -> Long.compare(a.sortKey(), b.sortKey()));
        return out;
    }

    private static Row row(RowKind kind, String seriesId, Date from, Date to,
                           Issue preceding, Issue following, ZoneId zone,
                           Map<String, String> namePatterns) {
        return new Row(kind, seriesId, from, to,
                // A preceding issue's stamped cut-off is a recorded bound. Anything
                // else here is the cadence talking, and says so.
                boundSource(preceding),
                boundSource(following),
                preceding == null ? null : preceding.publicId(),
                following == null ? null : following.publicId(),
                // The same key space as a real row, which sorts on its effective
                // cut-off, so a merged list is one sequence rather than two
                // interleaved ones.
                to.getTime(),
                suggestedNames(to, zone, namePatterns));
    }

    private static IntervalBoundSource boundSource(Issue neighbour) {
        return neighbour != null && neighbour.cutoffSource() == IntervalBoundSource.STAMPED
                ? IntervalBoundSource.STAMPED
                : IntervalBoundSource.NOMINAL;
    }

    /**
     * The name each language would give this period.
     *
     * Derived from the interval END, and the interval start is deliberately NOT
     * passed. IssueNaming.derive takes the start only to detect a multi-week
     * issue, and when it detects one it re-points ${week} at the START week and
     * moves the cut-off week to ${weekTo} -- the "Uge 26+27" form. Every weekly
     * period runs Wednesday to Wednesday and therefore spans two ISO weeks, so
     * handing over the start would produce that form for every gap there has ever
     * been, where production names the issue after the week it closed in: "EfS uge
     * 27". Passing null is what makes ${week} mean the cut-off's week.
     *
     * A real multi-week issue still names itself the other way. That is a property
     * of the issue somebody authored, not of a period nobody has authored yet.
     */
    private static Map<String, String> suggestedNames(Date to, ZoneId zone,
                                                      Map<String, String> namePatterns) {
        Map<String, String> out = new LinkedHashMap<>();
        if (namePatterns == null || namePatterns.isEmpty()) {
            return out;
        }
        IssueNaming.Numbers numbers = IssueNaming.derive(to, null, zone, null);
        for (Map.Entry<String, String> e : namePatterns.entrySet()) {
            String pattern = e.getValue();
            if (pattern == null || pattern.isBlank() || !IssueNaming.isExpandable(pattern)) {
                continue;
            }
            out.put(e.getKey(), IssueNaming.expand(pattern, numbers));
        }
        return out;
    }

    private static Issue latestAtOrBefore(List<Issue> ascending, Date when) {
        Issue found = null;
        for (Issue i : ascending) {
            if (i.effectiveCutoff() != null && i.effectiveCutoff().getTime() <= when.getTime()) {
                found = i;
            }
        }
        return found;
    }

    private static Issue earliestAfter(List<Issue> ascending, Date when) {
        for (Issue i : ascending) {
            if (i.effectiveCutoff() != null && i.effectiveCutoff().getTime() >= when.getTime()) {
                return i;
            }
        }
        return null;
    }
}
