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

import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The green-release streak, which is the cutover precondition itself.
 *
 * Split out of DiagnosticReportTest and kept free of Quarkus deliberately. The
 * streak rule decides whether a series may be cut over at all, so it is the last
 * thing that should stop running when a build agent happens to have no database.
 * consecutiveGreen is static and reads nothing but the list it is handed, so
 * there is no reason for it to sit behind a container boot.
 */
public class DiagnosticStreakTest {

    private static ShadowDiffRun run(boolean green, String skipReason, long at) {
        ShadowDiffRun r = new ShadowDiffRun();
        r.setComparedAt(new Date(at));
        r.setSkipReason(skipReason);
        r.setDelta(green ? Set.of() : Set.of("uid-a"), Set.of());
        return r;
    }

    /** Two green in a row is the precondition. */
    @Test
    public void twoGreenReleasesInARowMeetThePrecondition() {
        int streak = DiagnosticReportService.consecutiveGreen(List.of(
                run(true, null, 3000),
                run(true, null, 2000),
                run(false, null, 1000)));

        assertEquals(2, streak);
        assertTrue(streak >= DiagnosticReportService.REQUIRED_GREEN_RELEASES);
    }

    /**
     * A SKIPPED release breaks the streak. This is the load-bearing rule.
     *
     * A skipped run is stored green, because nothing diverged -- nothing was
     * compared. If the streak counted the flag rather than the reason, a series
     * whose every release was hand-uploaded would reach the cutover precondition
     * without one comparison ever having happened, and the report would say so
     * in the same words it uses for a series that genuinely agreed twice.
     */
    @Test
    public void aSkippedReleaseBreaksTheStreakEvenThoughItIsStoredGreen() {
        ShadowDiffRun skipped = run(true, "FILE_REPLACED_BY_HAND", 2000);
        assertTrue(skipped.isGreen(), "a skipped run really is stored green");

        int streak = DiagnosticReportService.consecutiveGreen(List.of(
                run(true, null, 3000),
                skipped,
                run(true, null, 1000)));

        assertEquals(1, streak, "the skip stops the count at the newest release");
    }

    /** A divergence breaks it too, obviously -- asserted so the two cannot drift apart. */
    @Test
    public void aDivergingReleaseBreaksTheStreak() {
        assertEquals(0, DiagnosticReportService.consecutiveGreen(List.of(
                run(false, null, 3000),
                run(true, null, 2000))));
    }

    /** No runs is no evidence, not a clean sheet. */
    @Test
    public void noRunsIsAStreakOfZero() {
        assertEquals(0, DiagnosticReportService.consecutiveGreen(List.of()));
    }
}
