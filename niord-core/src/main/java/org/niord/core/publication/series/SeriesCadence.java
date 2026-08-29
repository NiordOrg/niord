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
 * How often an issue is nominally released. Maps one-to-one to the legacy PeriodicalType,
 * with NONE standing where legacy had null.
 *
 * NONE MEANS "NO SCHEDULE", NOT "ONE-OFF". Those are different questions and this
 * enum only answers the first: eleven NCAGS editions have no schedule and are
 * plainly a series. What kind of thing a publication is lives in {@link SeriesKind}.
 *
 * Persisted as a native MySQL ENUM, which REJECTS values outside this list -- adding a constant
 * later needs an ALTER TABLE.
 */
public enum SeriesCadence {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY
}
