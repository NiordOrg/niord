package org.niord.core.publication.series.resolve;

/**
 * Which time predicate a series' membership uses.
 *
 * The two are not variants of one rule with a different bound. Only
 * PUBLISHED_IN_INTERVAL has a lower bound at all, and only it chains off the
 * previous issue -- which is also why only it can tile, gap-detect, or recover a
 * missing period. IN_FORCE_AT_CUTOFF issues overlap instead: the 2026 and 2027
 * firing-areas issues share 31 of their 32 members, and applying a chained
 * interval to the 2027 issue would leave it holding one message instead of
 * thirty-two.
 */
public enum TimeRelation {
    PUBLISHED_IN_INTERVAL,
    IN_FORCE_AT_CUTOFF
}
