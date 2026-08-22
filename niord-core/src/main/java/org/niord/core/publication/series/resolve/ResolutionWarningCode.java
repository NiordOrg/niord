package org.niord.core.publication.series.resolve;

/**
 * Something worth flagging about a resolution that is not, in itself, a miss.
 * The warnings list.
 *
 * Six codes, DISJOINT from CriteriaMissCode -- no value ever appears in both.
 * The older names CANCELLED_OR_EXPIRED_ALIVE_AT_CUTOFF, TYPE_MUTATED_SINCE_FREEZE
 * and PUBLISH_DATE_NULL are dropped rather than aliased: emitting one is a bug,
 * not a compatibility gesture.
 */
public enum ResolutionWarningCode {

    /**
     * A member is CANCELLED or EXPIRED yet its publishDateTo still reaches the
     * cut-off. The ONLY acknowledgeable warning.
     *
     * An exclusions panel is structurally blind to this class -- the messages ARE
     * members, so they never appear as exclusions -- which is why it is a warning
     * rather than something a curator would notice unaided. Confirmed three times
     * independently: 11 weekly issues, 15 messages across 21 P&T issues, and 10
     * across two annuals.
     */
    CANCELLED_BUT_DATE_ALIVE(true),

    /** A frozen member's type no longer matches the live message. Type is mutable and unversioned. */
    TYPE_DRIFT(false),

    /** Messages were dropped for having no publishDateFrom. Reported, never silent. */
    NULL_PUBLISH_FROM_DROPPED(false),

    /** An override points at a message the criteria would no longer have considered. */
    STALE_OVERRIDE(false),

    /** Another issue of the same series shares members with this one. */
    OVERLAPPING_ISSUE(false),

    /** The member count passed the configured limit. */
    LIMIT_EXCEEDED(false);

    private final boolean acknowledgeable;

    ResolutionWarningCode(boolean acknowledgeable) {
        this.acknowledgeable = acknowledgeable;
    }

    /** Whether a curator can sign this off and proceed. */
    public boolean isAcknowledgeable() {
        return acknowledgeable;
    }
}
