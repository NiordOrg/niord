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
 * Nominal weekday of the cut-off. An enum per weekday rather than java.time.DayOfWeek because
 * no entity in this codebase persists a java.time type.
 *
 * ALL SEVEN, and that is the point: the weekly EfS is released every WEDNESDAY, and S-5 makes
 * this field required for any series with cadence = WEEKLY. The enum originally held MONDAY and
 * SUNDAY alone -- the specification writes the type as "MONDAY...SUNDAY" and its DDL column
 * transcribed the ellipsis as a two-element list -- so the primary production series could not
 * record its own release day. See V3__cutoff_day_all_seven.sql.
 *
 * Persisted as a native MySQL ENUM, which REJECTS values outside this list -- adding a constant
 * later needs an ALTER TABLE.
 */
public enum CutoffDay {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY
}
