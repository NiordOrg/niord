package org.niord.core.publication.series;

/**
 * Issue lifecycle. OPEN is being assembled; PUBLISHED is released; RETIRED is withdrawn.
 *
 * Persisted as a native MySQL ENUM, which REJECTS values outside this list -- adding a constant
 * later needs an ALTER TABLE.
 */
public enum IssueStatus {
    OPEN,
    PUBLISHED,
    RETIRED
}
