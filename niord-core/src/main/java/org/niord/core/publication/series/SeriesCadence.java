package org.niord.core.publication.series;

/**
 * How often an issue is nominally released. Maps one-to-one to the legacy PeriodicalType,
 * with NONE standing where legacy had null.
 *
 * NONE MEANS "NO SCHEDULE", NOT "ONE-OFF". Those are different questions and this
 * enum only answers the first: eleven NCAGS editions have no schedule and are
 * plainly a series. What kind of thing a publication is lives in {@link SeriesKind}.
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
