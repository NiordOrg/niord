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

import java.util.Date;

/**
 * An issue's time window: the previous issue's stamped cut-off, and its own.
 *
 * The window is HALF-OPEN -- (previousCutoff, cutoff] -- strict at the lower
 * bound, closed at the upper. A message stamped exactly on a shared cut-off
 * therefore belongs to the earlier issue, and to exactly one.
 *
 * previousCutoff is null when there is no lower bound: the first issue of a
 * series, and every IN_FORCE_AT_CUTOFF issue, which never has one.
 */
public record Interval(Date previousCutoff, Date cutoff) {

    public Interval {
        if (cutoff == null) {
            throw new IllegalArgumentException("an issue always has a cut-off");
        }
        if (previousCutoff != null && !previousCutoff.before(cutoff)) {
            throw new IllegalArgumentException(
                    "previousCutoff " + previousCutoff.getTime() + " must precede cutoff " + cutoff.getTime());
        }
    }

    /** An interval with no lower bound. */
    public static Interval upTo(Date cutoff) {
        return new Interval(null, cutoff);
    }
}
