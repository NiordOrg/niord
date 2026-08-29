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

package org.niord.core.message;

import java.util.Set;

/**
 * What a set of {@code publication=} ids designates.
 *
 * The distinction this type exists to keep is between "no publication was named"
 * and "a publication was named and it contains nothing". They are different
 * answers -- the first means the search is unconstrained by publication, the
 * second means the answer is zero messages -- and collapsing them is the live
 * defect this redesign closes: today an id naming a publication with no message
 * tag falls through every filter and returns everything published.
 *
 * So {@link #designatesMemberSet()} is deliberately independent of member count.
 * A designation with zero members produces an explicit empty disjunction, never
 * a fall-through and never {@code in ()}.
 */
public record MemberSetDesignation(
        boolean designated,
        Set<String> memberUids,
        Set<String> tagIds) {

    /** No publication was named at all. */
    public static final MemberSetDesignation NONE =
            new MemberSetDesignation(false, Set.of(), Set.of());

    /**
     * At least one {@code publication=} id resolved to something the caller may
     * see -- regardless of how many messages that something contains.
     */
    public boolean designatesMemberSet() {
        return designated;
    }

    /** A designation that resolved, but to nothing. Zero messages, not "no filter". */
    public boolean isEmptyDesignation() {
        return designated && memberUids.isEmpty() && tagIds.isEmpty();
    }
}
