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
 * What kind of thing a publication is: something that comes out on a schedule,
 * something that comes out irregularly, or something published once.
 *
 * SEPARATE FROM CADENCE ON PURPOSE. Cadence answers "how often", and its NONE
 * value answers "there is no schedule" -- which is true of two quite different
 * things. Eleven NCAGS editions, eight ice-service notices and four editions of
 * Dansk Fyrliste have no schedule and are unmistakably series; five other
 * publications have no schedule because they were published once and stopped.
 * Reading cadence = NONE as "one-off" merges them, and the merge is not
 * academic: it is what put an eleven-issue series in the one-off list.
 *
 * A STORED FACT, NOT A ROW COUNT. The kind is decided once, at import, and read
 * everywhere after that. Deriving it live from "how many issues does it have"
 * would make a publication's kind change underneath whoever added the second
 * issue -- silently, and in the direction nobody asked for.
 *
 * Persisted as a native MySQL ENUM, which REJECTS values outside this list --
 * adding a constant later needs an ALTER TABLE.
 */
public enum SeriesKind {

    /** Released on a cadence: weekly EfS, the yearly roll-ups, P and T. */
    SCHEDULED,

    /**
     * Released irregularly, but more than once. A real series with no calendar:
     * a new edition appears when there is one to publish.
     */
    UNSCHEDULED,

    /**
     * Published once. Holds exactly one issue and refuses a second -- a one-off
     * that turned out to recur is an UNSCHEDULED series, and saying so is a
     * decision somebody makes rather than a side effect of an upload.
     */
    ONE_OFF
}
