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
 * Whether a message is a member, and why.
 *
 * The reason costs nothing extra -- the UI renders a "why is this here" line
 * per member regardless -- and it is what makes an exclusion reportable instead
 * of invisible.
 */
public record MemberDecision(String uid, boolean member, MembershipReason reason) {

    static MemberDecision included(String uid, MembershipReason reason) {
        return new MemberDecision(uid, true, reason);
    }

    static MemberDecision excluded(String uid, MembershipReason reason) {
        return new MemberDecision(uid, false, reason);
    }

    /** True when the message was excluded for a reason worth surfacing rather than assuming. */
    public boolean isReportableOmission() {
        return !member && reason == MembershipReason.NO_PUBLISH_DATE;
    }
}
