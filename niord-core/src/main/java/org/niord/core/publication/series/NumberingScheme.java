package org.niord.core.publication.series;

/**
 * Which numbers an issue derives, and therefore what ${year} means -- calendar year against
 * ISO week-based year. YEAR_EDITION implies the edition resets each year; EDITION_SEQUENCE implies
 * it never does, which is why no separate reset column exists.
 *
 * Persisted as a native MySQL ENUM, which REJECTS values outside this list -- adding a constant
 * later needs an ALTER TABLE.
 */
public enum NumberingScheme {
    ISO_WEEK_YEAR,
    YEAR_EDITION,
    MONTH_YEAR,
    EDITION_SEQUENCE,
    NONE
}
