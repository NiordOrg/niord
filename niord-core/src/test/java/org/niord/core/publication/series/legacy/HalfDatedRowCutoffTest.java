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

package org.niord.core.publication.series.legacy;

import org.junit.jupiter.api.Test;
import org.niord.core.publication.Publication;
import org.niord.core.publication.series.IssueStatus;
import org.niord.core.publication.series.PublicationIssue;
import org.niord.core.publication.series.PublicationSeries;
import org.niord.core.publication.series.SeriesCadence;
import org.niord.core.publication.series.SeriesStatus;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.vo.PublicationStatus;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An archive row with no dates gets no cut-off, rather than an invented one.
 *
 * The believability bounds are built from the period's open and its nominal
 * close. With NEITHER -- an in-force series, whose issues have no lower bound, and
 * an archive row that carries no start date -- every bound is null and the check
 * degenerates to "yes": the cascade then adopts whatever timestamp it finds
 * first. One row in the estate imported that way with a cut-off two years and
 * three months before the publication existed, which made it the oldest entry of
 * its archive by sort order, forever.
 *
 * The import is one-way and runs once, so an invented date is not a display bug
 * that can be corrected later.
 */
public class HalfDatedRowCutoffTest {

    private static final Date FROZEN = new Date(1_755_000_000_000L);

    private static PublicationSeries weekly(TimeRelation relation) {
        PublicationSeries s = new PublicationSeries();
        s.setSeriesId("weekly-pt");
        s.setStatus(SeriesStatus.DRAFT);
        s.setCadence(SeriesCadence.WEEKLY);
        s.setTimeRelation(relation);
        return s;
    }

    private static Publication row(Date publishFrom, Date updated) {
        Publication p = new Publication();
        p.setPublicationId("e982d095");
        p.setStatus(PublicationStatus.INACTIVE);
        p.setPublishDateFrom(publishFrom);
        p.setUpdated(updated);
        return p;
    }

    private static PublicationIssue issue(PublicationSeries series, Date intervalFrom) {
        PublicationIssue i = new PublicationIssue();
        i.setPublicId("e982d095");
        i.setSeries(series);
        i.setStatus(IssueStatus.PUBLISHED);
        i.setIntervalFrom(intervalFrom);
        return i;
    }

    /**
     * No start date and no lower bound: MANUAL, with no date at all.
     *
     * Not "the row's last-write stamp, which is the only date we have". A
     * timestamp being present is not the same as it being this release, and there
     * is nothing here to check that claim against.
     */
    @Test
    public void ahalfDatedInForceRowGetsNoCutoff() {
        PublicationSeries series = weekly(TimeRelation.IN_FORCE_AT_CUTOFF);
        // Two years and three months before the row could have existed.
        Date longBefore = new Date(FROZEN.getTime() - 71L * 24 * 3600 * 1000 * 10);

        var recovered = LegacyImportService.recoverCutoff(
                issue(series, null), row(null, longBefore), series, List.of(), 0);

        assertNull(recovered.cutoff(),
                "an unbelievable stamp was adopted as the content period's close. With no start "
                        + "date and no lower bound there is nothing to check a candidate against, "
                        + "so the honest answer is that nobody knows.");
        assertEquals(CutoffRecovery.MANUAL, recovered.source());
        assertTrue(recovered.reconstructed(),
                "the row is marked as not carrying a stamped cut-off, which is what an admin "
                        + "reading it needs to see");
    }

    /** With a nominal close the cascade still answers, so the guard is about the missing dates. */
    @Test
    public void arowThatDoesCarryItsCloseIsStillRecovered() {
        PublicationSeries series = weekly(TimeRelation.PUBLISHED_IN_INTERVAL);
        Date close = new Date(1_700_000_000_000L);

        var recovered = LegacyImportService.recoverCutoff(
                issue(series, new Date(close.getTime() - 7L * 24 * 3600 * 1000)),
                row(close, new Date(close.getTime() + 30 * 60 * 1000L)),
                series, List.of(), 0);

        assertNotNull(recovered.cutoff(),
                "a row with a nominal close must still recover one; the guard is about rows that "
                        + "carry no dates, not about tightening the cascade");
    }

    /** An OPEN row was never released, which is a different fact and keeps its own source. */
    @Test
    public void anunreleasedRowIsNotTreatedAsAHalfDatedOne() {
        PublicationSeries series = weekly(TimeRelation.IN_FORCE_AT_CUTOFF);
        PublicationIssue open = issue(series, null);
        open.setStatus(IssueStatus.OPEN);

        var recovered = LegacyImportService.recoverCutoff(
                open, row(null, FROZEN), series, List.of(), 0);

        assertNull(recovered.cutoff());
        assertEquals(CutoffRecovery.NOT_RELEASED, recovered.source(),
                "an issue that never published has no release to recover; saying MANUAL there "
                        + "would read as evidence lost rather than as an event that never happened");
    }
}
