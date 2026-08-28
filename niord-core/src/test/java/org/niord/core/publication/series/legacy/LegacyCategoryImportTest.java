package org.niord.core.publication.series.legacy;

import org.junit.jupiter.api.Test;
import org.niord.core.publication.Publication;
import org.niord.core.publication.PublicationCategory;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The categories the estate actually carries, and the flag that decides
 * who can see what.
 *
 * A category is a label with a priority and a publish flag, and the publish flag
 * is the whole reason this task is not merely mechanical: two of the five live
 * categories carry publish = false, and the four publications in them are
 * WITHHELD from /public/v1 on purpose. A category imported with the flag flipped
 * publishes documents somebody decided not to publish, and nothing about the
 * result announces itself -- the rows look ordinary and the site simply shows
 * more than it should.
 *
 * Pure: the shape is asserted against the captured estate, so it keeps running
 * on a build machine with no database. The behaviour that needs one -- that an
 * import leaves an existing category's flags alone -- is in LegacyImportServiceTest.
 */
public class LegacyCategoryImportTest {

    /** Every category referenced by the estate, keyed by id, first sighting wins. */
    private static Map<String, PublicationCategory> categoriesOfEstate() {
        Map<String, PublicationCategory> out = new LinkedHashMap<>();
        for (Publication p : LegacyEstateFixture.publications()) {
            PublicationCategory c = p.getCategory();
            if (c != null && c.getCategoryId() != null) {
                out.putIfAbsent(c.getCategoryId(), c);
            }
        }
        return out;
    }

    /**
     * Five categories, and they are these five.
     *
     * Pinned by id rather than counted, because a count agrees with itself while
     * naming the wrong rows. If the capture changes, this says which one moved.
     */
    @Test
    public void theEstateCarriesExactlyTheFiveLiveCategories() {
        Set<String> ids = new LinkedHashSet<>(categoriesOfEstate().keySet());

        assertEquals(Set.of(
                        "dk-dma-weekly-nm-publications",
                        "dk-dma-publications",
                        "dk-dma-nm-annex",
                        "dk-dma-internal-publications",
                        "dk-external-publications"),
                ids,
                "the five live category ids must round-trip; a sixth means the capture changed "
                        + "and the acceptance was written against a different estate");
    }

    /** Priority travels verbatim: it is the display order of the public list. */
    @Test
    public void everyCategoryKeepsItsPriority() {
        Map<String, Integer> expected = Map.of(
                "dk-dma-weekly-nm-publications", 10,
                "dk-dma-nm-annex", 20,
                "dk-dma-publications", 50,
                "dk-dma-internal-publications", 60,
                "dk-external-publications", 100);

        categoriesOfEstate().forEach((id, c) ->
                assertEquals(expected.get(id), c.getPriority(), id + " changed priority"));
    }

    /**
     * Two categories are withheld, and exactly four publications sit behind them.
     *
     * The number matters as much as the flag. If the flag were dropped or
     * inverted at import, these four would appear on /public/v1 -- and they are
     * the internal and external-link publications, which is to say the ones
     * somebody deliberately kept off it.
     */
    @Test
    public void thetwoWithheldCategoriesHideExactlyFourPublications() {
        Set<String> withheld = new LinkedHashSet<>();
        categoriesOfEstate().forEach((id, c) -> {
            if (!c.isPublish()) {
                withheld.add(id);
            }
        });

        assertEquals(Set.of("dk-dma-internal-publications", "dk-external-publications"), withheld,
                "these two are withheld from the public list on purpose");

        long hidden = LegacyEstateFixture.publications().stream()
                .filter(p -> p.getCategory() != null && withheld.contains(p.getCategory().getCategoryId()))
                .count();

        assertEquals(4, hidden,
                "four publications are withheld; importing their category with publish = true would "
                        + "put all four on the public site and nothing would say so");
    }

    /** And the other three are published, so the split is a split rather than a blanket. */
    @Test
    public void thepublishedCategoriesAreStillPublished() {
        Map<String, PublicationCategory> categories = categoriesOfEstate();
        for (String id : new String[] {
                "dk-dma-weekly-nm-publications", "dk-dma-nm-annex", "dk-dma-publications" }) {
            assertTrue(categories.get(id).isPublish(), id + " must stay on the public list");
        }
        assertFalse(categories.get("dk-dma-internal-publications").isPublish());
        assertFalse(categories.get("dk-external-publications").isPublish());
    }

    /**
     * The estate creates NO categories at import, and that is structural.
     *
     * A legacy publication holds its category by foreign key, so a category it
     * references necessarily exists as a row and resolves. The auto-create branch
     * the acceptance names is therefore unreachable from any real estate -- worth
     * stating, because a reader seeing categoriesCreated = 0 in the import report
     * would otherwise reasonably wonder whether the step ran at all.
     */
    @Test
    public void noCategoryNeedsCreatingBecauseTheForeignKeyGuaranteesItExists() {
        for (Publication p : LegacyEstateFixture.publications()) {
            if (p.getCategory() == null) {
                continue;
            }
            assertTrue(p.getCategory().getCategoryId() != null
                            && !p.getCategory().getCategoryId().isBlank(),
                    "a publication referencing a category with no id would be the one case that "
                            + "forces the importer to invent one");
        }
    }
}
