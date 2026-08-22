package org.niord.core.publication.series;

/**
 * Why a frozen member is in the snapshot: the criteria matched it, a curator added it, or it
 * came in with an import.
 *
 * Persisted as a native MySQL ENUM, which REJECTS values outside this list -- adding a constant
 * later needs an ALTER TABLE.
 */
public enum MemberSource {
    CRITERIA,
    OVERRIDE_INCLUDE,
    IMPORTED
}
