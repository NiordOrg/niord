package org.niord.core.publication.series;

/**
 * The per-series cutover switch. While LEGACY the public adapter serves this series from the
 * legacy table; while NEW it serves from the new one and excludes the legacy rows it replaced. This
 * is what makes the shadow-run union non-duplicating and rollback a flag flip with no data change.
 *
 * Persisted as a native MySQL ENUM, which REJECTS values outside this list -- adding a constant
 * later needs an ALTER TABLE.
 */
public enum PublicAuthority {
    LEGACY,
    NEW
}
