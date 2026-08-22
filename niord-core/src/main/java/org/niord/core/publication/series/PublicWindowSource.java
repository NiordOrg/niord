package org.niord.core.publication.series;

/**
 * Whether the public visibility window was derived from the interval or set by hand.
 *
 * Persisted as a native MySQL ENUM, which REJECTS values outside this list -- adding a constant
 * later needs an ALTER TABLE.
 */
public enum PublicWindowSource {
    DERIVED,
    MANUAL
}
