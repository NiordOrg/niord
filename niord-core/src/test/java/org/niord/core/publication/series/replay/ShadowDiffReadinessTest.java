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
import org.niord.core.publication.series.ContentMode;
import org.niord.core.publication.series.PublicationSeries;
import org.niord.core.publication.series.SeriesCadence;
import org.niord.core.publication.series.SeriesKind;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.criteria.MessageSeriesCriterionVo;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one readiness rule, over runs ordered newest RELEASE first.
 *
 * Measured on the reseeded estate: weekly-ntm had 470 green runs of 500 and
 * its four most recent releases green, and reported a streak of 0 -- because
 * one permanently-skipping release was re-diffed every sweep and its fresh
 * comparedAt sat at the head of a list ordered by diff time. Two annex series
 * reported "no" forever, because every release they have skips with
 * NO_MEMBERSHIP_SEMANTICS and a rule of the form "two consecutive green
 * comparisons" is unsatisfiable for a publication that cannot be compared.
 *
 * The exemption that answers that is asked of the SERIES, and the second half of
 * this file is why. Inferred from the runs it covered only a series whose
 * releases had been looked at and skipped -- so the annexes qualified and a
 * one-off with an uploaded document, which has nothing to look at, did not. The
 * bulk flip then refused the whole cutover batch over one such row.
 */
public class ShadowDiffReadinessTest {

    private static ShadowDiffRun run(int daysAgo, boolean green, String skip) {
        ShadowDiffRun r = new ShadowDiffRun();
        r.setCutoffAt(new Date(System.currentTimeMillis() - daysAgo * 86_400_000L));
        r.setComparedAt(new Date());
        r.setGreen(green);
        r.setSkipReason(skip);
        return r;
    }

    @Test
    public void theStreakCountsTheNewestReleasesAndStopsAtTheFirstThatDidNotAgree() {
        List<ShadowDiffRun> newestFirst = List.of(
                run(1, true, null), run(8, true, null), run(15, true, null),
                run(22, false, null), run(29, true, null));

        ShadowDiffService.Readiness r = ShadowDiffService.readinessOf(newestFirst);

        assertEquals(3, r.consecutiveGreen());
        assertEquals(5, r.runs());
        assertEquals(0, r.skipped());
        assertFalse(r.exempt());
        assertTrue(r.ready());
    }

    /** A skipped release breaks the streak: a week nobody could compare is not evidence. */
    @Test
    public void aSkippedReleaseDoesNotExtendAStreak() {
        List<ShadowDiffRun> newestFirst = List.of(
                run(1, true, null), run(8, true, "EMPTY_TAG"), run(15, true, null), run(22, true, null));

        ShadowDiffService.Readiness r = ShadowDiffService.readinessOf(newestFirst);

        assertEquals(1, r.consecutiveGreen());
        assertEquals(1, r.skipped());
        assertFalse(r.ready());
    }

    /**
     * An old skip somewhere down the list is history, not the head of it. Before
     * the ordering fix a re-diffed old skip carried the newest comparedAt and
     * ended the streak at zero.
     */
    @Test
    public void anOldSkipDeepInTheHistoryDoesNotZeroTheStreak() {
        List<ShadowDiffRun> newestFirst = List.of(
                run(1, true, null), run(8, true, null), run(400, true, "NO_MEMBERSHIP_SEMANTICS"));

        assertEquals(2, ShadowDiffService.readinessOf(newestFirst).consecutiveGreen());
        assertTrue(ShadowDiffService.readinessOf(newestFirst).ready());
    }

    /**
     * A series none of whose releases carries a member list can never earn a
     * green comparison. It is exempt -- reported as not comparable -- rather than
     * "no" forever, and its readiness is evidenced by the rehearsal instead.
     */
    @Test
    public void aSeriesWithoutMembershipSemanticsIsExemptRatherThanForeverNotReady() {
        List<ShadowDiffRun> newestFirst = List.of(
                run(1, true, "NO_MEMBERSHIP_SEMANTICS"), run(370, true, "NO_MEMBERSHIP_SEMANTICS"));

        ShadowDiffService.Readiness r = ShadowDiffService.readinessOf(newestFirst);

        assertTrue(r.exempt());
        assertTrue(r.ready());
        assertEquals(0, r.consecutiveGreen());
    }

    /** One comparable release among the skips is enough to lose the exemption. */
    @Test
    public void oneComparableReleaseMeansTheSeriesIsNotExempt() {
        List<ShadowDiffRun> newestFirst = List.of(
                run(1, true, "NO_MEMBERSHIP_SEMANTICS"), run(370, true, null));

        ShadowDiffService.Readiness r = ShadowDiffService.readinessOf(newestFirst);

        assertFalse(r.exempt());
        assertFalse(r.ready(), "one skipped newest release and one green older one is not two green");
    }

    @Test
    public void noRunsIsNotExemptAndNotReady() {
        ShadowDiffService.Readiness r = ShadowDiffService.readinessOf(List.of());
        assertFalse(r.exempt());
        assertFalse(r.ready());
    }

    // ------------------------------------------------- exemption from the series

    private static PublicationSeries series(SeriesKind kind, SeriesCadence cadence,
                                            ContentMode mode, boolean withCriteria) {
        PublicationSeries s = new PublicationSeries();
        s.setSeriesId(kind + "-" + cadence + "-" + mode);
        s.setKind(kind);
        s.setCadence(cadence);
        s.setContentMode(mode);
        if (withCriteria) {
            IssueCriteriaVo doc = new IssueCriteriaVo();
            MessageSeriesCriterionVo node = new MessageSeriesCriterionVo();
            node.setValues(new ArrayList<>(List.of("dma-nm")));
            doc.getCriteria().add(node);
            s.setCriteria(doc);
        }
        return s;
    }

    /**
     * A one-off with an uploaded document, and NOTHING has ever compared it.
     *
     * The defect this covers: the exemption was read off the runs, so it needed
     * a series whose releases had at least been looked at and skipped. A one-off
     * has one release and no member list, so there was nothing to look at -- and
     * the bulk flip refused the entire cutover batch with "'aids-to-navigation'
     * has 0 consecutive green comparison(s) of 0 run(s)", asking for evidence
     * that series is incapable of producing.
     */
    @Test
    public void aOneOffWithAnUploadedDocumentIsExemptWithNoRunsAtAll() {
        ShadowDiffService.Readiness r = ShadowDiffService.readinessOf(List.of(),
                series(SeriesKind.ONE_OFF, SeriesCadence.NONE, ContentMode.UPLOADED_FILE, false));

        assertTrue(r.exempt(), "an uploaded document has no member list to reproduce");
        assertTrue(r.ready());
        assertEquals(0, r.runs());
    }

    /** An unscheduled series that states no membership at all is exempt for the same reason. */
    @Test
    public void anUnscheduledSeriesWithNoCriteriaIsExempt() {
        ShadowDiffService.Readiness r = ShadowDiffService.readinessOf(List.of(),
                series(SeriesKind.UNSCHEDULED, SeriesCadence.NONE, ContentMode.EXTERNAL_LINK, false));

        assertTrue(r.exempt());
        assertTrue(r.ready());
    }

    /**
     * And a SCHEDULED one, which is where the kind-shaped reading of the rule
     * would have gone wrong: the accumulated yearly list runs on a cadence and
     * still publishes files somebody uploads.
     */
    @Test
    public void aScheduledSeriesWithUploadedFilesAndNoCriteriaIsExempt() {
        ShadowDiffService.Readiness r = ShadowDiffService.readinessOf(List.of(),
                series(SeriesKind.SCHEDULED, SeriesCadence.YEARLY, ContentMode.UPLOADED_FILE, false));

        assertTrue(r.exempt(), "a cadence is not a membership; the files are uploaded");
        assertTrue(r.ready());
    }

    /**
     * A generated series with criteria is STILL GATED, however few runs it has.
     *
     * The half of the rule that has to keep holding. This series can be compared,
     * so the absence of comparisons is missing evidence rather than an impossible
     * demand, and answering "ready" for it would flip the estate's real weeklies
     * on no evidence whatsoever.
     */
    @Test
    public void aGeneratedSeriesWithCriteriaAndNoRunsIsNotExemptAndNotReady() {
        ShadowDiffService.Readiness r = ShadowDiffService.readinessOf(List.of(),
                series(SeriesKind.SCHEDULED, SeriesCadence.WEEKLY, ContentMode.GENERATED_FROM_QUERY, true));

        assertFalse(r.exempt());
        assertFalse(r.ready());
        assertEquals(0, r.consecutiveGreen());
    }

    /** A generated series with no criteria states no membership either. */
    @Test
    public void aGeneratedSeriesWithNoCriteriaHasNoMembershipToCompare() {
        assertFalse(ShadowDiffService.hasMembershipSemantics(
                series(SeriesKind.SCHEDULED, SeriesCadence.WEEKLY,
                        ContentMode.GENERATED_FROM_QUERY, false)));
        assertTrue(ShadowDiffService.hasMembershipSemantics(
                series(SeriesKind.SCHEDULED, SeriesCadence.WEEKLY,
                        ContentMode.GENERATED_FROM_QUERY, true)));
    }

    /**
     * The series wins over the runs, in BOTH directions.
     *
     * The two answers can disagree, because a run is a fact recorded at the
     * moment a release was looked at and the series row is the fact now: a
     * content mode corrected after its releases were diffed leaves skips that no
     * longer describe it, and comparisons made before it were stale the moment
     * it changed. The current row is the one to believe.
     */
    @Test
    public void theSeriesDecidesTheExemptionWhereverBothCouldAnswer() {
        PublicationSeries comparable = series(SeriesKind.SCHEDULED, SeriesCadence.WEEKLY,
                ContentMode.GENERATED_FROM_QUERY, true);
        ShadowDiffService.Readiness stillGated = ShadowDiffService.readinessOf(
                List.of(run(1, true, "NO_MEMBERSHIP_SEMANTICS"), run(8, true, "NO_MEMBERSHIP_SEMANTICS")),
                comparable);

        assertFalse(stillGated.exempt(), "the skips are stale; the series states a membership now");
        assertFalse(stillGated.ready());

        PublicationSeries uploaded = series(SeriesKind.SCHEDULED, SeriesCadence.WEEKLY,
                ContentMode.UPLOADED_FILE, false);
        ShadowDiffService.Readiness exempt = ShadowDiffService.readinessOf(
                List.of(run(1, true, null), run(8, false, null)), uploaded);

        assertTrue(exempt.exempt(), "there is nothing left to compare, whatever the old runs found");
        assertTrue(exempt.ready());
        assertEquals(1, exempt.consecutiveGreen(), "the streak is still counted and still reported");
    }
}
