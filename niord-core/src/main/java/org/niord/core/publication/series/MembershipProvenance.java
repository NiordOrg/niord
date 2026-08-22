package org.niord.core.publication.series;

/**
 * How much the recorded membership can be trusted as an oracle. EXACT reproduces from the
 * criteria; EXPLAINED_DIFF differs for a recorded reason; UNION_SNAPSHOT holds more than any single
 * instant produces; NO_MEMBERSHIP has no query at all.
 *
 * Persisted as a native MySQL ENUM, which REJECTS values outside this list -- adding a constant
 * later needs an ALTER TABLE.
 */
public enum MembershipProvenance {
    EXACT,
    EXPLAINED_DIFF,
    UNION_SNAPSHOT,
    NO_MEMBERSHIP
}
