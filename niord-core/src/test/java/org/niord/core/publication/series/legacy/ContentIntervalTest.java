package org.niord.core.publication.series.legacy;

import org.junit.jupiter.api.Test;
import org.niord.core.publication.Publication;
import org.niord.core.publication.series.IntervalBoundSource;
import org.niord.core.publication.series.PublicationIssue;
import org.niord.core.publication.series.PublicationSeries;
import org.niord.core.publication.series.SeriesCadence;
import org.niord.core.publication.series.SeriesStatus;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.vo.PublicationStatus;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The imported CONTENT interval, which is not the public window.
 *
 * Nothing asserted on the interval at all before this class, which is why the
 * two defects it pins shipped: the importer wrote legacy's one window into both
 * pairs, so every tiling issue claimed the period AFTER the one it contains, and
 * every IN_FORCE_AT_CUTOFF issue got a lower bound the contract says it must not
 * have.
 *
 * The evidence for the tiling case is the frozen members -- the only witness
 * independent of both windows, because they were frozen from the legacy message
 * tag rather than derived from a date. Over 40 weekly-ntm issues spanning
 * 2017-2026, 93% of members fell in the PRECEDING period and 6% in the stated
 * one, consistent in every year.
 */
public class ContentIntervalTest {

    private static final Date FROZEN = at(2026, 8, 26);

    private static Date at(int y, int m, int d) {
        return Date.from(ZonedDateTime.of(y, m, d, 10, 0, 0, 0, ZoneId.of("UTC")).toInstant());
    }

    private static Publication release(Date from, Date to) {
        Publication p = new Publication();
        p.setPublicationId("p-" + (from == null ? "x" : from.getTime()));
        p.setStatus(PublicationStatus.ACTIVE);
        p.setPublishDateFrom(from);
        p.setPublishDateTo(to);
        return p;
    }

    private static PublicationSeries series(TimeRelation relation, SeriesCadence cadence) {
        PublicationSeries s = new PublicationSeries();
        s.setSeriesId("weekly-ntm");
        s.setStatus(SeriesStatus.DRAFT);
        s.setTimeRelation(relation);
        s.setCadence(cadence);
        return s;
    }

    // ------------------------------------------------------------ the tiling case

    /**
     * A weekly issue covers the week BEFORE it was published, not the week after.
     *
     * The one that shipped wrong. An EfS released on Wednesday the 19th carries
     * the notices of the week ending that Wednesday; the 19th-to-26th window is
     * when the document is on the site. Taking the public window as the interval
     * moved every issue's stated content forward by a whole period.
     */
    @Test
    public void aTilingIssueCoversThePeriodThatClosedWhenItWasPublished() {
        PublicationSeries weekly = series(TimeRelation.PUBLISHED_IN_INTERVAL, SeriesCadence.WEEKLY);
        Publication previous = release(at(2026, 8, 12), at(2026, 8, 19));
        Publication current = release(at(2026, 8, 19), at(2026, 8, 26));

        PublicationIssue issue = LegacyIssueTranslation.translate(current, weekly, FROZEN, previous);

        assertEquals(at(2026, 8, 12), issue.getIntervalFrom(),
                "the period opened at the PREVIOUS release");
        assertEquals(at(2026, 8, 19), issue.getIntervalTo(),
                "and closed at this one -- issues tile, so one closes where the next opens");

        // The public window is untouched and still says something different.
        assertEquals(at(2026, 8, 19), issue.getPublicFrom());
        assertEquals(at(2026, 8, 26), issue.getPublicTo());
    }

    /**
     * The interval is taken from the chain, not by subtracting a nominal period.
     *
     * A week nobody published leaves a two-week gap between releases. Stepping
     * back one nominal period would bound the issue at a date no release happened
     * on, and would leave the skipped week belonging to nothing at all.
     */
    @Test
    public void aSkippedReleaseProducesOneLongIntervalRatherThanAPhantomOne() {
        PublicationSeries weekly = series(TimeRelation.PUBLISHED_IN_INTERVAL, SeriesCadence.WEEKLY);
        Publication previous = release(at(2026, 8, 5), at(2026, 8, 12));
        Publication current = release(at(2026, 8, 19), at(2026, 8, 26));

        PublicationIssue issue = LegacyIssueTranslation.translate(current, weekly, FROZEN, previous);

        assertEquals(at(2026, 8, 5), issue.getIntervalFrom(),
                "two weeks of content, because two weeks of content is what it carries");
        assertEquals(at(2026, 8, 19), issue.getIntervalTo());
    }

    /**
     * The oldest issue of a chain keeps a NULL lower bound.
     *
     * Nothing records when the oldest imported issue began collecting. Inventing
     * a bound for it is the same move that produced this defect, and the model
     * already allows the null.
     */
    @Test
    public void theHeadOfAChainHasNoLowerBoundRatherThanAnInventedOne() {
        PublicationSeries weekly = series(TimeRelation.PUBLISHED_IN_INTERVAL, SeriesCadence.WEEKLY);

        PublicationIssue issue = LegacyIssueTranslation.translate(
                release(at(2017, 1, 6), at(2017, 1, 13)), weekly, FROZEN, null);

        assertNull(issue.getIntervalFrom(), "nothing witnesses when the oldest issue opened");
        assertNull(issue.getIntervalFromSource(), "and no source is claimed for a bound that is absent");
        assertEquals(at(2017, 1, 6), issue.getIntervalTo());
    }

    // --------------------------------------------------------- the in-force case

    /**
     * An IN_FORCE_AT_CUTOFF issue has NO lower bound. 528 of them had one.
     *
     * It carries whatever was still in force at its cut-off, however old -- the
     * 2027 firing areas legitimately contain a 2016 notice -- so a lower bound is
     * not merely unknown, it is a claim about the content that is false. The
     * contract states this outright and the estate has exactly 531 such issues.
     */
    @Test
    public void anInForceIssueGetsNoLowerBoundBecauseItHasNone() {
        PublicationSeries inForce = series(TimeRelation.IN_FORCE_AT_CUTOFF, SeriesCadence.YEARLY);

        PublicationIssue issue = LegacyIssueTranslation.translate(
                release(at(2027, 1, 1), at(2027, 12, 31)), inForce, FROZEN,
                release(at(2026, 1, 1), at(2026, 12, 31)));

        assertNull(issue.getIntervalFrom(),
                "an in-force issue reaches back as far as its content does, which is not a period");
        assertEquals(at(2027, 1, 1), issue.getIntervalTo(),
                "intervalTo is the cut-off it was frozen at, not the end of a window");
    }

    // ------------------------------------------------------------- the one-off

    /** A one-off has no preceding period, so its window stands as its interval. */
    @Test
    public void aOneOffKeepsItsWindowBecauseItHasNoPeriodToStepBack() {
        PublicationSeries oneOff = series(TimeRelation.PUBLISHED_IN_INTERVAL, SeriesCadence.NONE);
        Publication only = release(at(2022, 1, 1), at(2022, 12, 31));

        PublicationIssue issue = LegacyIssueTranslation.translate(only, oneOff, FROZEN, null);

        assertEquals(at(2022, 1, 1), issue.getIntervalFrom());
        assertEquals(at(2022, 12, 31), issue.getIntervalTo());
    }

    // ------------------------------------------------------------- provenance

    /**
     * Every imported bound says it was RECOVERED, never STAMPED.
     *
     * None of it was recorded -- all of it is read back out of the release chain
     * -- and the marker is what tells a reader deciding whether to trust an
     * imported interval that nobody wrote it down.
     */
    @Test
    public void everyImportedBoundIsMarkedRecoveredRatherThanRecorded() {
        PublicationSeries weekly = series(TimeRelation.PUBLISHED_IN_INTERVAL, SeriesCadence.WEEKLY);

        PublicationIssue issue = LegacyIssueTranslation.translate(
                release(at(2026, 8, 19), at(2026, 8, 26)), weekly, FROZEN,
                release(at(2026, 8, 12), at(2026, 8, 19)));

        assertSame(IntervalBoundSource.RECOVERED, issue.getIntervalFromSource());
        assertEquals(IntervalBoundSource.RECOVERED.name(), issue.getIntervalToSource());
    }

    /**
     * The interval never equals the public window on a tiling issue.
     *
     * The shape of the original defect, asserted directly: if these two ever
     * coincide again for a cadenced tiling series, the content interval has been
     * overwritten by the visibility window a second time.
     */
    @Test
    public void aTilingIssuesIntervalIsNeverJustItsPublicWindow() {
        PublicationSeries weekly = series(TimeRelation.PUBLISHED_IN_INTERVAL, SeriesCadence.WEEKLY);

        for (int week = 1; week <= 6; week++) {
            Publication previous = release(at(2026, 3, week), at(2026, 3, week + 1));
            Publication current = release(at(2026, 3, week + 1), at(2026, 3, week + 2));

            PublicationIssue issue = LegacyIssueTranslation.translate(current, weekly, FROZEN, previous);

            assertEquals(issue.getIntervalTo(), issue.getPublicFrom(),
                    "the content closes exactly when the edition goes public");
            org.junit.jupiter.api.Assertions.assertNotEquals(
                    issue.getPublicTo(), issue.getIntervalTo(),
                    "week " + week + ": the interval has been overwritten by the public window again");
        }
    }
}
