package org.niord.core.publication.series.resolve;

import org.junit.jupiter.api.Test;
import org.niord.core.publication.series.IntervalBoundSource;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The synthesizer that produces MISSING and UPCOMING rows. Pure, no database.
 *
 * Two of these exist because the API contract demands them in writing rather than
 * because the code looked risky: the in-force refusal (S6.4) and the name being
 * derived from the interval END (S3.15).
 */
public class GapSynthesisTest {

    private static final long WEEK = 7L * 24 * 3600_000L;
    private static final ZoneId CPH = ZoneId.of("Europe/Copenhagen");
    private static final Map<String, String> PATTERNS =
            Map.of("en", "NtM Week ${week} - ${year}");

    /** A Wednesday, 12:00 UTC, in 2026. */
    private static Date wed(int isoWeek) {
        ZonedDateTime t = ZonedDateTime.of(2026, 1, 7, 12, 0, 0, 0, ZoneId.of("UTC"));
        return Date.from(t.plusWeeks(isoWeek - 2L).toInstant());
    }

    private static GapSynthesis.Issue issue(String id, Date cutoff) {
        return new GapSynthesis.Issue(id, cutoff, IntervalBoundSource.STAMPED);
    }

    private static GapDetection.Gate tiling() {
        return GapDetection.gate(TimeRelation.PUBLISHED_IN_INTERVAL, "WEEKLY", true, false);
    }

    private static int isoWeekOf(Date d) {
        return ZonedDateTime.ofInstant(d.toInstant(), CPH).get(WeekFields.ISO.weekOfWeekBasedYear());
    }

    // ------------------------------------------------------------------ the gate

    /**
     * An in-force series produces NO rows. The contract requires this in writing.
     *
     * Its issues overlap rather than tile -- the 2026 and 2027 firing-areas issues
     * share 31 of their 32 members -- so a "missing year" between them is a
     * category error, and a MISSING row would offer a retro-create for something
     * that was never absent.
     */
    @Test
    public void anInForceSeriesProducesNoRowsAtAll() {
        GapDetection.Gate gate = GapDetection.gate(TimeRelation.IN_FORCE_AT_CUTOFF, "YEARLY", true, false);

        List<GapSynthesis.Row> rows = GapSynthesis.synthesize(gate, "dk-firing-areas",
                List.of(issue("a", wed(2)), issue("b", wed(54))),
                365L * 24 * 3600_000L, CPH, PATTERNS, wed(60));

        assertTrue(rows.isEmpty(), "a pseudo-row was synthesized for a series whose issues overlap by design");
    }

    /**
     * A closed gate returns empty, and empty is NOT an answer about gaps.
     *
     * Every imported series is DRAFT, so on today's estate this is the branch that
     * runs for all twenty of them. The reason string is what a caller must report;
     * a caller that shows the row count alone would render "0 gaps" for a series
     * nobody examined, which reads exactly like a clean one.
     */
    @Test
    public void aClosedGateReturnsEmptyAndSaysWhyRatherThanReportingNoGaps() {
        GapDetection.Gate draft = GapDetection.gate(TimeRelation.PUBLISHED_IN_INTERVAL, "WEEKLY", false, false);

        assertFalse(draft.enabled());
        assertFalse(draft.reason().isBlank(), "the gate must carry the reason a caller reports instead of a count");
        assertTrue(GapSynthesis.synthesize(draft, "weekly-ntm",
                List.of(issue("a", wed(2)), issue("b", wed(5))),
                WEEK, CPH, PATTERNS, wed(6)).isEmpty());
    }

    // ---------------------------------------------------------------- the rows

    /** Two weeks missing between two issues become two MISSING rows, in order. */
    @Test
    public void aMissingWeekBecomesOneRowPerMissingPeriod() {
        List<GapSynthesis.Row> rows = GapSynthesis.synthesize(tiling(), "weekly-ntm",
                List.of(issue("before", wed(10)), issue("after", wed(13))),
                WEEK, CPH, PATTERNS, wed(13));

        List<GapSynthesis.Row> missing = rows.stream()
                .filter(r -> r.kind() == GapSynthesis.RowKind.MISSING).toList();

        assertEquals(2, missing.size(), "two weeks tile between those cut-offs");
        assertTrue(missing.get(0).sortKey() < missing.get(1).sortKey(), "rows carry an ordering key");
        assertEquals("before", missing.get(0).precedingPublicId());
        assertEquals("after", missing.get(0).followingPublicId());
    }

    /**
     * The suggested name comes from the interval END.
     *
     * A Wednesday-to-Wednesday interval spans two ISO weeks. Deriving from the
     * start names the issue after the week it opened in; production names it after
     * the week it closed in. The two differ for every weekly issue there has ever
     * been, so getting this backwards is not a rare edge.
     */
    @Test
    public void theSuggestedNameComesFromTheIntervalEndNotItsStart() {
        List<GapSynthesis.Row> rows = GapSynthesis.synthesize(tiling(), "weekly-ntm",
                List.of(issue("before", wed(20)), issue("after", wed(22))),
                WEEK, CPH, PATTERNS, wed(22));

        GapSynthesis.Row gap = rows.stream()
                .filter(r -> r.kind() == GapSynthesis.RowKind.MISSING).findFirst().orElseThrow();

        int startWeek = isoWeekOf(gap.intervalFrom());
        int endWeek = isoWeekOf(gap.intervalTo());
        assertNotEquals(startWeek, endWeek, "fixture is pointless unless the two bounds fall in different weeks");

        assertEquals("NtM Week " + endWeek + " - " + gap.intervalTo().toInstant()
                        .atZone(CPH).get(WeekFields.ISO.weekBasedYear()),
                gap.suggestedNames().get("en"));
    }

    /** Exactly one UPCOMING: the period whose cut-off has not passed. */
    @Test
    public void thePeriodBeingWorkedTowardIsTheOnlyUpcomingRow() {
        List<GapSynthesis.Row> rows = GapSynthesis.synthesize(tiling(), "weekly-ntm",
                List.of(issue("newest", wed(30))),
                WEEK, CPH, PATTERNS, new Date(wed(31).getTime() - 3600_000L));

        List<GapSynthesis.Row> upcoming = rows.stream()
                .filter(r -> r.kind() == GapSynthesis.RowKind.UPCOMING).toList();

        assertEquals(1, upcoming.size(), "there is only ever one period being worked toward");
        assertEquals("newest", upcoming.get(0).precedingPublicId());
        assertNull(upcoming.get(0).followingPublicId(), "nothing follows the head of the list");
    }

    /**
     * A series that simply stopped shows its overdue periods.
     *
     * GapDetection only looks BETWEEN cut-offs, so without the forward pass a
     * series that stopped producing shows no gap at all -- which is the one case
     * somebody is looking for.
     */
    @Test
    public void periodsAfterTheNewestIssueAreMissingUntilTheOneStillOpen() {
        List<GapSynthesis.Row> rows = GapSynthesis.synthesize(tiling(), "weekly-ntm",
                List.of(issue("newest", wed(40))),
                WEEK, CPH, PATTERNS, new Date(wed(42).getTime() + 3600_000L));

        assertEquals(2, rows.stream().filter(r -> r.kind() == GapSynthesis.RowKind.MISSING).count(),
                "weeks 41 and 42 came and went with nothing published");
        assertEquals(1, rows.stream().filter(r -> r.kind() == GapSynthesis.RowKind.UPCOMING).count());
        assertSame(GapSynthesis.RowKind.UPCOMING, rows.get(rows.size() - 1).kind(),
                "the open period sorts last");
    }

    /** A bound the cadence inferred says so, rather than passing as recorded. */
    @Test
    public void aBoundWithNoStampedNeighbourIsMarkedNominal() {
        GapSynthesis.Row upcoming = GapSynthesis.synthesize(tiling(), "weekly-ntm",
                List.of(issue("newest", wed(50))),
                WEEK, CPH, PATTERNS, new Date(wed(51).getTime() - 1000L)).get(0);

        assertSame(IntervalBoundSource.STAMPED, upcoming.intervalFromSource(),
                "it chains from a real stamped cut-off");
        assertSame(IntervalBoundSource.NOMINAL, upcoming.intervalToSource(),
                "nothing has stamped the end of a period that has not closed");
    }
}
