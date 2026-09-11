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

package org.niord.core.publication.series.resolve;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Gap detection, its gate, and dormancy. Pure, no database. */
public class GapDetectionTest {

    private static final long WEEK = 7L * 24 * 3600_000L;
    private static final long YEAR = 365L * 24 * 3600_000L;

    private static Date at(long millis) {
        return new Date(millis);
    }

    // ------------------------------------------------------------------ the gate

    /**
     * The half of the firing-areas fixture the corpus check defers to here.
     *
     * That check asserts the two issues share 31 of 32 members. An in-force series
     * is examined like any other cadenced series -- a yearly publication that
     * stops is late in exactly the way a weekly one is -- but its coverage is not
     * read off intervals it does not have: the gate says so, and the release-slot
     * arithmetic that would call the year between two editions "missing" is not
     * what the synthesizer runs for it.
     */
    @Test
    public void anInForceSeriesIsExaminedButItsCoverageIsReadOffTheCutoffs() {
        GapDetection.Gate gate = GapDetection.gate(TimeRelation.IN_FORCE_AT_CUTOFF, "YEARLY", true, false);

        assertTrue(gate.enabled(), "an active yearly series is expected to keep producing, whatever it carries");
        assertEquals(GapDetection.Reason.CADENCED_SERIES, gate.code());
        assertFalse(gate.tiling(), "in-force issues carry no lower bound and do not tile");

        // The 2026 and 2027 firing-areas cut-offs, a year apart. Nothing was
        // withdrawn between them, so nothing is missing between them.
        Date y2026 = at(1_767_225_600_000L);
        Date y2027 = at(1_798_761_600_000L);
        assertTrue(GapDetection.withdrawn(gate, y2026, y2027, List.of(), YEAR).isEmpty(),
                "a MISSING period was produced between two consecutive in-force editions; they share 31 of "
                        + "their 32 members, so nothing is missing between them");
    }

    /**
     * A double week on an in-force weekly is not a gap, and a withdrawn week is.
     *
     * Two consecutive releases cover the stretch between them whatever its
     * length. What the stretch can hold is a release that was later withdrawn --
     * and that period is uncovered again, exactly as it is on a tiling series.
     */
    @Test
    public void betweenInForceReleasesOnlyAWithdrawnSlotIsMissing() {
        GapDetection.Gate gate = GapDetection.gate(TimeRelation.IN_FORCE_AT_CUTOFF, "WEEKLY", true, false);
        long start = 1_767_225_600_000L;
        Date week34 = at(start);
        Date week36 = at(start + 2 * WEEK);

        assertTrue(GapDetection.withdrawn(gate, week34, week36, List.of(), WEEK).isEmpty(),
                "a fortnight between two in-force releases is a double week, not a missing one");

        // Week 35 was released and withdrawn: its slot is missing again.
        List<GapDetection.Gap> gaps = GapDetection.withdrawn(gate, week34, week36,
                List.of(at(start + WEEK)), WEEK);
        assertEquals(1, gaps.size());
        assertEquals(week34, gaps.get(0).from());
        assertEquals(at(start + WEEK), gaps.get(0).to());

        // A withdrawal outside the stretch says nothing about it.
        assertTrue(GapDetection.withdrawn(gate, week34, week36, List.of(at(start + 5 * WEEK)), WEEK).isEmpty());
    }

    /** And the same input DOES produce gaps under the tiling relation, so the test proves something. */
    @Test
    public void theSameInputProducesGapsWhenTheSeriesActuallyTiles() {
        GapDetection.Gate tiling = GapDetection.gate(TimeRelation.PUBLISHED_IN_INTERVAL, "WEEKLY", true, false);
        assertTrue(tiling.enabled());
        assertTrue(tiling.tiling());

        long start = 1_767_225_600_000L;
        List<Date> withAMissingWeek = List.of(at(start), at(start + 3 * WEEK));
        List<GapDetection.Gap> gaps = GapDetection.gaps(tiling, withAMissingWeek, WEEK);

        assertEquals(2, gaps.size(), "two weeks are missing between those cut-offs");
        // A cut-off closes its period, so the first missing period OPENS at the
        // first cut-off and the last one CLOSES a period before the second: the
        // slots are the two weeks between the issues, not the second issue's own.
        assertEquals(at(start), gaps.get(0).from());
        assertEquals(at(start + WEEK), gaps.get(0).to());
        assertEquals(at(start + 2 * WEEK), gaps.get(1).to());
    }

    /**
     * The fallback and the coverage arithmetic tile from the same anchor.
     *
     * The two answer the same question for two shapes of issue -- one that knows
     * where its period opened and one that does not -- and a next issue that
     * opened exactly at the previous cut-off must get the same slots from both.
     */
    @Test
    public void theFallbackAndTheCoverageArithmeticAgreeOnWhichPeriodsAreMissing() {
        GapDetection.Gate tiling = GapDetection.gate(TimeRelation.PUBLISHED_IN_INTERVAL, "WEEKLY", true, false);
        long start = 1_767_225_600_000L;

        List<GapDetection.Gap> fromCutoffs = GapDetection.gaps(tiling,
                List.of(at(start), at(start + 3 * WEEK)), WEEK);
        List<GapDetection.Gap> fromCoverage = GapDetection.uncovered(tiling,
                at(start), at(start + 2 * WEEK), WEEK);

        assertEquals(fromCoverage.size(), fromCutoffs.size());
        for (int i = 0; i < fromCoverage.size(); i++) {
            assertEquals(fromCoverage.get(i).from(), fromCutoffs.get(i).from(), "slot " + i + " opens elsewhere");
            assertEquals(fromCoverage.get(i).to(), fromCutoffs.get(i).to(), "slot " + i + " closes elsewhere");
        }
    }

    /**
     * A cadence-less publication has no period to be missing one of.
     *
     * NULL RELATION, because that is the shape S-1 produces and the reason this
     * assertion used to pass while the running system got it wrong: every real
     * cadence-less series has no time relation, so the gate reached the relation
     * branch first and explained a one-off as an overlapping IN_FORCE series.
     * Passing PUBLISHED_IN_INTERVAL built the one series that cannot exist.
     *
     * Asserted on the CODE rather than the prose. The reason text covers every
     * cadence-less publication, including the unscheduled series that are not
     * one-offs at all, so it no longer names one and should not have to.
     */
    @Test
    public void acadencelessSeriesHasNoPeriodToBeMissing() {
        GapDetection.Gate gate = GapDetection.gate(null, "NONE", true, false);

        assertFalse(gate.enabled());
        assertEquals(GapDetection.Reason.NO_CADENCE, gate.code(),
                "a cadence-less series was explained as something else: " + gate.reason());
    }

    /**
     * A cadenced IN_FORCE series is expected to keep producing like any other.
     *
     * The weekly P&T list goes out every week whatever it carries. Gating it off
     * for its content rule left it with no upcoming period and no warning when
     * its newest cut-off had passed with nothing open -- the one case somebody
     * opening the series is looking for.
     */
    @Test
    public void acadencedInForceSeriesIsExpectedToKeepProducing() {
        GapDetection.Gate gate = GapDetection.gate(TimeRelation.IN_FORCE_AT_CUTOFF, "WEEKLY", true, false);

        assertTrue(gate.enabled());
        assertEquals(GapDetection.Reason.CADENCED_SERIES, gate.code());
        assertFalse(gate.tiling());
    }

    /** A series with no relation at all -- not query-backed -- reads its coverage off the cut-offs too. */
    @Test
    public void aSeriesWithNoRelationIsExaminedAndDoesNotTile() {
        GapDetection.Gate gate = GapDetection.gate(null, "MONTHLY", true, false);

        assertTrue(gate.enabled());
        assertFalse(gate.tiling());
    }

    @Test
    public void onlyAnActiveSeriesIsExpectedToKeepProducing() {
        assertFalse(GapDetection.gate(TimeRelation.PUBLISHED_IN_INTERVAL, "WEEKLY", false, false).enabled());
    }

    // ------------------------------------------------------------------ dormancy

    /**
     * A dormant series raises no gap warning.
     *
     * Otherwise a series last published in 2018 -- "Akkumuleret EfS" is the real
     * one -- produces a warning for every week since, several hundred rows, which
     * buries the single fact that actually matters.
     */
    @Test
    public void aDormantSeriesRaisesNoGapWarning() {
        Date lastIssue = at(1_514_764_800_000L);   // 1 January 2018
        Date now = at(1_767_225_600_000L);         // 1 January 2026

        assertTrue(GapDetection.isDormant(lastIssue, now, WEEK), "eight years of silence is dormant");

        GapDetection.Gate gate = GapDetection.gate(TimeRelation.PUBLISHED_IN_INTERVAL, "WEEKLY", true, true);
        assertFalse(gate.enabled());

        List<Date> cutoffs = List.of(lastIssue, now);
        assertTrue(GapDetection.gaps(gate, cutoffs, WEEK).isEmpty(),
                "a dormant series produced gap rows; that is several hundred warnings for one fact");
    }

    @Test
    public void dormancyIsThreeMissedPeriodsNotOne() {
        Date last = at(1_767_225_600_000L);
        assertEquals(3, GapDetection.DORMANCY_PERIODS);

        // Two missed weeks: a holiday or an operational slip. Not dormant.
        assertFalse(GapDetection.isDormant(last, at(last.getTime() + 2 * WEEK), WEEK),
                "two missed weeks should not raise dormancy; a holiday gap would");

        // Four: nobody is coming back to it on their own.
        assertTrue(GapDetection.isDormant(last, at(last.getTime() + 4 * WEEK), WEEK));
    }

    /** Derived, never stored: the same series is dormant or not depending only on when you ask. */
    @Test
    public void dormancyIsAnObservationNotAStoredFlag() {
        Date last = at(1_767_225_600_000L);

        assertFalse(GapDetection.isDormant(last, at(last.getTime() + WEEK), WEEK));
        assertTrue(GapDetection.isDormant(last, at(last.getTime() + 10 * WEEK), WEEK));

        // Same series, same last issue, different answer -- which is exactly why
        // storing it would let it go stale while looking authoritative.
    }

    // ------------------------------------------------------------------ upcoming

    @Test
    public void theUpcomingRowIsOnePeriodAfterTheLastIssue() {
        Date last = at(1_767_225_600_000L);
        assertEquals(at(last.getTime() + WEEK), GapDetection.nextCutoff(last, WEEK));
        assertNotNull(GapDetection.nextCutoff(last, WEEK));
    }

    // ------------------------------------------------------------------ periods

    @Test
    public void monthsAndYearsAreMeasuredNotApproximated() {
        java.time.ZoneId dk = java.time.ZoneId.of("Europe/Copenhagen");

        // February is shorter than January, and an average would get both wrong.
        long january = GapDetection.periodMillisOf("MONTHLY", dk, at(1_767_225_600_000L));
        long february = GapDetection.periodMillisOf("MONTHLY", dk, at(1_769_904_000_000L));
        assertTrue(january > february, "a fixed 30-day month would make these equal");

        assertEquals(7L * 24 * 3600_000L, GapDetection.periodMillisOf("WEEKLY", dk, null));
        assertEquals(24L * 3600_000L, GapDetection.periodMillisOf("DAILY", dk, null));
        assertEquals(0L, GapDetection.periodMillisOf("NONE", dk, null));
    }

    /** A caller that forgets the gate still cannot produce a pseudo-row. */
    @Test
    public void aClosedGateReturnsNoGapsEvenIfTheCallerIgnoresIt() {
        GapDetection.Gate closed = GapDetection.gate(TimeRelation.PUBLISHED_IN_INTERVAL, "YEARLY", false, false);
        assertFalse(closed.enabled());
        List<Date> spread = new ArrayList<>(List.of(at(0), at(50 * YEAR)));
        assertTrue(GapDetection.gaps(closed, spread, YEAR).isEmpty(),
                "fifty years of spread produced gaps through a closed gate");
        assertTrue(GapDetection.withdrawn(closed, at(0), at(50 * YEAR), List.of(at(YEAR)), YEAR).isEmpty(),
                "a withdrawn slot was produced through a closed gate");
    }
}
