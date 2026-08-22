package org.niord.core.publication.series;

/**
 * Nominal weekday of the cut-off. An enum per weekday rather than java.time.DayOfWeek because
 * no entity in this codebase persists a java.time type.
 *
 * Persisted as a native MySQL ENUM, which REJECTS values outside this list -- adding a constant
 * later needs an ALTER TABLE.
 */
public enum CutoffDay {
    MONDAY,
    SUNDAY
}
