package org.niord.core.publication.series;

/**
 * Where an interval bound came from -- a real column, not a derivation. No derivation can
 * produce MANUAL, because "an admin typed this" is not recoverable from the value itself.
 *
 * Persisted as a native MySQL ENUM, which REJECTS values outside this list -- adding a constant
 * later needs an ALTER TABLE.
 */
public enum IntervalBoundSource {
    STAMPED,
    NOMINAL,
    RECOVERED,
    MANUAL
}
