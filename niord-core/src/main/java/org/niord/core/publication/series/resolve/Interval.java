package org.niord.core.publication.series.resolve;

import java.util.Date;

/**
 * An issue's time window: the previous issue's stamped cut-off, and its own.
 *
 * The window is HALF-OPEN -- (previousCutoff, cutoff] -- strict at the lower
 * bound, closed at the upper. A message stamped exactly on a shared cut-off
 * therefore belongs to the earlier issue, and to exactly one.
 *
 * previousCutoff is null when there is no lower bound: the first issue of a
 * series, and every IN_FORCE_AT_CUTOFF issue, which never has one.
 */
public record Interval(Date previousCutoff, Date cutoff) {

    public Interval {
        if (cutoff == null) {
            throw new IllegalArgumentException("an issue always has a cut-off");
        }
        if (previousCutoff != null && !previousCutoff.before(cutoff)) {
            throw new IllegalArgumentException(
                    "previousCutoff " + previousCutoff.getTime() + " must precede cutoff " + cutoff.getTime());
        }
    }

    /** An interval with no lower bound. */
    public static Interval upTo(Date cutoff) {
        return new Interval(null, cutoff);
    }
}
