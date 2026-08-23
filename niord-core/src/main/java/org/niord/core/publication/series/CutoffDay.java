package org.niord.core.publication.series;

/**
 * Nominal weekday of the cut-off. An enum per weekday rather than java.time.DayOfWeek because
 * no entity in this codebase persists a java.time type.
 *
 * ALL SEVEN, and that is the point: the weekly EfS is released every WEDNESDAY, and S-5 makes
 * this field required for any series with cadence = WEEKLY. The enum originally held MONDAY and
 * SUNDAY alone -- the specification writes the type as "MONDAY...SUNDAY" and its DDL column
 * transcribed the ellipsis as a two-element list -- so the primary production series could not
 * record its own release day. See V3__cutoff_day_all_seven.sql.
 *
 * Persisted as a native MySQL ENUM, which REJECTS values outside this list -- adding a constant
 * later needs an ALTER TABLE.
 */
public enum CutoffDay {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY
}
