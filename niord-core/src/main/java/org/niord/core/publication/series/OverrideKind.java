package org.niord.core.publication.series;

/**
 * A curator adding a message to an issue, or removing one.
 *
 * Persisted as a native MySQL ENUM, which REJECTS values outside this list -- adding a constant
 * later needs an ALTER TABLE.
 */
public enum OverrideKind {
    INCLUDE,
    EXCLUDE
}
