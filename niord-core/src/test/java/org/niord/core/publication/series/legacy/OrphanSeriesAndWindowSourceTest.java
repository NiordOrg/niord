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
            PublicationIssue issue = LegacyIssueTranslation.translate(p, series, FROZEN, null);

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
                    LegacyIssueTranslation.translate(p, null, FROZEN, null).getPublicWindowSource(),
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
                LegacyIssueTranslation.translate(oneOff, null, FROZEN, null).getPublicWindowSource(),
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

    // ------------------------------------------------- one seriesId namespace

    /**
     * The templates and the one-offs share a namespace, and used not to.
     *
     * Template "Firing Practice Areas" and the 2016 publication of the same name
     * both authored "firing-practice-areas". Each passed its own uniqueness check,
     * because those checks were over two different sets that never met, and the
     * import died against the unique key a third of the way into the write with a
     * report that had said problems: [] minutes earlier.
     *
     * The 2016 row is now filed under that series rather than given a one-off, so
     * the planner no longer reaches this case with that pair. The guarantee is
     * asserted anyway: it holds for whatever the estate turns out to contain, and
     * a rule that only works because of today's grouping is not a rule.
     *
     * Asserted over the whole captured estate rather than a two-row fixture: a
     * hand-built pair proves the mechanism, but only the real estate proves the
     * two names actually collide, which is the part that was missed.
     */
    @Test
    public void noOrphanTakesAnIdATemplateHasAlreadyClaimed() {
        Set<String> templateIds = new java.util.LinkedHashSet<>();
        for (Publication t : LegacyEstateFixture.templates()) {
            LegacySeriesTranslation.authorSeriesId(t, templateIds);
        }
        assertTrue(templateIds.contains("firing-practice-areas"),
                "the estate really does have a template that authors this id; if this ever fails the "
                        + "test below has stopped proving anything");

        Map<String, String> ids =
                LegacySeriesTranslation.authorOrphanSeriesIds(orphans(), templateIds);

        for (Map.Entry<String, String> e : ids.entrySet()) {
            assertFalse(templateIds.contains(e.getValue()),
                    "publication " + e.getKey() + " authors '" + e.getValue()
                            + "', which a template already holds");
        }
    }

    /** A one-off whose name is taken escalates -- and does not lose its identity doing it. */
    @Test
    public void aTakenNameEscalatesRatherThanBlockingTheImport() {
        Map<String, String> free = LegacySeriesTranslation.authorOrphanSeriesIds(orphans());
        Map<String, String> constrained = LegacySeriesTranslation.authorOrphanSeriesIds(
                orphans(), Set.of("firing-practice-areas"));

        String clashing = null;
        for (Map.Entry<String, String> e : free.entrySet()) {
            if ("firing-practice-areas".equals(e.getValue())) {
                clashing = e.getKey();
            }
        }
        assertTrue(clashing != null, "the estate has an orphan authoring this id");

        assertFalse("firing-practice-areas".equals(constrained.get(clashing)),
                "the orphan yields the readable name to the template");
        assertTrue(constrained.get(clashing).startsWith("firing-practice-areas"),
                "and keeps it as a prefix, so the row is still recognisable in the DRAFT list");

        for (Map.Entry<String, String> e : free.entrySet()) {
            if (!e.getKey().equals(clashing)) {
                assertEquals(e.getValue(), constrained.get(e.getKey()),
                        "escalating one name must not disturb the others");
            }
        }

        assertEquals(constrained, LegacySeriesTranslation.authorOrphanSeriesIds(
                        orphans().reversed(), Set.of("firing-practice-areas")),
                "and the result cannot depend on the order the rows arrive in");
    }

    /**
     * Every id the import plans is distinct: templates, ruled series, one-offs.
     *
     * The three are authored by three different routines, which is exactly why
     * this is asserted over their union rather than inside any one of them.
     */
    @Test
    public void everyPlannedIdAcrossTheWholeEstateIsDistinct() {
        Set<String> all = templateIdsOf();
        int templates = all.size();

        for (String ruled : RULED_SERIES_IDS) {
            assertTrue(all.add(ruled), "ruled series id '" + ruled + "' is already taken");
        }

        List<Publication> standalone = orphans().stream()
                .filter(o -> LegacyOrphanGrouping.placeOf(o).kind()
                        == LegacyOrphanGrouping.Destination.OWN_SERIES)
                .toList();
        Map<String, String> ids =
                LegacySeriesTranslation.authorOrphanSeriesIds(standalone, all);

        for (Map.Entry<String, String> e : ids.entrySet()) {
            assertTrue(all.add(e.getValue()),
                    "publication " + e.getKey() + " authors '" + e.getValue()
                            + "', which is already claimed");
        }

        assertEquals(templates + RULED_SERIES_IDS.size() + standalone.size(), all.size(),
                "one id per planned series, and no two the same");
    }

    /** The three shared series B5-v names in words. */
    private static final List<String> RULED_SERIES_IDS =
            List.of("nm-annex-ncags", "nm-annex-ice-service", "danish-list-of-lights");

    private static Set<String> templateIdsOf() {
        Set<String> out = new java.util.LinkedHashSet<>();
        for (Publication t : LegacyEstateFixture.templates()) {
            LegacySeriesTranslation.authorSeriesId(t, out);
        }
        return out;
    }
}
