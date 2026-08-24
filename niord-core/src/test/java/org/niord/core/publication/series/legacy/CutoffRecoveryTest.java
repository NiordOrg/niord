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
 * B5.4b. The cut-off recovery cascade.
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
 * than asserting figures it cannot reproduce. B5.4b's numeric acceptance
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
}
