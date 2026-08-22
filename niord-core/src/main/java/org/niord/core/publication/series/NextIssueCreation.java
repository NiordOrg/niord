package org.niord.core.publication.series;

/**
 * Whether publishing an issue auto-creates the next one starting exactly at the stamped
 * cut-off. That chaining is what removes the drift a nominal plus-seven-days produced.
 *
 * Persisted as a native MySQL ENUM, which REJECTS values outside this list -- adding a constant
 * later needs an ALTER TABLE.
 */
public enum NextIssueCreation {
    AUTO_ON_PUBLISH,
    MANUAL
}
