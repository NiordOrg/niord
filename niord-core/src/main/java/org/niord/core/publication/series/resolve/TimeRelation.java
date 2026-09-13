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
 * Which time predicate a series' membership uses.
 *
 * The three are not variants of one rule with a different bound.
 *
 * PUBLISHED_IN_INTERVAL chains off the previous issue, so its issues TILE and
 * can be gap-detected and recovered a period at a time. IN_FORCE_AT_CUTOFF
 * issues overlap instead: the 2026 and 2027 firing-areas issues share 31 of
 * their 32 members, and applying a chained interval to the 2027 issue would
 * leave it holding one message instead of thirty-two -- which is why it has no
 * lower bound at all.
 *
 * COMPILED_FROM_SOURCE tiles like the first, and there the resemblance stops: it
 * runs no query over messages. Its members are the union of the frozen member
 * rows of ANOTHER series' published issues whose cut-off falls inside the
 * period, so what it reproduces is what those issues actually printed, curation
 * of them included. That is the whole point of the regime -- an annual that
 * reads like the year's weeklies stapled together -- and it is why a compilation
 * carries neither criteria nor a liveness flag: liveness was judged at each
 * source issue's own cut-off, and re-judging it at the year's end would drop
 * what the weeklies rightly carried.
 *
 * Persisted as a native MySQL ENUM, which REJECTS values outside this list --
 * adding a constant later needs an ALTER TABLE.
 */
public enum TimeRelation {
    PUBLISHED_IN_INTERVAL,
    IN_FORCE_AT_CUTOFF,
    COMPILED_FROM_SOURCE;

    /**
     * Whether this relation's issues TILE: one period ends where the next begins.
     *
     * The one question almost every caller was really asking when it compared
     * against PUBLISHED_IN_INTERVAL -- the interval shaping, the chain, the
     * overlap refusal, gap detection, the retro-create flow, S-4 and the
     * successor's lower bound. Written once so a third relation that tiles
     * reaches all of them together rather than one at a time.
     */
    public boolean tiles() {
        return this == PUBLISHED_IN_INTERVAL || this == COMPILED_FROM_SOURCE;
    }

    /**
     * Whether membership is derived from another series' frozen rows rather than
     * from a query over messages.
     *
     * Separate from {@link #tiles()} because the two questions have different
     * answers for PUBLISHED_IN_INTERVAL, and every caller that has to know which
     * derivation runs is asking this one.
     */
    public boolean compiled() {
        return this == COMPILED_FROM_SOURCE;
    }
}
