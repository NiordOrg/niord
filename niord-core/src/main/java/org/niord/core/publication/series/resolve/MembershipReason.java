/*
 * Copyright 2026 Danish Emergency Management Agency.
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
 * Why a message is or is not a member.
 *
 * Carried on every decision rather than only on inclusions, because the reason
 * is what the "hvorfor er den med" line renders, and because a message dropped
 * silently is indistinguishable from one that was never considered. NO_PUBLISH_DATE
 * exists for exactly that: those messages are excluded, and they must be
 * reportable as excluded rather than absent.
 */
public enum MembershipReason {

    /** Included: published inside (previousCutoff, cutoff]. */
    IN_INTERVAL,

    /** Included: in force at the cut-off, with no lower bound applied. */
    IN_FORCE_AT_CUTOFF,

    /** Included: a curator added it by hand. */
    MANUAL_INCLUDE,

    /** Excluded: status is not one of the public statuses. */
    STATUS_NOT_PUBLIC,

    /** Excluded: does not match the series' own criteria. */
    CRITERIA_MISMATCH,

    /**
     * Excluded: no publishDateFrom. Reported, never silently dropped -- the
     * column is nullable even on a published message, and one such message
     * would otherwise leak into every issue whose bound it cannot be compared to.
     */
    NO_PUBLISH_DATE,

    /** Excluded: published at or before the previous issue's cut-off. */
    BEFORE_INTERVAL,

    /** Excluded: published after this issue's cut-off. */
    AFTER_CUTOFF,

    /** Excluded: its publication window had closed by the cut-off. */
    NOT_ALIVE_AT_CUTOFF,

    /** Excluded: a curator removed it by hand. */
    MANUAL_EXCLUDE
}
