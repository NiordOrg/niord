package org.niord.core.publication.series;

/**
 * Page orientation for a generated report.
 *
 * Persisted as a native MySQL ENUM, which REJECTS values outside this list -- adding a constant
 * later needs an ALTER TABLE.
 */
public enum PageOrientation {
    PORTRAIT,
    LANDSCAPE
}
