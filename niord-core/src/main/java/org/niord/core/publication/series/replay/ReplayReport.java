package org.niord.core.publication.series.replay;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a replay found.
 *
 * Carries the skipped issues as loudly as the diffed ones. A replay that says
 * "1,077 issues, 40 diffs" and does not mention that 58 of them were never
 * compared reads as far better news than it is -- and the skips are not
 * incidental: roughly 48 publications have no membership semantics at all and
 * ten annexes have no recoverable member list, so a large silent skip count is
 * the expected state rather than a warning sign. It still has to be visible.
 */
public class ReplayReport {

    /** Why an issue was not compared. */
    public enum SkipReason {
        /** contentMode is not GENERATED_FROM_QUERY, or there is no criteria document. */
        NO_MEMBERSHIP_SEMANTICS,
        /** A file was replaced by hand (C6). "Reproducible from the member list" does not hold. */
        FILE_REPLACED_BY_HAND,
        /** No interval start, so there is no window to resolve over. */
        NO_INTERVAL,
        /** No cut-off, stamped or recovered, so the window has no end. */
        NO_CUTOFF,
        /**
         * The window has both bounds and they leave no time in it -- the cut-off
         * lands on or before the interval start. Nothing can be resolved over an
         * empty period, and building the interval anyway raises out of the whole
         * replay, which takes the historical diagnostic report down with it.
         */
        EMPTY_INTERVAL,
        /**
         * No snapshot header (time relation / alive-at-cut-off) on the issue. The
         * replay reads the issue's own header and never the series' row, so a
         * row without one is not comparable rather than comparable under a guess.
         */
        NO_SNAPSHOT_HEADER
    }

    /**
     * One issue's difference, keyed on uid.
     *
     * {@code missing} is recorded-but-not-resolved and {@code extra} is
     * resolved-but-not-recorded. Both directions matter and they mean opposite
     * things: missing is the new engine dropping something the archive served,
     * extra is it adding something the archive did not.
     */
    public record IssueDiff(String publicId, String seriesId,
                            Set<String> missing, Set<String> extra) {

        public boolean isEmpty() {
            return missing.isEmpty() && extra.isEmpty();
        }

        public int size() {
            return missing.size() + extra.size();
        }
    }

    private final List<IssueDiff> diffs = new ArrayList<>();
    private final Map<String, SkipReason> skipped = new LinkedHashMap<>();
    private int compared;
    private int identical;

    public List<IssueDiff> diffs() {
        return diffs;
    }

    public Map<String, SkipReason> skipped() {
        return skipped;
    }

    public int compared() {
        return compared;
    }

    public int identical() {
        return identical;
    }

    public void recordIdentical() {
        compared++;
        identical++;
    }

    public void recordDiff(IssueDiff diff) {
        compared++;
        diffs.add(diff);
    }

    public void recordSkip(String publicId, SkipReason reason) {
        skipped.put(publicId, reason);
    }

    /** Skips grouped by reason, for the report a person reads. */
    public Map<SkipReason, Integer> skipCounts() {
        Map<SkipReason, Integer> out = new LinkedHashMap<>();
        skipped.values().forEach(r -> out.merge(r, 1, Integer::sum));
        return out;
    }

    public int total() {
        return compared + skipped.size();
    }
}
