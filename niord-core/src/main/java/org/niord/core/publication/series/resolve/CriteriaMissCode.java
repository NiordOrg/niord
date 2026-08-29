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
 * Why the criteria did NOT match a message. The omissions panel.
 *
 * Six codes, one per way the predicate can drop a row, and they are exhaustive
 * by construction: every non-matching MembershipReason maps to exactly one.
 *
 * This vocabulary is DISJOINT from ResolutionWarningCode and always has been
 * deliberately so. An earlier wording mixed four values drawn from across both
 * sets, which is how a backend ships four of twelve while the UI renders and
 * translates all twelve.
 */
public enum CriteriaMissCode {

    /** Published at or before the previous issue's cut-off. Carries publishDateFrom and intervalFrom. */
    BEFORE_INTERVAL,

    /** Published after this issue's cut-off. Carries publishDateFrom and cutoff. */
    AFTER_CUTOFF,

    /** No publishDateFrom at all, so it cannot be compared to any bound. */
    NO_PUBLISH_DATE,

    /** Its window had closed by the cut-off. Carries publishDateTo and cutoff. */
    NOT_ALIVE_AT_CUTOFF,

    /** Status is not one of the public statuses. Carries status. */
    STATUS_NOT_PUBLIC,

    /** A criterion did not match. Carries kind, operator, expected and actual. */
    CRITERION_MISMATCH;

    /** The single mapping from a decision reason. Total, so a new reason cannot go unhandled. */
    public static CriteriaMissCode of(MembershipReason reason) {
        return switch (reason) {
            case BEFORE_INTERVAL -> BEFORE_INTERVAL;
            case AFTER_CUTOFF -> AFTER_CUTOFF;
            case NO_PUBLISH_DATE -> NO_PUBLISH_DATE;
            case NOT_ALIVE_AT_CUTOFF -> NOT_ALIVE_AT_CUTOFF;
            case STATUS_NOT_PUBLIC -> STATUS_NOT_PUBLIC;
            case CRITERIA_MISMATCH -> CRITERION_MISMATCH;
            case IN_INTERVAL, IN_FORCE_AT_CUTOFF, MANUAL_INCLUDE, MANUAL_EXCLUDE ->
                    throw new IllegalArgumentException(reason + " is not a criteria miss");
        };
    }
}
