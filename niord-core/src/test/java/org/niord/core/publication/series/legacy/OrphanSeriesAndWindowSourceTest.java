package org.niord.core.publication.series.legacy;

import org.junit.jupiter.api.Test;
import org.niord.core.publication.Publication;
import org.niord.core.publication.series.PublicWindowSource;
import org.niord.core.publication.series.PublicationIssue;
import org.niord.core.publication.series.PublicationSeries;
import org.niord.core.publication.series.SeriesCadence;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B5.4c: R8's window source, and the one-off series the orphans get (B5-v).
 *
 * Driven over the captured estate, because both rules were got wrong against it
 * before this suite existed: 9 cadenced rows were being marked MANUAL, and 8
 * authored ids were 95 characters against a varchar(64) column.
 */
public class OrphanSeriesAndWindowSourceTest {

    private static final Date FROZEN = new Date(1_755_000_000_000L);

    private static List<Publication> orphans() {
        return LegacyEstateFixture.publications().stream()
                .filter(p -> p.getTemplate() == null)
                .toList();
    }

    // -------------------------------------------------------------- B5.4c / R8

    /**
     * No row of anything cadenced carries MANUAL.
     *
     * Marking a cadenced imported issue MANUAL is what B2.3b step 13 skips by
     * design, and the first native publish would then leave two current EfS
     * issues on the public site at once.
     */
    @Test
    public void noCadencedIssueCarriesAManualWindow() {
        PublicationSeries weekly = new PublicationSeries();
        weekly.setCadence(SeriesCadence.WEEKLY);

        List<String> offenders = new ArrayList<>();
        for (Publication p : LegacyEstateFixture.publications()) {
            PublicationSeries series = p.getTemplate() == null ? null : weekly;
            PublicationIssue issue = LegacyIssueTranslation.translate(p, series, FROZEN);

            if (LegacyIssueTranslation.isCadenced(p, series)
                    && issue.getPublicWindowSource() != PublicWindowSource.DERIVED) {
                offenders.add(p.getPublicationId());
            }
        }
        assertTrue(offenders.isEmpty(),
                "R8: a cadenced issue must derive its window, or the first native publish leaves the "
                        + "imported predecessor uncapped on the public site: " + offenders);
    }

    /**
     * The 9 template-less WEEKLY rows are cadenced even with no series.
     *
     * They are the double-week issues, made by hand rather than from the weekly
     * template. Reading the cadence off the series alone marked every one of them
     * MANUAL, which is the defect this asserts against.
     */
    @Test
    public void theTemplatelessWeeklyRowsAreStillCadenced() {
        List<Publication> weeklyOrphans = orphans().stream()
                .filter(p -> p.getPeriodicalType() != null)
                .toList();

        assertEquals(9, weeklyOrphans.size(),
                "the captured estate holds 9 template-less rows with a cadence of their own");

        for (Publication p : weeklyOrphans) {
            assertEquals(PublicWindowSource.DERIVED,
                    LegacyIssueTranslation.translate(p, null, FROZEN).getPublicWindowSource(),
                    p.getPublicationId() + " is cadenced and must not be MANUAL");
        }
    }

    /** A genuine one-off, with no cadence anywhere, stays MANUAL. */
    @Test
    public void agenuineOneOffKeepsAManualWindow() {
        Publication oneOff = orphans().stream()
                .filter(p -> p.getPeriodicalType() == null)
                .findFirst().orElseThrow();

        assertEquals(PublicWindowSource.MANUAL,
                LegacyIssueTranslation.translate(oneOff, null, FROZEN).getPublicWindowSource(),
                "only a genuinely open-ended one-off is MANUAL");
    }

    // ---------------------------------------------------------------- B5-v

    /** Every orphan is authored an id, and they are unique. */
    @Test
    public void everyOrphanGetsItsOwnUniqueSeriesId() {
        Map<String, String> ids = LegacySeriesTranslation.authorOrphanSeriesIds(orphans());

        assertEquals(39, ids.size(), "the captured estate holds 39 template-less publications");
        assertEquals(ids.size(), new HashSet<>(ids.values()).size(),
                "two orphans sharing a seriesId would collide on a unique column");
    }

    /**
     * Every authored id fits the column.
     *
     * The ice-service annexes are the reason: their titles slug to 95 characters
     * against varchar(64), and MySQL in strict mode rejects rather than truncates,
     * so the import would have died on those eight rows.
     */
    @Test
    public void everyAuthoredIdFitsTheColumn() {
        List<String> tooLong = LegacySeriesTranslation.authorOrphanSeriesIds(orphans()).values().stream()
                .filter(id -> id.length() > LegacySeriesTranslation.MAX_SERIES_ID)
                .toList();

        assertTrue(tooLong.isEmpty(),
                "seriesId is varchar(64) and MySQL rejects rather than truncates: " + tooLong);
    }

    /** No authored id ends in a hyphen left behind by the cap. */
    @Test
    public void noAuthoredIdEndsInAStrandedHyphen() {
        LegacySeriesTranslation.authorOrphanSeriesIds(orphans()).values()
                .forEach(id -> assertFalse(id.endsWith("-"), id));
    }

    /**
     * Authoring is deterministic, which is what makes the import disposable.
     *
     * The whole grouping decision is reversible only because a re-import
     * reproduces the same ids; if authoring depended on iteration order, a
     * re-run after a regrouping would silently re-key the series that were not
     * regrouped.
     */
    @Test
    public void authoringIsDeterministic() {
        assertEquals(LegacySeriesTranslation.authorOrphanSeriesIds(orphans()),
                LegacySeriesTranslation.authorOrphanSeriesIds(orphans()));

        List<Publication> reversed = new ArrayList<>(orphans());
        java.util.Collections.reverse(reversed);
        assertEquals(LegacySeriesTranslation.authorOrphanSeriesIds(orphans()),
                LegacySeriesTranslation.authorOrphanSeriesIds(reversed),
                "the authored ids must not depend on the order the rows arrive in");
    }

    /**
     * A colliding group escalates WHOLLY, not partially.
     *
     * Three NCAGS rows share 2023. Naming one of them ncags-2023 and the other
     * two ncags-2023-<id> would make which-one-got-the-clean-name an artefact of
     * the query plan.
     */
    @Test
    public void acollidingGroupEscalatesTogether() {
        Set<String> ncags = new HashSet<>();
        for (Map.Entry<String, String> e
                : LegacySeriesTranslation.authorOrphanSeriesIds(orphans()).entrySet()) {
            if (e.getValue().startsWith("ncags-2023")) {
                ncags.add(e.getValue());
            }
        }
        assertEquals(3, ncags.size(), "all three 2023 NCAGS rows must be distinguished");
        ncags.forEach(id -> assertTrue(id.length() > "ncags-2023".length(),
                "one of the group kept the clean name: " + id));
    }
}
