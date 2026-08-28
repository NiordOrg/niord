package org.niord.core.publication.series.resolve;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Naming and numbering. Pure, no database. */
public class IssueNamingTest {

    private static final ZoneId DK = ZoneId.of("Europe/Copenhagen");

    private static Date at(int year, int month, int day, int hour, int minute) {
        return Date.from(ZonedDateTime.of(year, month, day, hour, minute, 0, 0, DK).toInstant());
    }

    // ------------------------------------------------------- the year boundary

    /**
     * The ISO week-YEAR, not the calendar year.
     *
     * A cut-off on 31 December 2025 belongs to ISO week 1 of 2026. Legacy pairs
     * the correct week with the calendar year and produces "EfS uge 1 - 2025",
     * which names the issue for a year it is not in.
     */
    @Test
    public void aCutoffInIsoWeekOneOfTheNextYearTakesTheNextYear() {
        IssueNaming.Numbers n = IssueNaming.derive(at(2025, 12, 31, 10, 0), null, DK, null);
        assertEquals(1, n.week(), "31.12.2025 is in ISO week 1");
        assertEquals(2026, n.year(),
                "the ISO week-year is 2026; taking the calendar year gives the legacy 'uge 1 - 2025' bug");
    }

    /** And the other direction: early January belonging to the previous week-year. */
    @Test
    public void aCutoffInEarlyJanuaryCanBelongToThePreviousWeekYear() {
        // 1 January 2027 falls in ISO week 53 of 2026.
        IssueNaming.Numbers n = IssueNaming.derive(at(2027, 1, 1, 10, 0), null, DK, null);
        assertEquals(53, n.week());
        assertEquals(2026, n.year(), "1.1.2027 is in ISO week 53 of 2026");
    }

    @Test
    public void weekFiftyTwoIsOrdinary() {
        IssueNaming.Numbers n = IssueNaming.derive(at(2026, 12, 24, 10, 0), null, DK, null);
        assertEquals(52, n.week());
        assertEquals(2026, n.year());
    }

    // ----------------------------------------------------------- the multi-week case

    /**
     * A window spanning two cadence PERIODS -- the double week over a holiday --
     * is named for both weeks it closed. The from-week is the first week no
     * other issue closed, not the week the previous cut-off happened to fall in.
     */
    @Test
    public void aWindowSpanningTwoWeeksCarriesBothNumbers() {
        Date from = at(2026, 1, 1, 10, 0);   // the previous cut-off, in week 1
        Date to = at(2026, 1, 15, 10, 0);    // two weeks later, week 3
        IssueNaming.Numbers n = IssueNaming.derive(to, from, DK, null);

        assertEquals(2, n.week(), "the first week this issue closed");
        assertEquals(3, n.weekTo(), "the cut-off week");
        assertEquals("Uge 2+3, 2026", "Uge " + n.week() + "+" + n.weekTo() + ", " + n.year());
    }

    /**
     * An ORDINARY week straddles two ISO weeks too -- every Wednesday-to-Wednesday
     * window does -- and is named for the one it closed in, with no second number.
     * Naming it "2+3" was the defect: it made every week of the year read as a
     * double week.
     */
    @Test
    public void anOrdinaryWeekStraddlingTwoIsoWeeksIsNotADoubleWeek() {
        Date from = at(2026, 1, 8, 10, 0);   // the previous cut-off, in week 2
        Date to = at(2026, 1, 15, 10, 0);    // one period later, week 3
        IssueNaming.Numbers n = IssueNaming.derive(to, from, DK, null);

        assertEquals(3, n.week(), "named for the week it closed in");
        assertNull(n.weekTo(), "a single period carries no second number");
    }

    /**
     * The issue is named for the END of the window, never the start.
     *
     * Naming from the start produces "Uge 26" where production says "uge 27".
     */
    @Test
    public void aSingleWeekWindowIsNamedForItsCutoffNotItsStart() {
        Date from = at(2026, 6, 29, 10, 0);
        Date to = at(2026, 7, 3, 10, 0);     // still week 27
        IssueNaming.Numbers n = IssueNaming.derive(to, from, DK, null);

        assertEquals(27, n.week());
        assertNull(n.weekTo(), "a single-week window carries no second number");
    }

    // ----------------------------------------------------- the token vocabulary

    /** One expansion case per token. A token with no case means the menu can offer something untested. */
    @Test
    public void everyDeclaredTokenExpands() {
        // Week 7, so the padded variants are visibly different from the plain ones.
        IssueNaming.Numbers n = IssueNaming.derive(at(2026, 2, 12, 9, 5), null, DK, 3);
        Map<String, String> values = IssueNaming.valuesOf(n);

        assertEquals(IssueNaming.TOKENS, values.keySet(),
                "the value map and the declared vocabulary disagree; one of them is a second source of truth");

        assertEquals("7", values.get("week"));
        assertEquals("07", values.get("week-2-digits"), "the padded variant must pad");
        assertEquals("2026", values.get("year"));
        assertEquals("26", values.get("year-2-digits"));
        assertEquals("2", values.get("month"));
        assertEquals("02", values.get("month-2-digits"));
        assertEquals("12", values.get("day"));
        assertEquals("12", values.get("day-2-digits"));
        assertEquals("3", values.get("edition"));

        for (String token : IssueNaming.TOKENS) {
            String expanded = IssueNaming.expand("x${" + token + "}x", n);
            assertFalse(expanded.contains("${"), token + " did not expand");
        }
    }

    /** The padded variants are IN, and production proves they are used: nm-w01-2026. */
    @Test
    public void theZeroPaddedVariantsAreAvailable() {
        IssueNaming.Numbers week1 = IssueNaming.derive(at(2026, 1, 2, 10, 0), null, DK, null);
        assertEquals("nm-w01-2026",
                IssueNaming.expand("nm-w${week-2-digits}-${year}", week1),
                "without the padded token, a pattern that wants 'w01' is inexpressible");
    }

    /** DM-Q15: a DAILY series must be nameable, which is why the day tokens exist. */
    @Test
    public void aDailySeriesCanBeNamed() {
        IssueNaming.Numbers n = IssueNaming.derive(at(2026, 3, 5, 8, 0), null, DK, null);
        assertEquals("Dagens EfS 05.03.2026",
                IssueNaming.expand("Dagens EfS ${day-2-digits}.${month-2-digits}.${year}", n),
                "shipping a DAILY cadence with no way to name its issues is worse than either alternative");
    }

    // ------------------------------------------------------------------- S-14

    /**
     * Nothing of the form ${...} may survive expansion.
     *
     * Production serves a real PDF at .../Skydeomraader-%24%7Byear%7D.pdf today,
     * because an unexpanded token reached a file name and then a URL.
     */
    @Test
    public void anUnknownTokenFailsRatherThanSurvivingIntoTheOutput() {
        IssueNaming.Numbers n = IssueNaming.derive(at(2026, 2, 12, 9, 5), null, DK, null);

        IssueNaming.UnknownTokenException e = assertThrows(IssueNaming.UnknownTokenException.class,
                () -> IssueNaming.expand("Skydeomraader-${yeer}.pdf", n));
        assertEquals("yeer", e.token());
        assertEquals("UNKNOWN_TOKEN", e.code());

        assertFalse(IssueNaming.isExpandable("Skydeomraader-${yeer}.pdf"));
        assertTrue(IssueNaming.isExpandable("Skydeomraader-${year}.pdf"));
    }

    @Test
    public void aFullyExpandedPatternKeepsItsLiteralText() {
        IssueNaming.Numbers n = IssueNaming.derive(at(2026, 7, 8, 10, 0), null, DK, null);
        assertEquals("EfS-Uge-28-2026.pdf",
                IssueNaming.expand("EfS-Uge-${week}-${year}.pdf", n));
        assertEquals("EfS uge 28", IssueNaming.expand("EfS uge ${week}", n));
    }

    // ------------------------------------------------------------- the timezone

    /**
     * Derived in the series' timezone, never the JVM default. Around midnight the
     * two disagree about which day -- and therefore which week -- a cut-off is in.
     */
    @Test
    public void derivationUsesTheSeriesTimezone() {
        // 22:30 UTC on a Sunday is already Monday in Copenhagen, so the two zones
        // put this cut-off in different ISO weeks.
        Date cutoff = Date.from(ZonedDateTime.of(2026, 1, 4, 23, 30, 0, 0, ZoneId.of("UTC")).toInstant());

        IssueNaming.Numbers utc = IssueNaming.derive(cutoff, null, ZoneId.of("UTC"), null);
        IssueNaming.Numbers dk = IssueNaming.derive(cutoff, null, DK, null);

        assertEquals(1, utc.week(), "4.1.2026 23:30 UTC is still ISO week 1");
        assertEquals(2, dk.week(), "the same instant is 5.1 in Copenhagen, which is ISO week 2");
    }
    // ============================================ the deferred citation token

    /**
     * A citation format may carry ${parameters}; a file name may not.
     *
     * These are two different questions and one answer cannot serve both. Reject
     * ${parameters} for citations and no citable series can be saved at all --
     * S-14 fails the format and S-13 requires one. Accept it for file names and
     * the token reaches a public URL, which is how production came to serve a PDF
     * at .../Skydeomraader-%24%7Byear%7D.pdf.
     */
    @Test
    public void theCitationVocabularyAdmitsParametersAndTheStrictOneDoesNot() {
        String citation = "EfS ${week}/${year} ${parameters}";

        assertTrue(IssueNaming.isCitationExpandable(citation),
                "the canonical citation format was rejected; with S-13 also requiring a reference "
                        + "format, no citable series could be configured at all");
        assertFalse(IssueNaming.isExpandable(citation),
                "${parameters} was accepted by the strict vocabulary, so it can reach a file name "
                        + "and then a public URL");
    }

    /** Both vocabularies still reject a token that is in neither. */
    @Test
    public void neitherVocabularyAdmitsAnUnknownToken() {
        assertFalse(IssueNaming.isExpandable("EfS ${nope}"));
        assertFalse(IssueNaming.isCitationExpandable("EfS ${nope}"));
    }

    /** And expansion leaves the deferred token for the citation layer, expanding the rest. */
    @Test
    public void expandCitationLeavesOnlyTheDeferredToken() {
        IssueNaming.Numbers n = new IssueNaming.Numbers(33, null, 2017, 8, 15, null);

        assertEquals("EfS 33/2017 ${parameters}",
                IssueNaming.expandCitation("EfS ${week}/${year} ${parameters}", n));
    }

    /**
     * A single week released LATE is still one week. Production released several
     * weeks two to five days after their nominal close -- a window of ten or
     * twelve days -- and each is one issue named for the week it closed. Only a
     * window that swallowed most of a second period is a double week.
     */
    @Test
    public void aSingleWeekReleasedLateIsNotADoubleWeek() {
        Date from = at(2026, 7, 8, 9, 0);     // Wednesday, week 28 opened
        Date lateClose = at(2026, 7, 20, 9, 0); // released the Monday after next: twelve days
        IssueNaming.Numbers late = IssueNaming.derive(lateClose, from, DK, null);
        assertEquals(30, late.week(), "named for the week it closed in");
        assertNull(late.weekTo(), "twelve days is one late week, not two");

        Date doubleClose = at(2026, 7, 22, 9, 0); // a full second period: fourteen days
        IssueNaming.Numbers two = IssueNaming.derive(doubleClose, from, DK, null);
        assertEquals(29, two.week(), "the first week no other issue closed");
        assertEquals(30, two.weekTo());
    }

    // ------------------------------------------------------------- ${year}

    /**
     * A publication that is NOT numbered by week takes the calendar year.
     *
     * The ISO answer is right for a weekly issue and wrong for everything else,
     * and the annual editions are where it bites. The accumulated list closes at
     * 31 December 23:59 and is the edition FOR that year; the in-force edition
     * opens at 1 January and is the edition FOR that year. Under the ISO
     * week-year the first is named for the January after it, and the year is part
     * of the file name and therefore of the public download link -- so the
     * January-2027 edition would collide with the real 2026 one.
     */
    @Test
    public void anAnnualEditionIsNamedForTheYearItCloses() {
        Date newYearsEve = at(2025, 12, 31, 23, 59);
        assertEquals(2026, IssueNaming.derive(newYearsEve, null, DK, null,
                        IssueNaming.YearBasis.ISO_WEEK_YEAR).year(),
                "a week-numbered issue closing on 31.12.2025 is in ISO week-year 2026");
        assertEquals(2025, IssueNaming.derive(newYearsEve, null, DK, null,
                        IssueNaming.YearBasis.CALENDAR_YEAR).year(),
                "an annual edition closing on 31.12.2025 is the 2025 edition, not the 2026 one");

        // And the mirror, which is the file-name collision the review found: a
        // period-start cut-off on 1 January 2027 falls in ISO week 53 of 2026.
        Date newYearsDay = at(2027, 1, 1, 0, 0);
        assertEquals(2026, IssueNaming.derive(newYearsDay, null, DK, null,
                        IssueNaming.YearBasis.ISO_WEEK_YEAR).year(),
                "1.1.2027 falls in ISO week-year 2026");
        assertEquals(2027, IssueNaming.derive(newYearsDay, null, DK, null,
                        IssueNaming.YearBasis.CALENDAR_YEAR).year(),
                "the edition in force from 1.1.2027 is the 2027 edition");
    }

    /** The week is the ISO week either way; only the year token moves. */
    @Test
    public void theYearBasisDoesNotMoveTheWeek() {
        Date d = at(2025, 12, 31, 10, 0);
        assertEquals(IssueNaming.derive(d, null, DK, null, IssueNaming.YearBasis.ISO_WEEK_YEAR).week(),
                IssueNaming.derive(d, null, DK, null, IssueNaming.YearBasis.CALENDAR_YEAR).week());
    }

    /** The four-argument form keeps the ISO pairing, which is what a week needs. */
    @Test
    public void theDefaultBasisIsTheIsoWeekYear() {
        Date d = at(2025, 12, 31, 10, 0);
        assertEquals(IssueNaming.derive(d, null, DK, null, IssueNaming.YearBasis.ISO_WEEK_YEAR).year(),
                IssueNaming.derive(d, null, DK, null).year());
    }
}
