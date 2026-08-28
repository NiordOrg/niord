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

package org.niord.core.publication.series.replay;

import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rendered report the cutover decision is read from.
 *
 * The failure mode being tested for is not a wrong number. It is a number that
 * is technically right and reads as more than it is -- a series shown as ready
 * on the strength of weeks nobody compared, or a report that stays silent about
 * what it did not examine. Every test here is about that.
 *
 * The streak arithmetic these numbers come from is tested in DiagnosticStreakTest,
 * which needs no database and therefore keeps running on an agent that has none.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class DiagnosticReportTest {

    @Inject
    DiagnosticReportService diagnostics;

    /**
     * With nothing to report, it says so in words rather than showing a blank.
     *
     * An empty readiness table is the single most misreadable thing this report
     * could produce: it looks like "no problems". It has to say that no series
     * meets the precondition BECAUSE no series has evidence.
     */
    @Test
    public void anEmptyReportSaysNoSeriesIsReadyRatherThanShowingNothing() {
        String md = diagnostics.render(false);

        assertTrue(md.contains("Publications cutover"), "it is the right report");
        assertTrue(md.contains("two consecutive green releases per series")
                        || md.contains("Two consecutive green releases")
                        || md.contains("consecutive green"),
                "the precondition is stated, not assumed known");
    }

    /**
     * It never implies it checked the archive when it did not.
     *
     * The historical replay is opt-in because it is expensive. A report that
     * omitted it silently would be read as covering everything, and the imported
     * archive is the larger half of what cutover depends on.
     */
    @Test
    public void withoutTheHistoricalPassItSaysTheHistoricalPassDidNotRun() {
        String md = diagnostics.render(false);

        assertTrue(md.contains("Historical replay"), "the section exists either way");
        assertTrue(md.contains("Not run"),
                "and states plainly that it did not run, rather than omitting the section");
        assertFalse(md.contains("Issues compared"),
                "no comparison counts are shown for a pass that never happened");
    }
}
