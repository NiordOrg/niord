package org.niord.core.publication.series;

/**
 * How often an issue is nominally released. Maps one-to-one to the legacy PeriodicalType,
 * with NONE standing where legacy had null. cadence = NONE IS the one-off; there is no separate
 * one-off content mode.
 *
 * Persisted as a native MySQL ENUM, which REJECTS values outside this list -- adding a constant
 * later needs an ALTER TABLE.
 */
public enum SeriesCadence {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY
}
