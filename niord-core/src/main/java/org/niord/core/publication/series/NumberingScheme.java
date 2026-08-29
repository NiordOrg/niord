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
 * Which numbers an issue derives, and therefore what ${year} means -- calendar year against
 * ISO week-based year. YEAR_EDITION implies the edition resets each year; EDITION_SEQUENCE implies
 * it never does, which is why no separate reset column exists.
 *
 * Persisted as a native MySQL ENUM, which REJECTS values outside this list -- adding a constant
 * later needs an ALTER TABLE.
 */
public enum NumberingScheme {
    ISO_WEEK_YEAR,
    YEAR_EDITION,
    MONTH_YEAR,
    EDITION_SEQUENCE,
    NONE
}
