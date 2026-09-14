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

package org.niord.core.publication.series;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.TestIds;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.vo.MessagePublication;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Which publications are numbered by a SPAN, and which carry a single number.
 *
 * A double week is a range of ISO WEEKS -- there is no column that could hold
 * "two months" or "two years" -- so the span an issue is numbered from is a
 * weekly fact and nothing else. A tiling series answered with its own lower
 * bound whatever its cadence was, and the annual compilation is where that shows
 * up worst: a window running 1 January to 31 December is fifty-two seven-day
 * periods to the arithmetic, so every edition of it stored a week range, and the
 * first number of that range came from a week in the January it opened in rather
 * than from the week it closes. The title, the file name and the public link all
 * read those numbers.
 *
 * The two weekly answers are asserted beside the others, because the rule is one
 * rule: narrowing it to a cadence must leave a weekly publication -- tiling or
 * in-force -- deriving exactly what it derived before.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class IssueSpanCadenceTest {

    /** The zone every derivation below is read in; never the JVM default. */
    private static final ZoneId CPH = ZoneId.of("Europe/Copenhagen");

    @Inject
    IssueShape shape;

    @Inject
    EntityManager em;

    // ------------------------------------------------------------------ fixtures

    /** An instant in the series' own zone, to the minute. */
    private static Date at(int year, int month, int day, int hour, int minute) {
        return Date.from(ZonedDateTime.of(year, month, day, hour, minute, 0, 0, CPH).toInstant());
    }

    private PublicationSeries series(SeriesCadence cadence, TimeRelation relation,
                                     NumberingScheme scheme, CutoffDefault cutoffDefault) {
        PublicationCategory c = new PublicationCategory();
        c.setCategoryId(TestIds.category());
        c.setPriority(100);
        em.persist(c);

        PublicationSeries s = new PublicationSeries();
        s.setSeriesId(TestIds.series());
        s.setStatus(SeriesStatus.ACTIVE);
        s.setContentMode(ContentMode.GENERATED_FROM_QUERY);
        s.setReportId("some-report");
        s.setCadence(cadence);
        s.setTimeRelation(relation);
        s.setAliveAtCutoff(relation == TimeRelation.IN_FORCE_AT_CUTOFF);
        s.setReleaseMode(ReleaseMode.MANUAL_GATE);
        s.setNextIssueCreation(NextIssueCreation.MANUAL);
        s.setMessagePublication(MessagePublication.NONE);
        s.setNumberingScheme(scheme);
        s.setCutoffDefault(cutoffDefault);
        // The owner supplies the zone the cut-offs are read in, so the weeks below
        // are Copenhagen's rather than the container's.
        s.setDomain(TestOwnerDomain.of(em));
        s.setCategory(c);
        s.getLanguages().add("da");
        PublicationSeriesDesc d = s.createDesc("da");
        d.setName("Test series");
        d.setNameSuggestionPattern("Uge ${week}, ${year}");
        em.persist(s);
        return s;
    }

    /**
     * An issue planted directly, with the bounds the case is about.
     *
     * Built rather than created through the lifecycle: what is under test is the
     * derivation from a period, and the create path's refusals are about other
     * things entirely.
     */
    private PublicationIssue issue(PublicationSeries s, Date from, Date to) {
        PublicationIssue i = new PublicationIssue();
        i.setSeries(s);
        i.setPublicId(UUID.randomUUID().toString());
        i.setRepoPath("publications/" + i.getPublicId());
        i.setStatus(IssueStatus.OPEN);
        i.setIntervalFrom(from);
        i.setIntervalFromSource(from == null ? null : IntervalBoundSource.MANUAL);
        i.setIntervalTo(to);
        i.setIntervalToSource(to == null ? null : IntervalBoundSource.NOMINAL);
        PublicationIssueDesc d = i.createDesc("da");
        d.setName("Test issue");
        em.persist(i);
        return i;
    }

    /** A released neighbour, so an in-force period has something to run from. */
    private void publishedAt(PublicationSeries s, Date cutoff) {
        PublicationIssue i = issue(s, null, null);
        i.setStatus(IssueStatus.PUBLISHED);
        i.setCutoffStampedAt(cutoff);
        i.setPublishedAt(cutoff);
        i.setPublicFrom(cutoff);
        em.merge(i);
    }

    // -------------------------------------------------------- the other cadences

    /**
     * THE DEFECT THIS PINS. A year-long period is one edition, not fifty-two.
     *
     * The accumulated annual runs 1 January to 31 December and tiles, so its own
     * lower bound was handed to the multi-period test -- which counts SEVEN-DAY
     * periods, finds fifty-two of them, and renumbers the edition for the week
     * fifty-one weeks before the one it closes in. Every annual in the estate
     * therefore carried a week range, and the week it was named for came out of
     * the January its period opened in.
     */
    @Test
    @Transactional
    public void anAnnualCompilationCarriesOneWeekAndNoRange() {
        PublicationSeries s = series(SeriesCadence.YEARLY, TimeRelation.PUBLISHED_IN_INTERVAL,
                NumberingScheme.YEAR_EDITION, CutoffDefault.PERIOD_END);
        PublicationIssue i = issue(s, at(2025, 1, 1, 0, 0), at(2025, 12, 31, 23, 59));
        em.flush();

        shape.renumber(i, s);

        assertEquals(1, i.getWeek(),
                "31 December 2025 falls in ISO week 1; the annual is numbered for the week its "
                        + "period closes in, whatever its window spans");
        assertNull(i.getWeekTo(),
                "a year is one period. The range came from counting a year-long window in weeks, "
                        + "and there is no column that could hold two years anyway");
        assertEquals(2025, i.getYear(),
                "the edition closing at the end of 2025 is the 2025 edition, in its title and in "
                        + "its file name");
    }

    /**
     * And a MONTHLY period is one month, for the same reason.
     *
     * Four-and-a-bit weeks is the count a monthly window produces, so the annual
     * is the loudest case rather than the only one.
     */
    @Test
    @Transactional
    public void aMonthlyIssueCarriesOneWeekAndNoRange() {
        PublicationSeries s = series(SeriesCadence.MONTHLY, TimeRelation.PUBLISHED_IN_INTERVAL,
                NumberingScheme.MONTH_YEAR, CutoffDefault.PERIOD_END);
        PublicationIssue i = issue(s, at(2025, 3, 1, 0, 0), at(2025, 3, 31, 23, 59));
        em.flush();

        shape.renumber(i, s);

        assertEquals(14, i.getWeek(), "31 March 2025 is the Monday of ISO week 14");
        assertNull(i.getWeekTo(),
                "a month is one period, and its window is four weeks long by arithmetic alone");
        assertEquals(2025, i.getYear());
    }

    // --------------------------------------------------------------- the weekly

    /**
     * The tiling weekly is untouched: a swallowed week is still two weeks.
     *
     * The window opens on the Wednesday of week 14 and closes on the Wednesday of
     * week 16, so week 15 went out with it and the edition is called for both.
     * This is the behaviour the cadence check has to leave exactly as it was.
     */
    @Test
    @Transactional
    public void aTilingWeeklyDoubleWeekStillCarriesBothWeeks() {
        PublicationSeries s = series(SeriesCadence.WEEKLY, TimeRelation.PUBLISHED_IN_INTERVAL,
                NumberingScheme.ISO_WEEK_YEAR, CutoffDefault.RELEASE_MOMENT);
        PublicationIssue i = issue(s, at(2025, 4, 2, 12, 0), at(2025, 4, 16, 12, 0));
        em.flush();

        shape.renumber(i, s);

        assertEquals(15, i.getWeek(), "the first week this issue closed opens the range");
        assertEquals(16, i.getWeekTo(),
                "a weekly window that swallowed a whole period says so; this is the one cadence "
                        + "whose period and whose counting unit are the same thing");
        assertEquals(2025, i.getYear());
    }

    /** An ordinary tiling week is one week, exactly as before. */
    @Test
    @Transactional
    public void anOrdinaryTilingWeekIsStillASingleWeek() {
        PublicationSeries s = series(SeriesCadence.WEEKLY, TimeRelation.PUBLISHED_IN_INTERVAL,
                NumberingScheme.ISO_WEEK_YEAR, CutoffDefault.RELEASE_MOMENT);
        PublicationIssue i = issue(s, at(2025, 4, 9, 12, 0), at(2025, 4, 16, 12, 0));
        em.flush();

        shape.renumber(i, s);

        assertEquals(16, i.getWeek(), "an issue is named for the week it closed in");
        assertNull(i.getWeekTo(), "one period is not a range");
    }

    /**
     * And the in-force weekly still reads its span off the PREDECESSOR's close.
     *
     * The issue carries no window at all, and the fortnight it covers is the gap
     * between the last cut-off and its own. Asserted beside the tiling case, and
     * with the same two weeks, so a change to either route shows up here.
     */
    @Test
    @Transactional
    public void anInForceWeeklyStillCarriesBothWeeksFromItsPredecessor() {
        PublicationSeries s = series(SeriesCadence.WEEKLY, TimeRelation.IN_FORCE_AT_CUTOFF,
                NumberingScheme.ISO_WEEK_YEAR, CutoffDefault.RELEASE_MOMENT);
        publishedAt(s, at(2025, 4, 2, 12, 0));
        PublicationIssue i = issue(s, null, at(2025, 4, 16, 12, 0));
        em.flush();

        shape.renumber(i, s);

        assertEquals(15, i.getWeek());
        assertEquals(16, i.getWeekTo(),
                "the in-force route has no window to read and must still find the week nobody "
                        + "published");
        assertEquals(2025, i.getYear());
    }
}
