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

package org.niord.core.publication.series.replay;

/**
 * The ways a replayed member list is allowed to differ from the recorded one.
 *
 * Every one of these was found by measurement against the real estate, and NONE
 * of them is a defect in the predicate core. That is the finding the whole
 * replay rests on: the query is right and the archive is what it is. A
 * divergence that does not fit one of these classes is therefore a bug, which is
 * exactly what makes the manifest a gate rather than a suppression list.
 *
 * Naming one of these on a manifest entry is a claim about MECHANISM, not a
 * label of convenience. If the mechanism does not fit, the honest move is to
 * add a class with its own evidence rather than stretch an existing one.
 */
public enum DivergenceClass {

    /**
     * Type mutated after release. P&T, 12 messages across 26 issues.
     *
     * {@code type} is mutable and unversioned. All twelve are PERMANENT_NOTICE
     * today; all were P or T when their issue was released. The query reads
     * TODAY's type, so it cannot reproduce a set that depended on yesterday's.
     */
    TYPE_MUTATED_AFTER_RELEASE,

    /**
     * Cancelled with a future publishDateTo -- the R5 class.
     *
     * updateStatus() stamps publishDateTo = now only when it is null or already
     * past, so an editor-set future validity end survives cancellation.
     * Date-alive, status-dead: the query KEEPS a notice legacy dropped.
     */
    CANCELLED_WITH_FUTURE_VALIDITY,

    /**
     * Alive-at-cut-off drops. Blank/sticky era, 73 members across 39 of 116.
     *
     * The sticky default's {@code || data.isIncluded} makes membership
     * permanent. The conjunct has to be off for that regime, and where the
     * import could not tell, the replay differs.
     */
    ALIVE_AT_CUTOFF_REGIME,

    /**
     * publishDateFrom IS NULL. One message, eight issues -- NM-780-18.
     *
     * CriteriaHelper.overlaps() ORs isNull(publishDateFrom), so the message is
     * "in force" from the beginning of time and leaks into every P&T issue from
     * 2017-01 to 2018-46 -- including issues released a year before it existed.
     * THE CORRECTED RULE AND THE LEGACY CODE DISAGREE HERE, deliberately.
     */
    NULL_PUBLISH_FROM,

    /**
     * Back-dated publishDateFrom. One message, two issues -- NM-1116-22.
     *
     * Published 2023-02-20 carrying a 2022 publish-from date. Legacy filed it
     * where it became public; the rule files it a quarter earlier.
     */
    BACK_DATED_PUBLISH_FROM,

    /**
     * Legacy tag staleness. P&T uge 6/2026, 8 members.
     *
     * startRecordingPublication() only ever ADDS, so anything cancelled while
     * the publication was not RECORDING is never cleaned out. THE GROUND TRUTH
     * IS WRONG HERE, NOT THE PREDICATE -- which is why this class exists at all
     * rather than being filed as a bug.
     */
    LEGACY_TAG_STALENESS,

    /**
     * Union over a long window. Four annual issues.
     *
     * Skydeomraader 2018 (71 members), 2020-ed1 (66), 2022-ed1 (41), EfS A
     * 2020-ed1 (56). Each holds two full annual cohorts. Every publishDate
     * boundary was swept +/-1 ms across the whole series history: NO INSTANT
     * REPRODUCES THE RECORDED SET. These issues are the answer to no query.
     */
    UNION_OVER_LONG_WINDOW,

    /**
     * Tag collision and name drift. Six tags shared, nine orphaned.
     *
     * nm-w01-2025 and nm-pt-w01-2025 are each shared by THREE publications.
     * nm-pt-w02-2025 belongs to the "uge 3 - 2025" publication; nm-pt-w03-2025
     * does not exist. The recorded set is not attributable to one issue.
     */
    TAG_COLLISION_OR_NAME_DRIFT,

    /**
     * Cut-off destroyed by a later save. 3 weekly, 6 P&T, 22 of 35 non-weekly.
     *
     * Retire/republish batches re-stamp {@code updated} -- four issues within 61
     * seconds on 2025-01-09. The recovered cut-off is therefore the batch's
     * timestamp rather than the release's, and the window is wrong by however
     * long sat between them.
     */
    CUTOFF_DESTROYED_BY_LATER_SAVE,

    /**
     * Manual tag edit. One in ~10,200 members -- NM-375-24.
     *
     * Published three hours inside its window, cancelled a month after the
     * cut-off, absent from both that week's tags while its two-minutes-earlier
     * neighbours are present. Attribution by elimination; THE CHANNEL IS
     * UNAUDITED BY DESIGN, so no better evidence exists or can exist.
     */
    MANUAL_TAG_EDIT,

    /**
     * Structurally irreproducible: the 2019 changeover fortnight, 3 issues.
     *
     * Two tags recorded simultaneously for six days; 12 messages sit in 2-3
     * issues at once and 2 in none. Tiling does not hold, so no interval
     * assignment can be correct for all of them.
     */
    CHANGEOVER_FORTNIGHT_2019,

    /**
     * No recoverable member list. Ten annexes.
     *
     * The issue has no tag and no query that could have produced one. There is
     * nothing to reproduce, and a replay of it is comparing against absence.
     */
    NO_RECOVERABLE_MEMBER_LIST
}
