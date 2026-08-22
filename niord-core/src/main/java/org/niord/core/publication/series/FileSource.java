package org.niord.core.publication.series;

/**
 * Whether the file behind an issue was generated from a report or uploaded by hand.
 *
 * Persisted as a native MySQL ENUM, which REJECTS values outside this list -- adding a constant
 * later needs an ALTER TABLE.
 */
public enum FileSource {
    GENERATED,
    UPLOADED
}
