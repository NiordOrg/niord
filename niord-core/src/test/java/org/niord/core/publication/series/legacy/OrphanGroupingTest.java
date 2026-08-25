package org.niord.core.publication.series.legacy;

import org.junit.jupiter.api.Test;
import org.niord.core.publication.Publication;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B5-v as revised: the 39 template-less publications are 8 series, not 39.
 *
 * The counts are Rasmus's ruling, checked against the captured estate. They are
 * asserted as exact figures rather than "more than one" because the ruling IS the
 * specification here -- there is no other document that says an NCAGS annex from
 * 2018 and one from 2026 are the same publication, and if a later edit quietly
 * splits them apart, the only thing that would notice is this test.
 */
public class OrphanGroupingTest {

    private static List<Publication> orphans() {
        return LegacyEstateFixture.publications().stream()
                .filter(p -> p.getTemplate() == null)
                .toList();
    }

    private static Map<String, Integer> placements() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Publication p : orphans()) {
            LegacyOrphanGrouping.Placement place = LegacyOrphanGrouping.placeOf(p);
            String key = place.kind() + (place.seriesId() == null ? "" : ":" + place.seriesId());
            out.merge(key, 1, Integer::sum);
        }
        return out;
    }

    /** The whole ruling, in one assertion. */
    @Test
    public void theThirtyNineOrphansAreNineSeries() {
        Map<String, Integer> placed = placements();

        assertEquals(11, placed.get("SHARED_SERIES:nm-annex-ncags"),
                "all 11 NCAGS annexes are one series");
        assertEquals(8, placed.get("SHARED_SERIES:nm-annex-ice-service"),
                "all 8 ice-service annexes are one series");
        assertEquals(4, placed.get("SHARED_SERIES:danish-list-of-lights"),
                "Danish List of Lights is one series with four editions");
        assertEquals(5, placed.get("EXISTING_SERIES:" + LegacyOrphanGrouping.WEEKLY_PT_TEMPLATE),
                "5 double weeks belong to the weekly P&T series");
        assertEquals(5, placed.get("EXISTING_SERIES:" + LegacyOrphanGrouping.WEEKLY_NTM_TEMPLATE),
                "5 double weeks belong to the weekly NtM series");
        assertEquals(1, placed.get("EXISTING_SERIES:" + LegacyOrphanGrouping.FIRING_PRACTICE_TEMPLATE),
                "the 2016 Firing Practice Areas annex joins the series that runs 2017-2027");
        assertEquals(5, placed.get("OWN_SERIES"),
                "five are genuinely standalone");

        assertEquals(39, placed.values().stream().mapToInt(Integer::intValue).sum(),
                "every template-less publication is placed; an unplaced one has no series and cannot "
                        + "be written, because PublicationIssue.series is NOT NULL");

        // 3 shared + 5 standalone = 8 series created; the 11 that join an
        // existing series create none.
        long created = placed.keySet().stream()
                .filter(k -> k.startsWith("SHARED_SERIES")).count() + placed.get("OWN_SERIES");
        assertEquals(8, created, "39 orphans become 8 series, not 39");
    }

    /**
     * The double weeks were the point.
     *
     * Having to hand-assemble them was one of the drivers for this rewrite, so
     * filing them under the weekly series is not tidying -- it is the thing the
     * new model exists to make expressible. A regression here would put them back
     * where they started, as orphans nobody can publish a successor to.
     */
    @Test
    public void everyDoubleWeekJoinsTheWeeklySeriesItWasHackedOutOf() {
        int joined = 0;
        for (Publication p : orphans()) {
            LegacyOrphanGrouping.Placement place = LegacyOrphanGrouping.placeOf(p);
            if (place.kind() != LegacyOrphanGrouping.Destination.EXISTING_SERIES) {
                continue;
            }
            if (place.seriesId().equals(LegacyOrphanGrouping.FIRING_PRACTICE_TEMPLATE)) {
                continue;
            }
            joined++;
            assertTrue(place.seriesId().equals(LegacyOrphanGrouping.WEEKLY_NTM_TEMPLATE)
                            || place.seriesId().equals(LegacyOrphanGrouping.WEEKLY_PT_TEMPLATE),
                    "a double week may only join one of the two weekly series");
            assertNull(place.categoryId(),
                    "an issue joining an existing series takes that series' category, and naming one "
                            + "here would let the two disagree");
        }
        assertEquals(10, joined);
    }

    /**
     * Exactly one publication is the 2016 Firing Practice Areas annex.
     *
     * The rule matches on title, and a title match that quietly caught a second
     * row would file something into a series nobody chose for it. One row was
     * ruled on, so one row is what the rule may claim.
     */
    @Test
    public void exactlyOnePublicationIsTheTwentySixteenFiringAnnex() {
        List<Publication> claimed = orphans().stream()
                .filter(o -> LegacyOrphanGrouping.FIRING_PRACTICE_TEMPLATE
                        .equals(LegacyOrphanGrouping.placeOf(o).seriesId()))
                .toList();

        assertEquals(1, claimed.size(), "the ruling is about one publication");
        assertEquals("f6ad2eda-2ab2-45d2-8184-c2f12c6a351f",
                claimed.get(0).getPublicationId(),
                "and it is the 2016 one -- named here so that a retitled row cannot silently "
                        + "inherit the ruling");
    }

    /** The annexes land in "Annexes - Notices to Mariners", as ruled. */
    @Test
    public void bothAnnexSeriesCarryTheAnnexCategory() {
        for (Publication p : orphans()) {
            LegacyOrphanGrouping.Placement place = LegacyOrphanGrouping.placeOf(p);
            if (place.seriesId() == null || !place.seriesId().startsWith("nm-annex-")) {
                continue;
            }
            assertEquals(LegacyOrphanGrouping.ANNEX_CATEGORY, place.categoryId(),
                    "the annex series are filed under Annexes - Notices to Mariners");
        }
    }

    /**
     * A shared series takes its settings from the group's NEWEST member.
     *
     * A series' configuration is what its NEXT issue follows, so the newest issue
     * is the closest thing to a statement of current intent. Deterministic, so a
     * re-import does not silently re-configure the series from a different row.
     */
    @Test
    public void asharedSeriesIsConfiguredFromItsNewestMember() {
        List<Publication> ncags = orphans().stream()
                .filter(p -> "nm-annex-ncags".equals(LegacyOrphanGrouping.placeOf(p).seriesId()))
                .toList();
        assertEquals(11, ncags.size());

        Publication source = LegacyOrphanGrouping.configurationSource(ncags);
        assertNotNull(source.getPublishDateFrom());

        for (Publication other : ncags) {
            if (other.getPublishDateFrom() != null) {
                assertTrue(!other.getPublishDateFrom().after(source.getPublishDateFrom()),
                        "the configuration source must be the newest member");
            }
        }
        assertEquals(source.getPublicationId(),
                LegacyOrphanGrouping.configurationSource(ncags.reversed()).getPublicationId(),
                "and must not depend on the order the group arrives in");
    }

    /**
     * Identity is untouched by any of this.
     *
     * B5.4a forbids keying an issue on its title, and classifying by title here
     * does not: every issue still takes its publicId from the legacy
     * publicationId. What the title decides is only which series files it.
     */
    @Test
    public void groupingByTitleDoesNotTouchTheIdSpace() {
        for (Publication p : orphans()) {
            assertEquals(p.getPublicationId(),
                    LegacyIssueTranslation.translate(p, null, new java.util.Date(0L), (java.util.Date) null).getPublicId(),
                    "the grouping decides the series, never the id");
        }
    }
}
