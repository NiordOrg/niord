package org.niord.core.publication.series;

/**
 * Who performed an audited action. SYSTEM exists because an AUTO_RELEASE publish has no user,
 * and IMPORT because the importer writes audit rows that were never anyone's action.
 *
 * Persisted as a native MySQL ENUM, which REJECTS values outside this list -- adding a constant
 * later needs an ALTER TABLE.
 */
public enum ActorKind {
    USER,
    SYSTEM,
    IMPORT
}
