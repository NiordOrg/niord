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
 * Which time predicate a series' membership uses.
 *
 * The two are not variants of one rule with a different bound. Only
 * PUBLISHED_IN_INTERVAL has a lower bound at all, and only it chains off the
 * previous issue -- which is also why only it can tile, gap-detect, or recover a
 * missing period. IN_FORCE_AT_CUTOFF issues overlap instead: the 2026 and 2027
 * firing-areas issues share 31 of their 32 members, and applying a chained
 * interval to the 2027 issue would leave it holding one message instead of
 * thirty-two.
 */
public enum TimeRelation {
    PUBLISHED_IN_INTERVAL,
    IN_FORCE_AT_CUTOFF
}
