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
import org.niord.core.publication.series.PublicationIssue;
import org.niord.core.publication.series.PublicationSeries;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.resolve.TimeRelation;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Which archived issues the replay can compare at all.
 *
 * A skip is a statement that a row has no comparable answer, and saying so about
 * one issue is very different from raising out of the whole replay -- which is
 * what an unguarded case does. The historical replay backs a diagnostic report an
 * operator reads during the cutover window, and a single bad row taking it down
 * means the evidence gate cannot run at all.
 */
public class ReplaySkipTest {

    private static final long DAY = 24L * 3600 * 1000;

    private static PublicationSeries queryBackedSeries() {
        PublicationSeries s = new PublicationSeries();
        s.setSeriesId("weekly-ntm");
        s.setContentMode(ContentMode.GENERATED_FROM_QUERY);
        s.setCriteria(new IssueCriteriaVo());
        return s;
    }

    private static PublicationIssue comparableIssue(Date from, Date cutoff) {
        PublicationIssue i = new PublicationIssue();
        i.setPublicId("nm-w27-2026");
        i.setSeries(queryBackedSeries());
        i.setIntervalFrom(from);
        i.setCutoffStampedAt(cutoff);
        i.setSnapshotTimeRelation(TimeRelation.PUBLISHED_IN_INTERVAL);
        i.setSnapshotAliveAtCutoff(false);
        return i;
    }

    /** The ordinary case: a real window, so nothing is skipped. */
    @Test
    public void awindowWithTimeInItIsComparable() {
        Date from = new Date(1_700_000_000_000L);
        assertNull(ReplayHarness.skipReasonFor(comparableIssue(from, new Date(from.getTime() + 7 * DAY))),
                "an ordinary weekly issue must be replayed, not skipped");
    }

    /**
     * A window with no time in it is SKIPPED, not resolved.
     *
     * Three archived issues have one: a withdrawal and its replacement are
     * written by a single release action minutes apart, and the replacement
     * chains off the withdrawn twin's close -- so its period opens exactly where
     * it ends. Constructing that interval raises rather than returning nothing,
     * and the exception escaped the per-issue handling and took the entire
     * historical replay with it, so the report answered 500 and the cutover
     * evidence could not be produced at all.
     */
    @Test
    public void awindowWithNoTimeInItIsSkippedRatherThanRaising() {
        Date instant = new Date(1_700_000_000_000L);

        assertEquals(ReplayReport.SkipReason.EMPTY_INTERVAL,
                ReplayHarness.skipReasonFor(comparableIssue(instant, instant)),
                "a cut-off ON the interval start leaves no period to resolve over");

        assertEquals(ReplayReport.SkipReason.EMPTY_INTERVAL,
                ReplayHarness.skipReasonFor(
                        comparableIssue(instant, new Date(instant.getTime() - 1))),
                "a cut-off BEFORE the interval start is the same fact, more obviously");
    }

    /** The earlier skips still win, so a skip names the most specific reason. */
    @Test
    public void amissingBoundIsReportedAsTheMissingBoundRatherThanAsAnEmptyWindow() {
        Date instant = new Date(1_700_000_000_000L);

        assertEquals(ReplayReport.SkipReason.NO_INTERVAL,
                ReplayHarness.skipReasonFor(comparableIssue(null, instant)));
        assertEquals(ReplayReport.SkipReason.NO_CUTOFF,
                ReplayHarness.skipReasonFor(comparableIssue(instant, null)));
    }

    /** A series with no membership semantics has nothing to reproduce. */
    @Test
    public void anissueWithNoQueryIsSkippedBeforeAnyBoundIsRead() {
        PublicationIssue i = comparableIssue(new Date(1_700_000_000_000L),
                new Date(1_700_000_000_000L));
        i.getSeries().setContentMode(ContentMode.UPLOADED_FILE);

        assertEquals(ReplayReport.SkipReason.NO_MEMBERSHIP_SEMANTICS,
                ReplayHarness.skipReasonFor(i),
                "an uploaded file was never generated from a member list, so 'reproducible from "
                        + "the member list' is not a property it has");
    }
}
