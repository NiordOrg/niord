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
     * The half of the firing-areas fixture B0.3 defers to here.
     *
     * B0.3 asserts the two issues share 31 of 32 members. At that point there is
     * no gap-detection code whose absence could be checked. Here there is, so the
     * absence is asserted rather than merely omitted: for an in-force series NO
     * tiling, NO gap check and NO overlap refusal runs.
     */
    @Test
    public void anInForceSeriesGetsNoGapDetectionAtAll() {
        GapDetection.Gate gate = GapDetection.gate(TimeRelation.IN_FORCE_AT_CUTOFF, "YEARLY", true, false);

        assertFalse(gate.enabled(), "gap detection ran for a series whose issues overlap by design");
        assertTrue(gate.reason().contains("overlap"), "the reason should say why: " + gate.reason());

        // The 2026 and 2027 firing-areas cut-offs, a year apart. Under tiling
        // logic the year between them looks like a missing period; it is not.
        List<Date> cutoffs = List.of(at(1_767_225_600_000L), at(1_798_761_600_000L));
        List<GapDetection.Gap> gaps = GapDetection.gaps(gate, cutoffs, YEAR);

        assertTrue(gaps.isEmpty(),
                "a MISSING pseudo-row was produced for an in-force series; those issues share 31 of their "
                        + "32 members, so nothing is missing between them");
    }

    /** And the same input DOES produce gaps under the tiling relation, so the test proves something. */
    @Test
    public void theSameInputProducesGapsWhenTheSeriesActuallyTiles() {
        GapDetection.Gate tiling = GapDetection.gate(TimeRelation.PUBLISHED_IN_INTERVAL, "WEEKLY", true, false);
        assertTrue(tiling.enabled());

        long start = 1_767_225_600_000L;
        List<Date> withAMissingWeek = List.of(at(start), at(start + 3 * WEEK));
        List<GapDetection.Gap> gaps = GapDetection.gaps(tiling, withAMissingWeek, WEEK);

        assertEquals(2, gaps.size(), "two weeks are missing between those cut-offs");
        assertEquals(at(start + WEEK), gaps.get(0).from());
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

    /** A cadenced IN_FORCE series still gets the overlap explanation. */
    @Test
    public void acadencedInForceSeriesStillReportsTheOverlap() {
        GapDetection.Gate gate = GapDetection.gate(TimeRelation.IN_FORCE_AT_CUTOFF, "WEEKLY", true, false);

        assertFalse(gate.enabled());
        assertEquals(GapDetection.Reason.RELATION_NOT_TILING, gate.code());
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
        GapDetection.Gate closed = GapDetection.gate(TimeRelation.IN_FORCE_AT_CUTOFF, "YEARLY", true, false);
        List<Date> spread = new ArrayList<>(List.of(at(0), at(50 * YEAR)));
        assertTrue(GapDetection.gaps(closed, spread, YEAR).isEmpty(),
                "fifty years of spread produced gaps through a closed gate");
    }
}
