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

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.publication.series.IntervalBoundSource;
import org.niord.core.publication.series.IssueStatus;
import org.niord.core.publication.series.PublicationIssue;

import java.time.ZonedDateTime;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An annual issue's cut-off is a boundary of the period it describes, and the
 * moment somebody released it is a different fact.
 *
 * An ACCUMULATED annual is decided where its window closes: "Akkumuleret EfS
 * 2003" is what was published during 2003 and was loaded in December 2016,
 * thirteen years after its content period closed, and its last-write stamp is
 * not a release moment at all. An IN-FORCE annual is decided at the end of a
 * DAY, because its changeover is a day's work -- the later of the day its
 * window opens and the day it went out, which for the firing-areas editions are
 * seven weeks apart.
 *
 * The last-write stamp is the publication moment where it is credible, and where
 * it decided the DAY it is still not the cut-off: the cut-off is the end of that
 * day.
 *
 * Driven from the captured estate, by publicationId.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class AnnualCutoffTest {

    private static final String EFS_A_2018 = "0b040947-d602-4d05-bea9-7a46ca271afe";
    private static final String EFS_A_2024 = "39cda106-d6c3-48c8-84f6-5bfec9c4e410";
    private static final String EFS_A_2017_SECOND_EDITION = "35046715-ad41-4654-a1fe-f3a1f87cf724";
    private static final String FIRING_2022_EDITED_NEXT_YEAR = "76bee094-4959-4a0e-bae0-b55ae80a9e17";
    private static final String FIRING_2022_EDITED_IN_YEAR = "19546efb-8f21-42e1-a124-02bd1901b6e6";
    private static final String EFS_A_2019 = "5a558b8b-92b2-4cc1-8da9-fac307a2e79e";
    private static final String EFS_A_2025 = "bad49ccc-b26b-4921-b44e-6f0840be26dc";
    private static final String FIRING_2020_FIRST_EDITION = "745fb228-cf58-4eeb-a164-20640071e4d5";
    private static final String FIRING_2020_SECOND_EDITION = "66e62bf2-4bdd-4581-9e75-a248ae2b9777";
    private static final String FIRING_2026 = "558aa1ac-7807-43b0-8d00-9b074baca026";
    private static final String EFS_A_2017_FIRST_EDITION = "92ab125a-7265-44aa-abb5-3c276ae4b4cf";
    private static final String FIRING_2023 = "7f85c228-77bd-426b-9732-af7fa57b1328";
    private static final String FIRING_2024 = "b558fff5-486b-4716-a4d7-23f8a4b01064";
    private static final String FIRING_2025 = "b5738302-78e2-4e8c-b199-a71d10276de4";
    private static final String FIRING_2027_OPEN = "46c4ed07-17a7-4afc-87f9-c78d266c4805";
    private static final String ACCUMULATED_2003 = "0b97ec80-1b8c-4dca-aa80-1bb5d8c71a63";
    private static final String ACCUMULATED_2018_OPEN_ENDED = "2b27a139-f1dc-4a90-b416-c86364ba6456";

    @Inject
    LegacyImportService importService;

    private LegacyImportService.Plan plan;

    private PublicationIssue issue(String id) {
        if (plan == null) {
            plan = importService.planFrom(LegacyEstateFixture.templates(), LegacyEstateFixture.publications());
        }
        PublicationIssue issue = plan.issues().get(id);
        assertNotNull(issue, id + " is not in the plan");
        return issue;
    }

    /**
     * An in-force annual is decided at the END of a DAY -- the later of the day
     * its window opens and the day it was released.
     *
     * NOT AT THE INSTANT THE WINDOW OPENS, which is what this asserted first. The
     * changeover is a day's work rather than a moment's: on "EfS A - 2025" the
     * window was opened at 10:28:17, the 2024 notices were cancelled at 11:18 and
     * the 2025 notices published at 11:28 -- so resolving at 10:28:17 produced the
     * 2024 list and the shadow diff reported 29 missing and 29 extra against the
     * tag that holds the 2025 one.
     *
     * AND NOT THE WINDOW-OPEN DAY EITHER, WHERE THE TWO DIFFER. A window is opened
     * during the sitting on some editions and named nominally at the turn of the
     * year on others, with the sitting weeks later -- see the firing-areas cases
     * below. What the edition means is what was in force at the end of the day it
     * was released; the window-open day is the same answer only when they are the
     * same day, and it is the fallback when nothing credible witnessed the release.
     */
    @Test
    public void anInForceAnnualIsCutOffAtTheEndOfTheLaterOfItsTwoDays() {
        // Window from 1 January 2018, released on the 2nd: the release day.
        PublicationIssue efsA2018 = issue(EFS_A_2018);
        assertEquals(endOfDayOf(efsA2018, efsA2018.getPublishedAt()), efsA2018.getCutoffStampedAt(),
                "EfS A 2018 has a nominal 1 January window and went out on the 2nd");
        assertEquals(CutoffRecovery.FROM_UPDATED, efsA2018.getCutoffSource(),
                "the day was read off a stamp that witnessed the release");
        assertNull(efsA2018.getIntervalFrom(), "an in-force list has no lower bound");

        // The interval's upper bound is still the boundary the window names. It is
        // what the edition is FOR; the cut-off is when its contents were settled,
        // and those can be a day or two apart.
        assertEquals(efsA2018.getPublicFrom(), efsA2018.getIntervalTo());

        PublicationIssue efsA2024 = issue(EFS_A_2024);
        assertEquals(endOfDayOf(efsA2024, efsA2024.getPublishedAt()), efsA2024.getCutoffStampedAt(),
                "the 2024 window opens on 1 January and the edition went out on the 3rd");
        assertEquals(CutoffRecovery.FROM_UPDATED, efsA2024.getCutoffSource());

        // A mid-year edition released during the sitting that opened its window:
        // the end of that seam DAY, and the window is what named it.
        PublicationIssue efsA2017b = issue(EFS_A_2017_SECOND_EDITION);
        assertEquals(endOfDayOf(efsA2017b, new Date(1488889914000L)), efsA2017b.getCutoffStampedAt(),
                "the second 2017 edition took effect on 7 March and went out the same day");
        assertEquals(CutoffRecovery.PUBLIC_WINDOW, efsA2017b.getCutoffSource(),
                "released on the day the window opened, so the window is what decides");
    }

    /**
     * The absolute instant, written out once, so the rule is readable without
     * running the helper in your head.
     */
    @Test
    public void theCutoffOfEfsA2018IsTheLastMillisecondOfTheDayItWentOut() {
        assertEndOfDay(issue(EFS_A_2018), 2018, 1, 2,
                "to the millisecond: rounded up to midnight this would be the following day, "
                        + "and for an edition taking effect at new year the following YEAR");
    }

    /**
     * The firing-areas editions, which is where the two days come apart widest.
     *
     * These carry a NOMINAL window from the turn of the year while the changeover
     * is done weeks later. On the 2025 edition the window opens 1 January, the
     * outgoing set was cancelled and the incoming set published on 7 February, and
     * the edition was released on 26 February -- so at the end of 1 January none of
     * it had happened and the edition resolved to the 2024 list, 32 members missing
     * and 30 extra. 2024 has the same shape a day out; 2023 has no credible stamp
     * at all, because its row was next written a year later, and keeps its
     * window-open day.
     */
    @Test
    public void aNominallyDatedAnnualIsCutOffOnTheDayItWasActuallyReleased() {
        assertEndOfDay(issue(FIRING_2025), 2025, 2, 26,
                "released 26 February 2025, seven weeks after its window opened");
        assertEquals(CutoffRecovery.FROM_UPDATED, issue(FIRING_2025).getCutoffSource());

        assertEndOfDay(issue(FIRING_2024), 2024, 1, 3,
                "window from 1 January 2024, released on the 3rd");
        assertEquals(CutoffRecovery.FROM_UPDATED, issue(FIRING_2024).getCutoffSource());

        assertEndOfDay(issue(FIRING_2023), 2023, 1, 2,
                "assembled fifteen seconds after its window opened, so the two days are one");
        assertEquals(CutoffRecovery.PUBLIC_WINDOW, issue(FIRING_2023).getCutoffSource());
        assertEquals(new Date(1672653449000L), issue(FIRING_2023).getPublishedAt(),
                "its row was next written a year later, but its own tag creation says when "
                        + "it went out -- which the last-write column alone could not");
    }

    /**
     * The two 2022 firing editions date in the order they were current, and the
     * withdrawal write is what used to break it.
     *
     * Both carry the same window, from 5 January 2022. The FIRST edition's row was
     * last written 2 February at 14:52 -- six minutes after the SECOND edition's
     * tag was created at 14:46 -- so that write is the changeover deactivating it,
     * not its release. Believed as a release it dated the outgoing edition to the
     * day its own replacement went out, and the pair sorted backwards.
     *
     * Now the first edition falls back to its own tag creation, which is its
     * release, and the second is dated by the tag it was assembled from rather
     * than by a window opened four weeks earlier.
     */
    @Test
    public void theTwo2022FiringEditionsDateInTheOrderTheyWereCurrent() {
        PublicationIssue first = issue(FIRING_2022_EDITED_IN_YEAR);
        PublicationIssue second = issue(FIRING_2022_EDITED_NEXT_YEAR);

        assertEndOfDay(first, 2022, 1, 5,
                "released on the day its window opened; its February write is its withdrawal");
        assertEquals(CutoffRecovery.PUBLIC_WINDOW, first.getCutoffSource());
        assertEquals(new Date(1641377573000L), first.getPublishedAt(),
                "and the withdrawal write is not its publication moment either -- its own "
                        + "tag creation is");

        assertEndOfDay(second, 2022, 2, 2,
                "its row was next written a year later, so its own tag creation is what "
                        + "witnessed it going out");
        assertEquals(CutoffRecovery.FROM_UPDATED, second.getCutoffSource());

        assertTrue(first.getCutoffStampedAt().before(second.getCutoffStampedAt()),
                "the edition that came first must date first; it sorted after its own "
                        + "replacement while a withdrawal write was believed");
    }

    /**
     * The two editions whose day comes from their own TAG creation rather than
     * from their last write -- pinned because the tag is the weaker of the two
     * witnesses and these are where it decides.
     *
     * The first 2017 EfS A edition was next written during the changeover that
     * replaced it in March, so only its tag creation -- 2 January, the day after
     * its window opened -- falls credibly inside its own window. That one day
     * moves its resolution from the previous year's list to its own.
     *
     * The 2025 edition is the other kind, and it deserves attention rather than
     * confidence: its window opened 7 February, its notices went out that day,
     * and its row was next written in 2026 -- so the only credible stamp left is
     * a tag created on 26 February, nineteen days later. Its own members are all
     * still in force on that date, so nothing it holds is lost; what cannot be
     * checked from the archive alone is whether anything published in those
     * nineteen days now resolves INTO it.
     */
    @Test
    public void anEditionWhoseOnlyCredibleStampIsItsTagIsDatedByTheTag() {
        assertEndOfDay(issue(EFS_A_2017_FIRST_EDITION), 2017, 1, 2,
                "assembled on 2 January 2017; its March write is the changeover that replaced it");
        assertEquals(CutoffRecovery.FROM_UPDATED, issue(EFS_A_2017_FIRST_EDITION).getCutoffSource());

        assertEndOfDay(issue(EFS_A_2025), 2025, 2, 26,
                "the only stamp inside its own window is its tag creation");
        assertEquals(CutoffRecovery.FROM_UPDATED, issue(EFS_A_2025).getCutoffSource());
        assertTrue(issue(EFS_A_2025).getPublishedAt().after(issue(EFS_A_2025).getPublicFrom()),
                "and the moment it records is that stamp, not the day's end");
    }

    /**
     * An edition is ended by a RE-EDITION of itself, never by next year's row.
     *
     * Each of these was assembled at almost the instant the previous edition went
     * out, which is what a yearly rhythm looks like from the inside: the list for
     * the coming year is started as the current one ships. Reading that as the
     * current edition's replacement rejects the edition's own release stamp and
     * leaves the row claiming nobody ever published it.
     *
     * The 2026 firing edition is the sharpest case -- the 2027 row's tag was
     * created at 09:42:23 on 2 January 2026 and the 2026 row was last written at
     * 09:42:23 on 2 January 2026, the same second, by one action.
     */
    @Test
    public void anEditionIsNotEndedByTheRowForTheFollowingPeriod() {
        assertEquals(new Date(1767343343000L), issue(FIRING_2026).getPublishedAt(),
                "the 2027 row's tag is not the 2026 edition's withdrawal");
        assertEndOfDay(issue(FIRING_2026), 2026, 1, 2,
                "so the day it went out still decides its cut-off");
        assertEquals(CutoffRecovery.FROM_UPDATED, issue(FIRING_2026).getCutoffSource());

        assertEquals(new Date(1546603759000L), issue(EFS_A_2019).getPublishedAt(),
                "the 2020 edition's list was cloned into place a year early, on the very day "
                        + "the 2019 edition went out; that is not the 2019 edition ending");
        assertEndOfDay(issue(EFS_A_2019), 2019, 1, 4, "released on the day its window opened");
    }

    /**
     * A re-edition sharing the window IS the replacement, and the pair still
     * dates in order.
     *
     * The 2020 firing editions run on one window: the first opened 3 January, the
     * second took over on the 8th. The second's tag bounds the first -- but the
     * first's own last write came in an hour before that tag was built, so it is
     * still its release and the edition keeps its moment.
     */
    @Test
    public void aReEditionSharingTheWindowStillBoundsThePrevious() {
        assertEquals(new Date(1578479890000L), issue(FIRING_2020_FIRST_EDITION).getPublishedAt(),
                "written at 11:38, an hour before the replacement's tag at 12:32");
        assertEquals(new Date(1578484221000L), issue(FIRING_2020_SECOND_EDITION).getPublishedAt());

        assertTrue(!issue(FIRING_2020_FIRST_EDITION).getCutoffStampedAt()
                        .after(issue(FIRING_2020_SECOND_EDITION).getCutoffStampedAt()),
                "the first edition must not date after the one that replaced it");
    }

    /** The end of the day an instant falls in, in the SERIES' own zone. */
    private static Date endOfDayOf(PublicationIssue issue, Date instant) {
        return org.niord.core.publication.series.CutoffDefault.endOfDay(
                instant, issue.getSeries().cutoffZone());
    }

    /** The issue's cut-off is the last millisecond of the given day, in the series' zone. */
    private static void assertEndOfDay(PublicationIssue issue, int year, int month, int day, String why) {
        assertNotNull(issue.getCutoffStampedAt(), why);
        ZonedDateTime at = issue.getCutoffStampedAt().toInstant()
                .atZone(issue.getSeries().cutoffZone());
        assertEquals(year, at.getYear(), why);
        assertEquals(month, at.getMonthValue(), why);
        assertEquals(day, at.getDayOfMonth(), why);
        assertEquals(23, at.getHour(), why);
        assertEquals(59, at.getMinute(), why);
        assertEquals(59, at.getSecond(), why);
        assertEquals(999, at.getNano() / 1_000_000, why);
    }

    /** The publication moment is kept apart, and only a credible stamp is one. */
    @Test
    public void thePublicationMomentIsACredibleStampAndNothingElse() {
        assertEquals(new Date(1514875432000L), issue(EFS_A_2018).getPublishedAt(),
                "edited on 2 January 2018, inside the 2018 window: that is when it went out");
        assertEquals(new Date(1704260530000L), issue(EFS_A_2024).getPublishedAt());

        // An accumulated annual is the counter-case, and it is untouched: loaded
        // thirteen years after its content period closed, with no stamp of any
        // kind inside its window.
        assertNull(issue(ACCUMULATED_2003).getPublishedAt(),
                "a stamp in some later year is an edit, never a release");
    }

    /**
     * THE MOMENT IS THE STAMP, THE CUT-OFF IS THE DAY -- even when the same stamp
     * decided both.
     *
     * The stamp that says which day an edition was settled on is not thereby the
     * end of that day. Reading the moment back off the cut-off would report every
     * annual as released at 23:59:59.999, a time nobody worked at, on rows whose
     * actual stamp is sitting right there in the archive.
     */
    @Test
    public void aDayDecidedByAStampStillReportsTheStampAsTheMoment() {
        for (String id : new String[] { EFS_A_2018, EFS_A_2024, FIRING_2025, FIRING_2024 }) {
            PublicationIssue issue = issue(id);
            assertEquals(CutoffRecovery.FROM_UPDATED, issue.getCutoffSource(), id);
            assertNotNull(issue.getPublishedAt(), id + " has no publication moment");
            assertNotEquals(issue.getCutoffStampedAt(), issue.getPublishedAt(),
                    id + ": the release moment must not be the end of the day it fell on");
            assertEquals(issue.getCutoffStampedAt(),
                    endOfDayOf(issue, issue.getPublishedAt()),
                    id + ": and the cut-off is the end of the day that moment falls in");
        }
    }

    /** An accumulated annual describes the year its window names, and is decided where it CLOSES. */
    @Test
    public void anAccumulatedAnnualCoversItsWindowAndIsCutOffWhereItCloses() {
        PublicationIssue acc2003 = issue(ACCUMULATED_2003);
        assertEquals(new Date(1041379200000L), acc2003.getIntervalFrom(), "1 January 2003");
        assertEquals(new Date(1072911540000L), acc2003.getIntervalTo(), "the legacy end, 31 December 2003");
        assertEquals(IntervalBoundSource.NOMINAL, acc2003.getIntervalFromSource());
        assertEquals(IntervalBoundSource.NOMINAL, acc2003.getIntervalToSource());
        assertEquals(acc2003.getIntervalTo(), acc2003.getCutoffStampedAt());
        assertEquals(CutoffRecovery.PUBLIC_WINDOW, acc2003.getCutoffSource());
        assertNull(acc2003.getPublishedAt(), "loaded in December 2016, thirteen years after: not a release moment");
    }

    /** Legacy left the 2018 accumulation open; it closes at the end of its own year, in the series' zone. */
    @Test
    public void anOpenEndedAccumulatedAnnualClosesAtTheEndOfItsYear() {
        PublicationIssue acc2018 = issue(ACCUMULATED_2018_OPEN_ENDED);
        assertNotNull(acc2018.getIntervalTo(), "an open legacy end date is closed at the end of the year");
        ZonedDateTime end = acc2018.getIntervalTo().toInstant().atZone(acc2018.getSeries().cutoffZone());
        assertEquals(2018, end.getYear());
        assertEquals(12, end.getMonthValue());
        assertEquals(31, end.getDayOfMonth());
        assertEquals(23, end.getHour());
        assertEquals(59, end.getMinute());
        assertEquals(IntervalBoundSource.RECOVERED, acc2018.getIntervalToSource(),
                "an invented year-end is flagged as reconstructed, unlike one the archive recorded");
        assertEquals(acc2018.getIntervalTo(), acc2018.getCutoffStampedAt());
        assertNull(acc2018.getPublishedAt(), "edited in 2021: not a release moment");
    }

    /** A never-released annual has no cut-off and no publication moment. */
    @Test
    public void anUnreleasedAnnualHasNeither() {
        PublicationIssue firing2027 = issue(FIRING_2027_OPEN);
        assertEquals(IssueStatus.OPEN, firing2027.getStatus());
        assertNull(firing2027.getCutoffStampedAt());
        assertEquals(CutoffRecovery.NOT_RELEASED, firing2027.getCutoffSource());
        assertNull(firing2027.getPublishedAt());
    }

    /**
     * An annual is numbered for the year its cut-off falls in -- the CALENDAR
     * year, which is what its numbering scheme takes.
     *
     * The distinction is the whole reason the year basis exists. An accumulated
     * edition closes at 31 December 23:59, and the ISO week-based answer names
     * that instant for the January after it: "Akkumuleret EfS 2004" for the 2003
     * volume, in its title, in its file name, and therefore in the public download
     * link it is cited by.
     *
     * Imported annuals previously took their year from the start of the public
     * window rather than from the cut-off. That happens to agree for an in-force
     * list, whose window opens inside the year it describes, and disagrees for
     * every accumulated one loaded years late.
     */
    @Test
    public void anAnnualIsNumberedForTheCalendarYearItsCutoffFallsIn() {
        assertEquals(Integer.valueOf(2018), issue(EFS_A_2018).getYear());
        assertEquals(Integer.valueOf(2024), issue(EFS_A_2024).getYear());
        assertEquals(Integer.valueOf(2017), issue(EFS_A_2017_SECOND_EDITION).getYear(),
                "the second 2017 edition took effect in March 2017 and is the 2017 edition");
        assertEquals(Integer.valueOf(2022), issue(FIRING_2022_EDITED_NEXT_YEAR).getYear(),
                "edited in January 2023, but it is the 2022 edition");
        assertEquals(Integer.valueOf(2003), issue(ACCUMULATED_2003).getYear(),
                "what was published during 2003, loaded in 2016: the 2003 volume");
        assertEquals(Integer.valueOf(2018), issue(ACCUMULATED_2018_OPEN_ENDED).getYear(),
                "closes at the end of 2018, so the ISO week-based year would name it 2019");
    }

    /**
     * An unreleased annual is numbered from its NOMINAL close, exactly as a
     * natively created open issue is.
     *
     * It has no cut-off stamp -- nobody has published it -- but it does have a
     * period, and the period's end is what the edition will be called. Leaving it
     * blank until publish would show the one row an admin is about to work on as
     * the only unnumbered row in its series.
     */
    @Test
    public void anUnreleasedAnnualIsNumberedFromItsNominalClose() {
        PublicationIssue firing2027 = issue(FIRING_2027_OPEN);
        assertNull(firing2027.getCutoffStampedAt(), "nobody published it");
        assertNotNull(firing2027.getYear(), "an open issue with a period still has a year");
        assertEquals(Integer.valueOf(firing2027.effectiveCutoff().toInstant()
                        .atZone(firing2027.getSeries().cutoffZone()).getYear()),
                firing2027.getYear(),
                "the year of an annual is the calendar year its period closes in");
    }
}
