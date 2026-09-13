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
 * Why a frozen member is in the snapshot: the criteria matched it, a curator added it, or it
 * came in with an import.
 *
 * Persisted as a native MySQL ENUM, which REJECTS values outside this list -- adding a constant
 * later needs an ALTER TABLE.
 */
public enum MemberSource {
    CRITERIA,
    OVERRIDE_INCLUDE,
    IMPORTED,

    /**
     * Carried over from a source issue that already printed it.
     *
     * A compilation runs no query: its members are the frozen rows of another
     * series' published issues, so CRITERIA would claim a derivation that never
     * happened and OVERRIDE_INCLUDE would claim a person decided it. The row also
     * carries which source issue owns it, which no other source has an answer to.
     */
    COMPILED
}
