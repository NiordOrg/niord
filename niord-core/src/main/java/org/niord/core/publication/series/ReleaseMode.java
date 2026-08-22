package org.niord.core.publication.series;

/**
 * Manual gate against fully automatic release. Carried even though no task builds the automatic
 * path yet, because under AUTO_RELEASE a publish has no human actor -- which is what forces a
 * nullable publishedBy and an actorKind on the audit.
 *
 * Persisted as a native MySQL ENUM, which REJECTS values outside this list -- adding a constant
 * later needs an ALTER TABLE.
 */
public enum ReleaseMode {
    MANUAL_GATE,
    AUTO_RELEASE
}
