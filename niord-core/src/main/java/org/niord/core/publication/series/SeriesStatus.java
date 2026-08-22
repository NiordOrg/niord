package org.niord.core.publication.series;

/**
 * Series lifecycle. Only ACTIVE series appear in the "ny udgave" picker. RETIRED series are
 * excluded from gap warnings, but their published issues stay publicly visible and their files stay
 * at their links -- retiring a series is not retiring its issues.
 *
 * Persisted as a native MySQL ENUM, which REJECTS values outside this list -- adding a constant
 * later needs an ALTER TABLE.
 */
public enum SeriesStatus {
    DRAFT,
    ACTIVE,
    RETIRED
}
