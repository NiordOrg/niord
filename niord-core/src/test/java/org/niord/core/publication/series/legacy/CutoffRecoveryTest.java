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

package org.niord.core.publication.series.legacy;

import org.junit.jupiter.api.Test;
import org.niord.core.publication.Publication;
import org.niord.core.publication.series.IssueStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cut-off recovery cascade.
 *
 * ON THE DECLARED FIGURES. The plan states the cascade is "exact on 452 of 496
 * chained pairs". Neither number reproduces against the captured estate under
 * any reading tried: chaining across every template gives 1,025 pairs of which
 * 942 agree within the window; chaining within each of the two large series
 * gives 492 pairs of which 468 agree. 496 and 452 are close to the per-series
 * reading but not equal to it, so some narrower definition is meant and it is
 * not recoverable from the plan text.
 *
 * So this suite asserts the cascade's STRUCTURE -- which stage wins, in what
 * order, and what each records -- and pins the distribution AS MEASURED rather
 * than asserting figures it cannot reproduce. The cascade's numeric acceptance
 * ("matches the declared figures +/-1, asserted as four counts") is deliberately
 * NOT claimed here; it needs the definition of a chained pair confirmed first.
 *
 * Pinning the measured numbers is not the same as accepting them: it means any
 * later change to the cascade or the capture shows up as a failure rather than
 * as a drift nobody notices.
 */
public class CutoffRecoveryTest {

    private static final long MINUTE = 60 * 1000L;

    private static Publication row(Long updated) {
        Publication p = new Publication();
        p.setPublicationId("p");
        p.setUpdated(updated == null ? null : new Date(updated));
        return p;
    }

    // ------------------------------------------------------- stage precedence

    /** Stage 1 wins when the next tag agrees with it. */
    @Test
    public void agreementInsideTheWindowKeepsTheUpdatedStamp() {
        CutoffRecovery.Recovered r = CutoffRecovery.recover(
                row(1_000_000L), new Date(1_000_000L + MINUTE), null, true, CutoffRecovery.Bounds.NONE);
        assertEquals(CutoffRecovery.FROM_UPDATED, r.source());
        assertEquals(1_000_000L, r.cutoff().getTime());
    }

    /**
     * Stage 2 overrides stage 1 when they disagree by more than the window.
     *
     * This ordering is the whole point. `updated` is non-null on every row in the
     * estate, so a first-non-null cascade would never reach stage 2 at all --
     * which is exactly the "key on updated alone" failure, recovering ~493 of 499
     * weekly cut-offs and only 13 of 35 non-weekly ones.
     */
    @Test
    public void disagreementBeyondTheWindowPrefersTheNextTag() {
        long updated = 1_000_000L;
        long tag = updated + 60 * MINUTE;

        CutoffRecovery.Recovered r = CutoffRecovery.recover(row(updated), new Date(tag), null, true, CutoffRecovery.Bounds.NONE);
        assertEquals(CutoffRecovery.FROM_NEXT_TAG, r.source());
        assertEquals(tag, r.cutoff().getTime());
    }

    /** The boundary is inclusive: exactly the window still agrees. */
    @Test
    public void theWindowBoundaryIsInclusive() {
        long updated = 1_000_000L;
        assertEquals(CutoffRecovery.FROM_UPDATED, CutoffRecovery.recover(
                row(updated), new Date(updated + CutoffRecovery.AGREEMENT_WINDOW_MS), null, true, CutoffRecovery.Bounds.NONE).source());
        assertEquals(CutoffRecovery.FROM_NEXT_TAG, CutoffRecovery.recover(
                row(updated), new Date(updated + CutoffRecovery.AGREEMENT_WINDOW_MS + 1), null, true, CutoffRecovery.Bounds.NONE).source());
    }

    /** Stage 3 is reached only when neither of the first two has anything. */
    @Test
    public void theCoverDateIsReachedOnlyWhenNothingElseWitnessesTheRelease() {
        Date cover = new Date(2_000_000L);
        assertEquals(CutoffRecovery.FROM_COVER,
                CutoffRecovery.recover(row(null), null, cover, true, CutoffRecovery.Bounds.NONE).source());

        // ... and never when an earlier stage has an answer.
        assertEquals(CutoffRecovery.FROM_UPDATED,
                CutoffRecovery.recover(row(1_000_000L), null, cover, true, CutoffRecovery.Bounds.NONE).source());
    }

    /** Stage 4 flags rather than inventing a stamp. */
    @Test
    public void nothingLeavesTheCutoffNullAndFlagsManual() {
        CutoffRecovery.Recovered r = CutoffRecovery.recover(row(null), null, null, true, CutoffRecovery.Bounds.NONE);
        assertEquals(CutoffRecovery.MANUAL, r.source());
        assertNull(r.cutoff(), "an invented stamp is worse than an admitted gap");
    }

    // ----------------------------------------------------- what gets recorded

    /** No stage ever reports STAMPED, on any input. */
    @Test
    public void noStageLaundersItselfIntoAStampedRelease() {
        List<CutoffRecovery.Recovered> all = List.of(
                CutoffRecovery.recover(row(1L), new Date(1L), null, true, CutoffRecovery.Bounds.NONE),
                CutoffRecovery.recover(row(1L), new Date(9_000_000L), null, true, CutoffRecovery.Bounds.NONE),
                CutoffRecovery.recover(row(null), null, new Date(5L), true, CutoffRecovery.Bounds.NONE),
                CutoffRecovery.recover(row(null), null, null, true, CutoffRecovery.Bounds.NONE));

        for (CutoffRecovery.Recovered r : all) {
            assertFalse("STAMPED".equals(r.source()),
                    "collapsing a recovered stamp into STAMPED launders a last-write timestamp into a "
                            + "release stamp for ~700 issues");
            assertTrue(r.reconstructed(),
                    "legacy stored no release instant, so every stage here is a reconstruction");
        }
        assertFalse(CutoffRecovery.isStamped(CutoffRecovery.FROM_UPDATED));
    }

    /** Every stage records its own value; none share one. */
    @Test
    public void thefourStagesAreDistinguishable() {
        assertEquals(5, List.of(CutoffRecovery.FROM_UPDATED, CutoffRecovery.FROM_NEXT_TAG,
                CutoffRecovery.FROM_COVER, CutoffRecovery.MANUAL,
                CutoffRecovery.NOT_RELEASED).stream().distinct().count());
    }

    // ------------------------------------------------- the unreleased issue

    /**
     * An issue nobody released gets NO cut-off, rather than a plausible one.
     *
     * Every stage below reads a timestamp that exists on an unreleased row too,
     * and `updated` is the trap: on a never-published issue it is when the row
     * was CREATED, and it was created by the release of the issue BEFORE it. So
     * the cascade would hand back the predecessor's release instant, to the
     * millisecond, and call it this issue's cut-off.
     *
     * Measured on the test estate before this guard existed: all four OPEN issues
     * carried a stamp, and three of them carried one dated BEFORE their own
     * interval opened -- the 2027 firing-areas issue by a full year.
     */
    @Test
    public void anIssueThatWasNeverReleasedGetsNoCutoffAtAll() {
        // The shape that bites: a row whose `updated` is a real, recent, entirely
        // plausible timestamp -- it is just not this issue's release.
        CutoffRecovery.Recovered r = CutoffRecovery.recover(row(1_786_530_618_000L), null, null, false, CutoffRecovery.Bounds.NONE);

        assertNull(r.cutoff(),
                "an unreleased issue has no release instant; inventing one dates it a full period "
                        + "before its own interval and anchors gap arithmetic on it");
        assertEquals(CutoffRecovery.NOT_RELEASED, r.source());
        assertFalse(r.reconstructed(),
                "nothing was reconstructed, so the row must not be flagged as reconstructed -- that "
                        + "badge would sit on every open issue forever, warning about the one row "
                        + "that is behaving normally");
    }

    /** And the guard runs BEFORE every stage, not after the winning one. */
    @Test
    public void noStageCanOutrunTheUnreleasedGuard() {
        Date tag = new Date(9_000_000L);
        Date cover = new Date(5_000_000L);

        for (CutoffRecovery.Recovered r : List.of(
                CutoffRecovery.recover(row(1_000_000L), tag, cover, false, CutoffRecovery.Bounds.NONE),
                CutoffRecovery.recover(row(1_000_000L), null, null, false, CutoffRecovery.Bounds.NONE),
                CutoffRecovery.recover(row(null), tag, null, false, CutoffRecovery.Bounds.NONE),
                CutoffRecovery.recover(row(null), null, cover, false, CutoffRecovery.Bounds.NONE))) {
            assertEquals(CutoffRecovery.NOT_RELEASED, r.source());
            assertNull(r.cutoff());
        }
    }

    // ------------------------------------------ the cut-off has to be believable

    /**
     * A stamp outside the period it supposedly closes is not that period's cut-off.
     *
     * NtM Week 52 - 2025 covers 17-24 December and recovered a cut-off of 2 January,
     * 349 days early, from an `updated` stamp that looks like a placeholder row made
     * in January for the year-end edition and never touched again. Its 19 frozen
     * members were all published inside the interval, so nothing else about the row
     * was wrong -- only the date the cascade believed.
     */
    @Test
    public void aStampFromOutsideThePeriodIsNotBelieved() {
        Date opens = new Date(1_766_052_000_000L);            // 2025-12-18
        Date closes = new Date(opens.getTime() + 7 * 24 * 3600_000L);
        Date januaryPlaceholder = new Date(opens.getTime() - 349L * 24 * 3600_000L);

        CutoffRecovery.Recovered r = CutoffRecovery.recover(
                row(januaryPlaceholder.getTime()), null, null, true,
                new CutoffRecovery.Bounds(opens, closes));

        assertNull(r.cutoff(),
                "a period cannot close before it opens, however respectable the stamp's "
                        + "provenance -- and for a tiling issue the nominal close is the better "
                        + "answer anyway, which effectiveCutoff already coalesces onto");
        assertEquals(CutoffRecovery.MANUAL, r.source());
    }

    /** And a stamp a fortnight AFTER a weekly close is an edit, not the release. */
    @Test
    public void aStampMoreThanAPeriodLateIsAnEditRatherThanTheRelease() {
        Date opens = new Date(1_766_052_000_000L);
        Date closes = new Date(opens.getTime() + 7 * 24 * 3600_000L);

        assertNull(CutoffRecovery.recover(
                row(closes.getTime() + 14L * 24 * 3600_000L), null, null, true,
                new CutoffRecovery.Bounds(opens, closes)).cutoff());
    }

    /**
     * The ordinary case is untouched: releases run a little AFTER the bound they close.
     *
     * The upper bound is a full period for exactly this reason. A weekly issue whose
     * nominal close is 10:00 and whose release ran at 10:30 is the normal shape of
     * every row in the archive, and a tight ceiling would have rejected all of them.
     */
    @Test
    public void aReleaseRunningShortlyAfterTheNominalCloseIsStillBelieved() {
        Date opens = new Date(1_766_052_000_000L);
        Date closes = new Date(opens.getTime() + 7 * 24 * 3600_000L);
        Date halfAnHourLate = new Date(closes.getTime() + 30 * 60_000L);

        CutoffRecovery.Recovered r = CutoffRecovery.recover(
                row(halfAnHourLate.getTime()), null, null, true,
                new CutoffRecovery.Bounds(opens, closes));

        assertEquals(halfAnHourLate, r.cutoff());
        assertEquals(CutoffRecovery.FROM_UPDATED, r.source());
    }

    /**
     * A lower-ranked stage is TRIED when the winner is not believable.
     *
     * The next witness is still better evidence than no witness, so the cascade moves
     * on rather than giving up at the first rejection.
     */
    @Test
    public void alowerStageIsTriedWhenTheWinningStageIsOutOfBounds() {
        Date opens = new Date(1_766_052_000_000L);
        Date closes = new Date(opens.getTime() + 7 * 24 * 3600_000L);
        Date insideTheWindow = new Date(closes.getTime() - 3600_000L);

        // updated is wildly out; the next tag witnesses the close properly.
        CutoffRecovery.Recovered r = CutoffRecovery.recover(
                row(opens.getTime() - 300L * 24 * 3600_000L), insideTheWindow, null, true,
                new CutoffRecovery.Bounds(opens, closes));

        assertEquals(insideTheWindow, r.cutoff());
        assertEquals(CutoffRecovery.FROM_NEXT_TAG, r.source());
    }

    // ------------------------------------------------- the measured estate run

    /**
     * The cascade over the whole captured estate, with the stage recorded on
     * every row.
     *
     * Asserted as the four counts, but the counts are the MEASURED ones -- see
     * the class comment. The load-bearing assertion here is the one that does not
     * depend on the disputed definition: every row gets a stage, and no row is
     * left without one.
     */
    @Test
    public void everyRowGetsAStageAndTheDistributionIsPinned() {
        Map<String, Integer> stages = new LinkedHashMap<>();
        Map<String, List<Publication>> chains = new LinkedHashMap<>();

        for (Publication p : LegacyEstateFixture.publications()) {
            String key = p.getTemplate() == null ? "<none>" : p.getTemplate().getPublicationId();
            chains.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
        }

        int total = 0;
        for (List<Publication> chain : chains.values()) {
            chain.sort(Comparator.comparing(
                    p -> p.getPublishDateFrom() == null ? new Date(0) : p.getPublishDateFrom()));
            for (int i = 0; i < chain.size(); i++) {
                // The same question the importer asks: OPEN is the one status that
                // never had a release. Running the estate with this hard-coded true
                // would measure a cascade nothing calls.
                boolean released =
                        LegacyIssueTranslation.statusOf(chain.get(i).getStatus()) != IssueStatus.OPEN;
                // The SAME bounds the importer derives -- a tiling period runs from
                // the previous release to this one. Bounds.NONE here would measure a
                // cascade nothing calls.
                CutoffRecovery.Bounds bounds = new CutoffRecovery.Bounds(
                        i > 0 ? chain.get(i - 1).getPublishDateFrom() : null,
                        chain.get(i).getPublishDateFrom());
                CutoffRecovery.Recovered r = CutoffRecovery.recover(
                        chain.get(i), CutoffRecovery.nextTagCreated(chain, i), null, released, bounds);
                assertNotNull(r.source(), "every row must record which stage decided it");
                stages.merge(r.source(), 1, Integer::sum);
                total++;
            }
        }

        assertEquals(1077, total, "the cascade must reach every row of the estate");
        assertEquals(1077, stages.values().stream().mapToInt(Integer::intValue).sum());

        // Measured against the captured estate on 2026-08-23, re-measured 2026-08-24
        // when the unreleased guard moved four OPEN rows off stage 1. Pinned so a
        // change in the cascade or the capture surfaces here rather than drifting.
        assertEquals(975, stages.getOrDefault(CutoffRecovery.FROM_UPDATED, 0));
        assertEquals(53, stages.getOrDefault(CutoffRecovery.FROM_NEXT_TAG, 0));
        assertEquals(4, stages.getOrDefault(CutoffRecovery.NOT_RELEASED, 0),
                "the four OPEN issues -- one each on weekly-ntm and weekly-ntm-p-t, two on "
                        + "firing-practice-areas -- have never been released and must carry no stamp");
        assertEquals(0, stages.getOrDefault(CutoffRecovery.FROM_COVER, 0),
                "no cover dates are available in the capture; the 27 annuals the plan describes need "
                        + "the printed PDFs, which this fixture does not carry");
        // Stage 4 is reached by 45 rows once the cascade is BOUNDED. Every row has
        // an updated column, so before the bounds check none of them reached it --
        // the column was always believed. These are the rows whose stamp falls more
        // than a full period after the interval closed: an edit captured instead of
        // the release, which is the failure this cascade was built to avoid. They
        // fall back to the nominal close, which IS the release moment.
        assertEquals(45, stages.getOrDefault(CutoffRecovery.MANUAL, 0),
                "a stamp outside the period it supposedly closes is not that period's cut-off");
    }

    /** The end of a chain has no successor, and that is correct rather than missing. */
    @Test
    public void theNewestIssueOfAChainHasNoNextTag() {
        List<Publication> chain = List.of(row(1L), row(2L));
        assertNull(CutoffRecovery.nextTagCreated(chain, 1));
        assertNull(CutoffRecovery.nextTagCreated(chain, 5));
        assertNull(CutoffRecovery.nextTagCreated(chain, -1));
    }

    // ------------------------------------------- the annual in-force day rule

    private static final java.time.ZoneId CPH = java.time.ZoneId.of("Europe/Copenhagen");

    private static Date cph(int y, int m, int d, int h, int min) {
        return Date.from(java.time.ZonedDateTime.of(y, m, d, h, min, 0, 0, CPH).toInstant());
    }

    private static void assertEndOfDay(Date actual, int y, int m, int d, String why) {
        assertNotNull(actual, why);
        java.time.ZonedDateTime at = actual.toInstant().atZone(CPH);
        assertEquals(y, at.getYear(), why);
        assertEquals(m, at.getMonthValue(), why);
        assertEquals(d, at.getDayOfMonth(), why);
        assertEquals(23, at.getHour(), why);
        assertEquals(59, at.getMinute(), why);
        assertEquals(59, at.getSecond(), why);
        assertEquals(999, at.getNano() / 1_000_000, why);
    }

    /**
     * The window opened during the sitting: the day it opened is the day, and the
     * whole changeover falls inside it.
     *
     * On "EfS A - 2025" the window opened at 10:28:17, the previous year's notices
     * were cancelled at 11:18 and the new year's published at 11:28, and it was
     * released the same day. A cut-off at the opening instant resolves the edition
     * from before its own changeover.
     */
    @Test
    public void anInForceAnnualReleasedOnTheDayItOpensIsCutOffAtTheEndOfThatDay() {
        Date opened = Date.from(java.time.ZonedDateTime
                .of(2025, 2, 7, 10, 28, 17, 0, CPH).toInstant());
        Date released = cph(2025, 2, 7, 12, 3);

        CutoffRecovery.Recovered r = CutoffRecovery.forAnnualInForce(opened, released, CPH);

        assertEquals(CutoffRecovery.PUBLIC_WINDOW, r.source(),
                "the release is on the same day, so the window-open day is what stands");
        assertTrue(r.reconstructed());
        assertFalse(CutoffRecovery.witnessesTheRelease(r),
                "a window boundary is not a moment anybody pressed publish");
        assertEndOfDay(r.cutoff(), 2025, 2, 7, "the same day, never the next one");

        // The whole sequence that day is on the right side of it, which is the
        // point: the cancellations and the new publications both precede the
        // cut-off, so the edition resolves to the list it actually shipped.
        Date cancelledAt = cph(2025, 2, 7, 11, 18);
        Date publishedAt = cph(2025, 2, 7, 11, 29);
        assertTrue(cancelledAt.before(r.cutoff()));
        assertTrue(publishedAt.before(r.cutoff()));
        assertTrue(opened.before(cancelledAt),
                "and the window opened BEFORE the changeover, which is the whole defect");
    }

    /**
     * The window was named nominally and the sitting happened weeks later: the
     * RELEASE day is the day.
     *
     * "Skydeområder 2025" carries a window from 1 January, its outgoing set was
     * cancelled and its incoming set published on 7 February, and it was released
     * on 26 February. At the end of 1 January none of that had happened, so the
     * edition resolved to the previous year's list.
     */
    @Test
    public void anInForceAnnualReleasedAfterItsWindowOpensIsCutOffAtTheEndOfTheRELEASEDay() {
        Date opened = cph(2025, 1, 1, 12, 10);
        Date released = cph(2025, 2, 26, 12, 12);

        CutoffRecovery.Recovered r = CutoffRecovery.forAnnualInForce(opened, released, CPH);

        assertEndOfDay(r.cutoff(), 2025, 2, 26,
                "the edition is what was in force at the end of the day it went out");
        assertEquals(CutoffRecovery.FROM_UPDATED, r.source(),
                "the day was read off a stamp that witnessed the release, so it says so");
        assertTrue(r.reconstructed());

        // The changeover, three weeks after the window opened, is on the right
        // side of the cut-off -- which the window-open day could not manage.
        assertTrue(cph(2025, 2, 7, 11, 20).before(r.cutoff()));
        assertTrue(cph(2025, 2, 7, 11, 20).after(CutoffRecovery
                .forAnnualInForce(opened, null, CPH).cutoff()));
    }

    /** Nothing credible witnessed the release, so the window-open day stands. */
    @Test
    public void withoutACredibleReleaseStampTheWindowOpenDayStands() {
        Date opened = cph(2023, 1, 2, 11, 57);

        CutoffRecovery.Recovered none = CutoffRecovery.forAnnualInForce(opened, null, CPH);
        assertEndOfDay(none.cutoff(), 2023, 1, 2, "no stamp, so the window is the only witness");
        assertEquals(CutoffRecovery.PUBLIC_WINDOW, none.source());
    }

    /**
     * A stamp EARLIER than the window-open day does not pull the cut-off back.
     *
     * The edition cannot have been settled before the day it takes effect, and a
     * row prepared in advance says when it was prepared, not when it came into
     * force.
     */
    @Test
    public void aReleaseStampBeforeTheWindowOpensLeavesTheWindowOpenDay() {
        Date opened = cph(2026, 1, 1, 12, 10);
        Date early = cph(2025, 12, 30, 10, 40);

        CutoffRecovery.Recovered r = CutoffRecovery.forAnnualInForce(opened, early, CPH);

        assertEndOfDay(r.cutoff(), 2026, 1, 1, "the later of the two days is the window's");
        assertEquals(CutoffRecovery.PUBLIC_WINDOW, r.source());
    }

    /** With no window at all, a credible stamp is the only day there is. */
    @Test
    public void withNoWindowTheReleaseDayIsTheAnswer() {
        CutoffRecovery.Recovered r = CutoffRecovery.forAnnualInForce(
                null, cph(2025, 3, 4, 9, 0), CPH);
        assertEndOfDay(r.cutoff(), 2025, 3, 4, "the only day anything is known about");
        assertEquals(CutoffRecovery.FROM_UPDATED, r.source());
    }

    /**
     * WHICH day an instant falls on is the zone's answer, and the zone is the
     * series' own.
     *
     * An hour either side of midnight in Copenhagen is the same UTC evening, and
     * a run on a UTC machine would put the two on one day. Both of these belong
     * to 2 January, and the second one is a full day past the first's end.
     */
    @Test
    public void theDayIsDecidedInTheSeriesZoneAndNotTheMachines() {
        Date opened = cph(2026, 1, 1, 12, 10);
        // 2 January 00:30 in Copenhagen is 1 January 23:30 UTC.
        Date justAfterMidnight = cph(2026, 1, 2, 0, 30);
        // 1 January 23:30 in Copenhagen is 1 January 22:30 UTC -- the same day.
        Date justBeforeMidnight = cph(2026, 1, 1, 23, 30);

        assertEndOfDay(CutoffRecovery.forAnnualInForce(opened, justAfterMidnight, CPH).cutoff(),
                2026, 1, 2, "half an hour into the 2nd is the 2nd");
        assertEquals(CutoffRecovery.FROM_UPDATED,
                CutoffRecovery.forAnnualInForce(opened, justAfterMidnight, CPH).source());

        assertEndOfDay(CutoffRecovery.forAnnualInForce(opened, justBeforeMidnight, CPH).cutoff(),
                2026, 1, 1, "half an hour before midnight is still the 1st");
        assertEquals(CutoffRecovery.PUBLIC_WINDOW,
                CutoffRecovery.forAnnualInForce(opened, justBeforeMidnight, CPH).source());
    }

    /** No window, no stamp, no answer -- and a human is told rather than given a date. */
    @Test
    public void anInForceAnnualWithNoWindowAndNoStampIsManual() {
        CutoffRecovery.Recovered r = CutoffRecovery.forAnnualInForce(null, null, CPH);
        assertNull(r.cutoff());
        assertEquals(CutoffRecovery.MANUAL, r.source());
    }
}
