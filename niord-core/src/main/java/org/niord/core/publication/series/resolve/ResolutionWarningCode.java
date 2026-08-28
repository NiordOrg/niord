/*
 * Copyright 2026 Danish Maritime Authority.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.niord.core.publication.series.resolve;

/**
 * Something worth flagging about a resolution that is not, in itself, a miss.
 * The warnings list.
 *
 * Five codes, DISJOINT from CriteriaMissCode -- no value ever appears in both.
 * The older names CANCELLED_OR_EXPIRED_ALIVE_AT_CUTOFF, TYPE_MUTATED_SINCE_FREEZE
 * and PUBLISH_DATE_NULL are dropped rather than aliased: emitting one is a bug,
 * not a compatibility gesture.
 *
 * Type drift is deliberately NOT one of them, and used to be. It is not a fact
 * about a resolution at all -- a resolution has no frozen snapshot to compare
 * against -- and it is already answered where it can be: the member list computes
 * it per row, against the live message, and shows what the value is now. A second
 * answer as a whole-issue warning would be the same question with less
 * information, computed from a different place.
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
