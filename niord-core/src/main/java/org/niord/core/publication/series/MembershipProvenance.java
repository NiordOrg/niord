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

package org.niord.core.publication.series;

/**
 * How much the recorded membership can be trusted as an oracle. EXACT reproduces from the
 * criteria; EXPLAINED_DIFF differs for a recorded reason; UNION_SNAPSHOT holds more than any single
 * instant produces; NO_MEMBERSHIP has no query at all; IMPORTED was named by hand and never
 * derived, so there is nothing for a replay to reproduce.
 *
 * Persisted as a native MySQL ENUM, which REJECTS values outside this list -- adding a constant
 * later needs an ALTER TABLE.
 */
public enum MembershipProvenance {
    EXACT,
    EXPLAINED_DIFF,
    UNION_SNAPSHOT,
    NO_MEMBERSHIP,

    /**
     * Named by hand, never derived.
     *
     * The six tag-carrying annexes are the case: the locked tag holds exactly one
     * message, and no query of any shape could have selected it, because the only
     * discriminator between the two live annex messages in a year is the message
     * body. So the row is real membership with no derivation behind it -- which is
     * neither EXACT (nothing reproduces it), nor a diff (there is nothing to
     * differ FROM), nor a union (it is one message named once), nor
     * NO_MEMBERSHIP (there IS a member, and discarding it is what the ruling
     * exists to prevent).
     *
     * Added by V4__membership_provenance_imported.sql.
     */
    IMPORTED
}
