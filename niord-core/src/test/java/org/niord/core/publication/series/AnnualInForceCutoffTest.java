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

package org.niord.core.publication.series;

import org.junit.jupiter.api.Test;
import org.niord.core.domain.Domain;
import org.niord.core.publication.series.resolve.TimeRelation;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one rule that decides an annual in-force edition's cut-off, and the two
 * callers that must agree on it.
 *
 * The cut-off of such an edition is the end of the LATER of two days: the day
 * its public window opens, and the day it was released. The changeover is done
 * by hand over a day, and the window is opened either during that sitting or
 * nominally at the turn of the year with the sitting weeks later -- so what was
 * in force at the end of the day the edition went out is the edition, and the
 * window-open day is the same answer only when the two are the same day.
 *
 * The publish action reads this from the edition's boundary and the clock; the
 * archive reader reads it from a stored window and a stored release stamp. A
 * recovered edition and a natively published one sit in the same series and are
 * compared against each other, so they have to land on the same instant.
 */
public class AnnualInForceCutoffTest {

    private static final ZoneId CPH = ZoneId.of("Europe/Copenhagen");

    private static Date at(int y, int m, int d, int h, int min) {
        return Date.from(ZonedDateTime.of(y, m, d, h, min, 0, 0, CPH).toInstant());
    }

    private static void assertEndOfDay(Date actual, int y, int m, int d, String why) {
        assertNotNull(actual, why);
        ZonedDateTime z = actual.toInstant().atZone(CPH);
        assertEquals(y, z.getYear(), why);
        assertEquals(m, z.getMonthValue(), why);
        assertEquals(d, z.getDayOfMonth(), why);
        assertEquals(23, z.getHour(), why);
        assertEquals(59, z.getMinute(), why);
        assertEquals(59, z.getSecond(), why);
        assertEquals(999, z.getNano() / 1_000_000, why);
    }

    // ------------------------------------------------------------ the rule

    /** No release to believe: the window-open day is the only day there is. */
    @Test
    public void withoutAReleaseTheWindowOpenDayStands() {
        Date opens = at(2023, 1, 2, 11, 57);
        assertEndOfDay(CutoffDefault.annualInForceCutoff(opens, null, CPH), 2023, 1, 2,
                "nothing witnessed a release, so the window is the only witness");
        assertFalse(CutoffDefault.releaseDayIsLater(opens, null, CPH));
    }

    /**
     * A release BEFORE the window opens does not pull the cut-off back.
     *
     * An edition prepared in December for a January boundary is still the edition
     * of that boundary, and nothing about the year it describes had happened when
     * the row was written.
     */
    @Test
    public void aReleaseBeforeTheWindowLeavesTheWindowOpenDay() {
        Date opens = at(2026, 1, 1, 12, 10);
        assertEndOfDay(CutoffDefault.annualInForceCutoff(opens, at(2025, 12, 30, 10, 40), CPH),
                2026, 1, 1, "the later of the two days is the window's");
        assertFalse(CutoffDefault.releaseDayIsLater(opens, at(2025, 12, 30, 10, 40), CPH));
    }

    /** Released during the sitting that opened the window: one day, one answer. */
    @Test
    public void aReleaseOnTheSameDayIsTheSameAnswerAsNoReleaseAtAll() {
        Date opens = at(2025, 2, 7, 10, 28);
        Date released = at(2025, 2, 7, 12, 3);

        assertEquals(CutoffDefault.annualInForceCutoff(opens, null, CPH),
                CutoffDefault.annualInForceCutoff(opens, released, CPH),
                "the same day cannot produce two different instants");
        assertEndOfDay(CutoffDefault.annualInForceCutoff(opens, released, CPH), 2025, 2, 7,
                "the end of the changeover day, which contains the whole sitting");
        assertFalse(CutoffDefault.releaseDayIsLater(opens, released, CPH),
                "the same day is not a LATER day");
    }

    /**
     * Released weeks after a nominal window opened: the release day.
     *
     * The firing-areas shape. The window is dated 1 January and the changeover is
     * done in February; at the end of 1 January the previous edition was still
     * the one in force.
     */
    @Test
    public void aReleaseAfterTheWindowOpensTakesTheReleaseDay() {
        Date opens = at(2025, 1, 1, 12, 10);
        Date released = at(2025, 2, 26, 12, 12);

        assertEndOfDay(CutoffDefault.annualInForceCutoff(opens, released, CPH), 2025, 2, 26,
                "what was in force at the end of the day the edition went out");
        assertTrue(CutoffDefault.releaseDayIsLater(opens, released, CPH));
        assertTrue(CutoffDefault.annualInForceCutoff(opens, released, CPH)
                        .after(CutoffDefault.annualInForceCutoff(opens, null, CPH)),
                "and it is strictly later than the window-open day it replaced");
    }

    /** One bound missing is still an answer; both missing is not. */
    @Test
    public void aMissingBoundLeavesTheOtherAndTwoLeaveNothing() {
        assertEndOfDay(CutoffDefault.annualInForceCutoff(null, at(2025, 3, 4, 9, 0), CPH),
                2025, 3, 4, "with no window, the release day is the only day known");
        assertTrue(CutoffDefault.releaseDayIsLater(null, at(2025, 3, 4, 9, 0), CPH));
        assertNull(CutoffDefault.annualInForceCutoff(null, null, CPH));
    }

    /**
     * WHICH day an instant falls on is decided in the series' zone.
     *
     * Half an hour either side of midnight in Copenhagen is one and the same UTC
     * evening. A run that answered in the machine's zone would put both on 1
     * January, and the two answers here are a full day apart.
     */
    @Test
    public void theDayIsDecidedInTheGivenZoneAndNotTheMachines() {
        Date opens = at(2026, 1, 1, 12, 10);
        Date justAfterMidnight = at(2026, 1, 2, 0, 30);    // 1 January 23:30 UTC
        Date justBeforeMidnight = at(2026, 1, 1, 23, 30);  // 1 January 22:30 UTC

        assertEndOfDay(CutoffDefault.annualInForceCutoff(opens, justAfterMidnight, CPH),
                2026, 1, 2, "half an hour into the 2nd is the 2nd");
        assertEndOfDay(CutoffDefault.annualInForceCutoff(opens, justBeforeMidnight, CPH),
                2026, 1, 1, "half an hour before midnight is still the 1st");

        // The same two instants in UTC land on one day, which is what makes the
        // zone load-bearing rather than cosmetic.
        assertEquals(CutoffDefault.endOfDay(justAfterMidnight, ZoneId.of("UTC")),
                CutoffDefault.endOfDay(justBeforeMidnight, ZoneId.of("UTC")),
                "both are 1 January in UTC, so a UTC answer cannot tell them apart");
    }

    /** The shape test the rule is gated on: only a YEARLY in-force series. */
    @Test
    public void onlyAYearlyInForceSeriesTakesTheDayRule() {
        assertTrue(CutoffDefault.isAnnualInForce(SeriesCadence.YEARLY, TimeRelation.IN_FORCE_AT_CUTOFF));
        assertFalse(CutoffDefault.isAnnualInForce(SeriesCadence.YEARLY, TimeRelation.PUBLISHED_IN_INTERVAL),
                "an accumulated annual is decided where its window closes");
        assertFalse(CutoffDefault.isAnnualInForce(SeriesCadence.WEEKLY, TimeRelation.IN_FORCE_AT_CUTOFF),
                "a weekly in-force list has no day-long changeover to contain");
    }

    // --------------------------------------------------- the publish default

    private static PublicationSeries annualInForce() {
        Domain domain = new Domain();
        domain.setTimeZone("Europe/Copenhagen");
        PublicationSeries series = new PublicationSeries();
        series.setDomain(domain);
        series.setCadence(SeriesCadence.YEARLY);
        series.setTimeRelation(TimeRelation.IN_FORCE_AT_CUTOFF);
        series.setCutoffDefault(CutoffDefault.PERIOD_START);
        return series;
    }

    /** An in-force issue carries ONE bound, and it is the upper one. */
    private static PublicationIssue editionFor(Date boundary) {
        PublicationIssue issue = new PublicationIssue();
        issue.setIntervalFrom(null);
        issue.setIntervalTo(boundary);
        return issue;
    }

    /**
     * Published on the boundary's own day, or before it: the boundary day.
     *
     * The edition is the edition OF that day, and nothing later has happened yet.
     */
    @Test
    public void publishingOnOrBeforeTheBoundaryKeepsTheBoundaryDay() {
        PublicationSeries series = annualInForce();
        Date boundary = at(2026, 1, 1, 12, 10);

        assertEndOfDay(IssuePublishService.defaultCutoff(
                        editionFor(boundary), series, at(2026, 1, 1, 14, 0)),
                2026, 1, 1, "released the same day the boundary names");
        assertEndOfDay(IssuePublishService.defaultCutoff(
                        editionFor(boundary), series, at(2025, 12, 30, 9, 0)),
                2026, 1, 1, "prepared in December for a January edition");
    }

    /** Published after the boundary: the day it actually went out. */
    @Test
    public void publishingAfterTheBoundaryTakesTheReleaseDay() {
        PublicationSeries series = annualInForce();
        Date boundary = at(2025, 1, 1, 12, 10);

        assertEndOfDay(IssuePublishService.defaultCutoff(
                        editionFor(boundary), series, at(2025, 2, 26, 12, 12)),
                2025, 2, 26, "the changeover happened in February, so that is when it was settled");
    }

    /**
     * The two callers land on the same instant for the same edition.
     *
     * This is the whole reason the rule is one function. A series holds recovered
     * editions and natively published ones side by side, and a cut-off that
     * differed by so much as a millisecond between them would order them wrongly
     * against each other.
     */
    @Test
    public void thePublishDefaultAndTheRecoveredAnswerAgree() {
        PublicationSeries series = annualInForce();
        Date boundary = at(2025, 1, 1, 12, 10);
        Date released = at(2025, 2, 26, 12, 12);

        assertEquals(
                org.niord.core.publication.series.legacy.CutoffRecovery
                        .forAnnualInForce(boundary, released, CPH).cutoff(),
                IssuePublishService.defaultCutoff(editionFor(boundary), series, released),
                "a recovered edition and a natively published one must describe the same instant");
    }

    /** An accumulated annual is untouched: its window's close, to the second. */
    @Test
    public void anAccumulatedAnnualStillTakesItsWindowCloseVerbatim() {
        Domain domain = new Domain();
        domain.setTimeZone("Europe/Copenhagen");
        PublicationSeries series = new PublicationSeries();
        series.setDomain(domain);
        series.setCadence(SeriesCadence.YEARLY);
        series.setTimeRelation(TimeRelation.PUBLISHED_IN_INTERVAL);
        series.setCutoffDefault(CutoffDefault.PERIOD_END);

        Date closes = at(2003, 12, 31, 23, 59);
        PublicationIssue issue = editionFor(closes);
        issue.setIntervalFrom(at(2003, 1, 1, 0, 0));

        assertEquals(closes, IssuePublishService.defaultCutoff(issue, series, at(2016, 12, 15, 14, 5)),
                "what was published during a year is known when the year closes, "
                        + "whenever the volume was loaded");
    }
}
