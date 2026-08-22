package org.niord.core.publication.series;

/**
 * Page size for a generated report. All eight legacy values are kept: typing the field must not
 * also shrink the enumeration.
 *
 * Persisted as a native MySQL ENUM, which REJECTS values outside this list -- adding a constant
 * later needs an ALTER TABLE.
 */
public enum PageSize {
    A3,
    A4,
    A5,
    B4,
    B5,
    LETTER,
    LEGAL,
    LEDGER
}
