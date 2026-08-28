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
    private static final Map<String, GapSynthesis.Patterns> PATTERNS =
            Map.of("en", new GapSynthesis.Patterns("NtM Week ${week} - ${year}",
                    "ntm-${year}-${week}.pdf"));

    /** A Wednesday, 12:00 UTC, in 2026. */
    private static Date wed(int isoWeek) {
        ZonedDateTime t = ZonedDateTime.of(2026, 1, 7, 12, 0, 0, 0, ZoneId.of("UTC"));
        return Date.from(t.plusWeeks(isoWeek - 2L).toInstant());
    }

    private static GapSynthesis.Issue issue(String id, Date cutoff) {
        return new GapSynthesis.Issue(id, cutoff, IntervalBoundSource.STAMPED);
    }

    /** A released issue whose content period opened at a known instant. */
    private static GapSynthesis.Issue chained(String id, Date openedAt, Date cutoff) {
        return new GapSynthesis.Issue(id, cutoff, IntervalBoundSource.STAMPED, openedAt, false);
    }

    /** An issue still being worked on, with the nominal close it is working toward. */
    private static GapSynthesis.Issue open(String id, Date openedAt, Date nominalClose) {
        return new GapSynthesis.Issue(id, nominalClose, IntervalBoundSource.NOMINAL, openedAt, true);
    }

    // ------------------------------------------------------------------ coverage

    /**
     * A double-week issue is not a gap. It opened where the previous issue closed
     * and carried both weeks; that its window is two periods long is the whole
     * of what happened. Counting release slots reported one week missing here,
     * with a retro-create for content already in the archive.
     */
    @Test
    public void aDoubleWeekIssueChainedFromThePreviousCloseLeavesNothingMissing() {
        List<GapSynthesis.Row> rows = GapSynthesis.synthesize(tiling(), "weekly-ntm",
                List.of(chained("before", wed(9), wed(10)), chained("double", wed(10), wed(12))),
                WEEK, CPH, PATTERNS, null, wed(12));

        assertEquals(0, rows.stream().filter(r -> r.kind() == GapSynthesis.RowKind.MISSING).count(),
                "the double week covered both periods; nothing is missing");
    }

    /** A next issue that opens LATER than the previous close leaves the stretch between uncovered. */
    @Test
    public void aStretchNoIntervalCoversIsMissingOnePeriodAtATime() {
        List<GapSynthesis.Row> rows = GapSynthesis.synthesize(tiling(), "weekly-ntm",
                List.of(chained("before", wed(9), wed(10)), chained("after", wed(12), wed(13))),
                WEEK, CPH, PATTERNS, null, wed(13));

        List<GapSynthesis.Row> missing = rows.stream()
                .filter(r -> r.kind() == GapSynthesis.RowKind.MISSING).toList();
        assertEquals(2, missing.size(), "weeks 11 and 12 are covered by no interval");
        assertEquals(wed(10), missing.get(0).intervalFrom(), "the first uncovered period opens at the previous close");
        assertEquals("before", missing.get(0).precedingPublicId());
        assertEquals("after", missing.get(0).followingPublicId());
    }

    /**
     * While the newest issue is OPEN nothing is synthesized after it. Its period
     * is late, not missing -- it is the row being worked on -- and rows behind it
     * would offer retro-creates for weeks that issue will carry when it publishes.
     */
    @Test
    public void anOpenNewestIssueIsLateNotMissing() {
        List<GapSynthesis.Row> rows = GapSynthesis.synthesize(tiling(), "weekly-ntm",
                List.of(chained("published", wed(39), wed(40)), open("working", wed(40), wed(41))),
                WEEK, CPH, PATTERNS, null, new Date(wed(43).getTime() + 3600_000L));

        assertEquals(0, rows.size(), "an open issue three weeks late is late, and is its own row");
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
                365L * 24 * 3600_000L, CPH, PATTERNS, null, wed(60));

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
                WEEK, CPH, PATTERNS, null, wed(6)).isEmpty());
    }

    // ------------------------------------------------- a series with no issues

    /**
     * A newly ACTIVATED series offers somewhere to start.
     *
     * Everything else here anchors on an issue that already exists, so a series
     * with none produced no rows at all -- not the weeks it had missed, and not
     * even the period currently open. The retro-create affordance lives on a
     * MISSING row, so the screen showed "no issues yet" and offered no way to make
     * one: an admin could create a series, activate it, and then be stuck.
     *
     * Found by rehearsing a full week through the UI, which is the first time
     * anybody had activated a series and looked at it.
     */
    @Test
    public void aSeriesWithNoIssuesSynthesizesFromItsDeclaredStart() {
        // Declared to start at week 10; it is now week 13, so weeks 10, 11 and 12
        // have closed and week 13 is the one being worked toward.
        List<GapSynthesis.Row> rows = GapSynthesis.synthesize(tiling(), "weekly-ntm",
                List.of(), WEEK, CPH, PATTERNS, wed(10), wed(13));

        assertFalse(rows.isEmpty(),
                "an activated series with no issues produced no rows, so there is nothing to "
                        + "retro-create from and no way to make its first issue");
        assertEquals(4, rows.size(), "expected three closed periods and one open, got " + rows);
        assertEquals(3, rows.stream().filter(r -> r.kind() == GapSynthesis.RowKind.MISSING).count());
        assertEquals(1, rows.stream().filter(r -> r.kind() == GapSynthesis.RowKind.UPCOMING).count(),
                "there is only ever one period being worked toward");
    }

    /** The first period opens exactly where the series says it does, not a week later. */
    @Test
    public void theFirstSynthesizedPeriodStartsAtTheDeclaredStart() {
        List<GapSynthesis.Row> rows = GapSynthesis.synthesize(tiling(), "weekly-ntm",
                List.of(), WEEK, CPH, PATTERNS, wed(10), wed(13));

        assertEquals(wed(10), rows.get(0).intervalFrom(),
                "the first interval must open where firstIssueStartsAt says; opening it a "
                        + "period later silently drops the series' first week");
    }

    /**
     * No declared start, no rows -- rather than a guess.
     *
     * S-4 requires firstIssueStartsAt of every interval-based series, so a null one
     * means the series is not in a state to be producing issues. Inventing an anchor
     * would offer retro-creates for periods nobody declared.
     */
    @Test
    public void aSeriesWithNoIssuesAndNoDeclaredStartSynthesizesNothing() {
        assertTrue(GapSynthesis.synthesize(tiling(), "weekly-ntm",
                List.of(), WEEK, CPH, PATTERNS, null, wed(13)).isEmpty());
    }

    // ---------------------------------------------------------------- the rows

    /** Two weeks missing between two issues become two MISSING rows, in order. */
    @Test
    public void aMissingWeekBecomesOneRowPerMissingPeriod() {
        List<GapSynthesis.Row> rows = GapSynthesis.synthesize(tiling(), "weekly-ntm",
                List.of(issue("before", wed(10)), issue("after", wed(13))),
                WEEK, CPH, PATTERNS, null, wed(13));

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
                WEEK, CPH, PATTERNS, null, wed(22));

        GapSynthesis.Row gap = rows.stream()
                .filter(r -> r.kind() == GapSynthesis.RowKind.MISSING).findFirst().orElseThrow();

        int startWeek = isoWeekOf(gap.intervalFrom());
        int endWeek = isoWeekOf(gap.intervalTo());
        assertNotEquals(startWeek, endWeek, "fixture is pointless unless the two bounds fall in different weeks");

        int endYear = gap.intervalTo().toInstant().atZone(CPH).get(WeekFields.ISO.weekBasedYear());
        assertEquals("NtM Week " + endWeek + " - " + endYear,
                gap.suggestions().get("en").name());

        // The file name is expanded from the SAME numbers. A prefill whose title
        // says one week and whose file name says another is how a week 27 issue
        // gets filed as week 26.
        assertEquals("ntm-" + endYear + "-" + endWeek + ".pdf",
                gap.suggestions().get("en").fileName());
    }

    /** Exactly one UPCOMING: the period whose cut-off has not passed. */
    @Test
    public void thePeriodBeingWorkedTowardIsTheOnlyUpcomingRow() {
        List<GapSynthesis.Row> rows = GapSynthesis.synthesize(tiling(), "weekly-ntm",
                List.of(issue("newest", wed(30))),
                WEEK, CPH, PATTERNS, null, new Date(wed(31).getTime() - 3600_000L));

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
                WEEK, CPH, PATTERNS, null, new Date(wed(42).getTime() + 3600_000L));

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
                WEEK, CPH, PATTERNS, null, new Date(wed(51).getTime() - 1000L)).get(0);

        assertSame(IntervalBoundSource.STAMPED, upcoming.intervalFromSource(),
                "it chains from a real stamped cut-off");
        assertSame(IntervalBoundSource.NOMINAL, upcoming.intervalToSource(),
                "nothing has stamped the end of a period that has not closed");
    }
}
