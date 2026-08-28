package org.niord.core.publication.series;

import org.junit.jupiter.api.Test;
import org.niord.core.publication.series.resolve.IssueNaming;

import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Invariants whose enforcement shipped but whose assertion did not.
 *
 * X-6 and I-15 were each left pending on a task. Both tasks completed; both
 * rules went unasserted, so the code was written and then never held to what it
 * was written for. Closing that is the whole of the invariant-binding pass, and it is why the plan
 * forbids deferring it to Phase Z: a pending binding that survives into cutover
 * is a rule nothing checks.
 *
 * No database -- both are properties of pure functions.
 */
public class LateBoundInvariantsTest {

    private static final ZoneId CPH = ZoneId.of("Europe/Copenhagen");

    private static Date at(int year, int month, int day, int hour) {
        return Date.from(ZonedDateTime.of(year, month, day, hour, 0, 0, 0, CPH).toInstant());
    }

    // ------------------------------------------------------------------- X-6

    /**
     * Neither publication root may resolve under the repository root.
     *
     * The repository is what `/rest/repo/file` serves. An archive root underneath
     * it would publish every superseded edition; a PREVIEW root underneath it
     * would publish issues that are still OPEN, whose contents are not decided
     * yet. The second is the worse of the two, which is why the rule names both
     * roots rather than just the archive.
     *
     * Asserted as the containment relation, which is what
     * PublicationPathService.assertOutsideRepository checks at startup.
     */
    @BindsRule({"X-6"})
    @Test
    public void neitherPublicationRootMayLiveInsideTheRepository() {
        Path repo = Path.of("/srv/niord/repo").toAbsolutePath().normalize();

        // The shapes that must be refused, including the one that only looks
        // outside until it is normalised.
        for (String inside : new String[] {
                "/srv/niord/repo/publications/archive",
                "/srv/niord/repo/publications/preview",
                "/srv/niord/repo/../repo/archive"}) {
            assertTrue(Path.of(inside).toAbsolutePath().normalize().startsWith(repo),
                    inside + " resolves inside the repository and must be refused at startup");
        }

        for (String outside : new String[] {
                "/srv/niord/publication-archive",
                "/srv/niord/publication-preview"}) {
            assertFalse(Path.of(outside).toAbsolutePath().normalize().startsWith(repo),
                    outside + " is a legal root");
        }

        assertNotEquals(Path.of("/srv/niord/publication-archive"),
                Path.of("/srv/niord/publication-preview"),
                "the roots are distinct, so one being legal does not make the other legal");
    }

    // ------------------------------------------------------------------ I-15

    /**
     * week / year come from the ISO week-YEAR, not the calendar year.
     *
     * This is the part that goes wrong quietly. 1 January 2017 falls in ISO week
     * 52 of week-year 2016, so a calendar year would label the issue that opens
     * 2017 as "week 52, 2017" -- a week that has not happened yet. The reverse
     * trap is 30 December 2019, which is already week 1 of 2020.
     */
    @BindsRule({"I-15"})
    @Test
    public void theWeekAndYearComeFromTheIsoWeekYearNotTheCalendarYear() {
        IssueNaming.Numbers newYear = IssueNaming.derive(at(2017, 1, 1, 12), null, CPH, null);
        assertEquals(52, newYear.week(), "1 Jan 2017 is in ISO week 52");
        assertEquals(2016, newYear.year(),
                "and in week-year 2016; the calendar year would name a week that has not happened");

        IssueNaming.Numbers lateDecember = IssueNaming.derive(at(2019, 12, 30, 12), null, CPH, null);
        assertEquals(1, lateDecember.week(), "30 Dec 2019 is already ISO week 1");
        assertEquals(2020, lateDecember.year(), "of week-year 2020 -- the same trap, reversed");
    }

    /** The zone is the series domain's, so a cut-off can fall on either side of midnight. */
    @BindsRule({"I-15"})
    @Test
    public void theWeekIsComputedInTheSeriesZoneNotUtc() {
        // 23:30 UTC on a Sunday is already Monday in Copenhagen, so the two zones
        // disagree about which ISO week the cut-off belongs to.
        Date sundayLateUtc = Date.from(ZonedDateTime.of(2024, 1, 7, 23, 30, 0, 0,
                ZoneId.of("UTC")).toInstant());

        assertEquals(1, IssueNaming.derive(sundayLateUtc, null, ZoneId.of("UTC"), null).week(),
                "in UTC it is still Sunday of week 1");
        assertEquals(2, IssueNaming.derive(sundayLateUtc, null, CPH, null).week(),
                "in Copenhagen it is Monday, so week 2 -- reading the wrong zone misnames the issue");
    }

    /**
     * A window spanning two cadence PERIODS yields weekTo; a single period does
     * not -- even though every ordinary Wednesday-to-Wednesday window straddles
     * two ISO weeks. An issue is named for the week it closed in, and only a
     * double week carries both numbers.
     */
    @BindsRule({"I-15"})
    @Test
    public void aspanningWindowGetsAWeekToAndASingleWeekDoesNot() {
        IssueNaming.Numbers spanning =
                IssueNaming.derive(at(2026, 1, 14, 12), at(2025, 12, 31, 12), CPH, null);
        assertEquals(2, spanning.week(), "the first week this issue closed opens the range");
        assertEquals(3, spanning.weekTo(), "and the cut-off week closes it");

        IssueNaming.Numbers ordinary =
                IssueNaming.derive(at(2026, 1, 14, 12), at(2026, 1, 7, 12), CPH, null);
        assertEquals(3, ordinary.week(), "an ordinary week is named for the week it closed in");
        assertNull(ordinary.weekTo(),
                "a single period is not a range, however many ISO weeks its window touches");

        IssueNaming.Numbers single =
                IssueNaming.derive(at(2026, 1, 14, 12), at(2026, 1, 12, 12), CPH, null);
        assertNull(single.weekTo(),
                "a window inside one week is not a range, and naming it 'uge 3+3' would be absurd");
    }
}
