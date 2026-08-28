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

import org.junit.jupiter.api.Test;

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
}
