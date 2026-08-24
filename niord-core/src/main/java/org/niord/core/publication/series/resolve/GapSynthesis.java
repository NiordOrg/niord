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

    /** What a series names a period, per language. */
    public record Patterns(String namePattern, String fileNamePattern) {
    }

    /** What this period would be called, per language. */
    public record Suggestion(String name, String fileName) {
    }

    /**
     * One synthesized row.
     *
     * suggestions is per language rather than one string, because a series
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
                      Map<String, Suggestion> suggestions) {
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
     * @param patterns the naming patterns per language; may be empty
     */
    public static List<Row> synthesize(GapDetection.Gate gate,
                                       String seriesId,
                                       List<Issue> issues,
                                       long periodMillis,
                                       ZoneId zone,
                                       Map<String, Patterns> patterns,
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
                    zone, patterns));
        }

        // After the newest issue. GapDetection.gaps looks only BETWEEN cut-offs, so
        // without this a series that simply stopped shows no gap at all -- which is
        // the one case somebody is actually looking for. Periods whose cut-off has
        // already passed are MISSING; the first that has not is UPCOMING, and there
        // is only ever one of those.
        //
        // Only from a cut-off that has actually PASSED. While the newest issue's own
        // cut-off is still ahead, that issue IS the period being worked toward, and
        // a row past it describes a period nobody has started -- offering a
        // retro-create for the week after the one currently open.
        Issue newest = issues.get(issues.size() - 1);
        if (newest.effectiveCutoff() != null && now != null
                && newest.effectiveCutoff().getTime() <= now.getTime()) {
            Date from = newest.effectiveCutoff();
            Issue preceding = newest;
            // Bounded by the dormancy gate rather than by a literal: a series nobody
            // has published to for DORMANCY_PERIODS is already gated off above, so
            // this cannot run away.
            while (true) {
                Date to = GapDetection.nextCutoff(from, periodMillis);
                boolean passed = to.getTime() <= now.getTime();
                out.add(row(passed ? RowKind.MISSING : RowKind.UPCOMING, seriesId, from, to,
                        preceding, null, zone, patterns));
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
                           Map<String, Patterns> patterns) {
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
                suggestions(to, zone, patterns));
    }

    /**
     * Where a pseudo-row's bound came from: the neighbouring issue it chains
     * off, or the cadence where there is no neighbour.
     *
     * RECOVERED is carried through rather than collapsed into NOMINAL. Most of
     * the imported archive has a recovered cut-off, so collapsing it would drop
     * the provenance marker from precisely the rows where somebody needs to know
     * how firm the bound is before creating an issue against it.
     *
     * A neighbour whose cut-off an admin typed reads as STAMPED here. The marker
     * distinguishes a recorded bound from an inferred one, and a hand-entered
     * cut-off is recorded; MANUAL itself would claim somebody authored THIS
     * period, which nobody has -- that is what makes the row a pseudo-row.
     */
    private static IntervalBoundSource boundSource(Issue neighbour) {
        if (neighbour == null || neighbour.cutoffSource() == null) {
            return IntervalBoundSource.NOMINAL;
        }
        return switch (neighbour.cutoffSource()) {
            case STAMPED, MANUAL -> IntervalBoundSource.STAMPED;
            case RECOVERED -> IntervalBoundSource.RECOVERED;
            case NOMINAL -> IntervalBoundSource.NOMINAL;
        };
    }

    /**
     * What each language would call this period -- its title and its file name.
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
    private static Map<String, Suggestion> suggestions(Date to, ZoneId zone,
                                                       Map<String, Patterns> patterns) {
        Map<String, Suggestion> out = new LinkedHashMap<>();
        if (patterns == null || patterns.isEmpty()) {
            return out;
        }
        IssueNaming.Numbers numbers = IssueNaming.derive(to, null, zone, null);
        for (Map.Entry<String, Patterns> e : patterns.entrySet()) {
            Patterns p = e.getValue();
            if (p == null) {
                continue;
            }
            String name = expand(p.namePattern(), numbers);
            String fileName = expand(p.fileNamePattern(), numbers);
            // A language whose patterns expand to nothing contributes no row at
            // all. An entry carrying two nulls would prefill a retro-create with
            // a blank name, which is worse than offering none.
            if (name != null || fileName != null) {
                out.put(e.getKey(), new Suggestion(name, fileName));
            }
        }
        return out;
    }

    private static String expand(String pattern, IssueNaming.Numbers numbers) {
        if (pattern == null || pattern.isBlank() || !IssueNaming.isExpandable(pattern)) {
            return null;
        }
        return IssueNaming.expand(pattern, numbers);
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
