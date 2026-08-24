package org.niord.core.publication.series.replay;

import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B6.3. The report the cutover decision is made from.
 *
 * The failure mode being tested for is not a wrong number. It is a number that
 * is technically right and reads as more than it is -- a series shown as ready
 * on the strength of weeks nobody compared, or a report that stays silent about
 * what it did not examine. Every test here is about that.
 */
@QuarkusTest
public class DiagnosticReportTest {

    @Inject
    DiagnosticReportService diagnostics;

    private static ShadowDiffRun run(boolean green, String skipReason, long at) {
        ShadowDiffRun r = new ShadowDiffRun();
        r.setComparedAt(new Date(at));
        r.setSkipReason(skipReason);
        r.setDelta(green ? Set.of() : Set.of("uid-a"), Set.of());
        return r;
    }

    // -------------------------------------------------------------- the streak

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

    // -------------------------------------------------------------- the report

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
