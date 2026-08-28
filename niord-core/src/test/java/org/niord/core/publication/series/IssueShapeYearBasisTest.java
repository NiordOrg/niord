package org.niord.core.publication.series;

import org.junit.jupiter.api.Test;
import org.niord.core.publication.series.resolve.IssueNaming;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Which year a publication's ${year} token means. Pure, no database.
 *
 * The mapping lives beside the other publication rules rather than inside the
 * naming code, because it is a statement about what a series IS: a week-numbered
 * publication pairs its ISO week with the ISO week-year, and everything else is
 * named for the calendar year its period falls in.
 */
public class IssueShapeYearBasisTest {

    private static PublicationSeries series(NumberingScheme scheme, CutoffDefault cutoffDefault) {
        PublicationSeries s = new PublicationSeries();
        s.setNumberingScheme(scheme);
        s.setCutoffDefault(cutoffDefault);
        return s;
    }

    /** The weekly shape: ISO week, ISO week-year. */
    @Test
    public void aWeekNumberedSeriesTakesTheIsoWeekYear() {
        assertEquals(IssueNaming.YearBasis.ISO_WEEK_YEAR,
                IssueShape.yearBasisOf(series(NumberingScheme.ISO_WEEK_YEAR, CutoffDefault.RELEASE_MOMENT)));
    }

    /**
     * Every other scheme takes the calendar year.
     *
     * A year-and-edition publication, a month-and-year one, a bare sequence and a
     * publication that numbers nothing at all are none of them named for a week,
     * so pairing them with a week-based year names some of them for the wrong
     * year at the turn of every December.
     */
    @Test
    public void everyOtherSchemeTakesTheCalendarYear() {
        for (NumberingScheme scheme : new NumberingScheme[]{
                NumberingScheme.YEAR_EDITION, NumberingScheme.MONTH_YEAR,
                NumberingScheme.EDITION_SEQUENCE, NumberingScheme.NONE}) {
            assertEquals(IssueNaming.YearBasis.CALENDAR_YEAR,
                    IssueShape.yearBasisOf(series(scheme, CutoffDefault.RELEASE_MOMENT)),
                    scheme + " is not numbered by week and must not take the ISO week-year");
        }
    }

    /**
     * A cut-off that falls on a period BOUNDARY settles it on its own.
     *
     * The two are asked in this order deliberately. A series cut off at the start
     * or the end of its period is by definition numbered by that period, whatever
     * scheme somebody typed on the form -- and this is exactly the annual shape
     * where the ISO answer names the edition for the January after it.
     */
    @Test
    public void aPeriodBoundaryCutoffTakesTheCalendarYearWhateverTheScheme() {
        assertEquals(IssueNaming.YearBasis.CALENDAR_YEAR,
                IssueShape.yearBasisOf(series(NumberingScheme.ISO_WEEK_YEAR, CutoffDefault.PERIOD_END)));
        assertEquals(IssueNaming.YearBasis.CALENDAR_YEAR,
                IssueShape.yearBasisOf(series(NumberingScheme.ISO_WEEK_YEAR, CutoffDefault.PERIOD_START)));
    }

    /** An unstated scheme keeps the pairing a week needs, and a null series too. */
    @Test
    public void anUnstatedSchemeKeepsTheIsoPairing() {
        assertEquals(IssueNaming.YearBasis.ISO_WEEK_YEAR,
                IssueShape.yearBasisOf(series(null, CutoffDefault.RELEASE_MOMENT)));
        assertEquals(IssueNaming.YearBasis.ISO_WEEK_YEAR, IssueShape.yearBasisOf(null));
    }
}
