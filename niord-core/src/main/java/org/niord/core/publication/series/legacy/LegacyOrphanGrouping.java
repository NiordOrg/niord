package org.niord.core.publication.series.legacy;

import org.niord.core.publication.Publication;
import org.niord.core.publication.PublicationDesc;

import java.util.List;

/**
 * Where each template-less publication belongs. Ruling B5-v, revised.
 *
 * The first version gave all 39 their own one-off series, because nothing in the
 * plan said which of them were really the same publication and inventing an
 * answer would have been a guess dressed as a migration. Rasmus has now supplied
 * the answer, so the guess is replaced by a ruling and 39 series become 9.
 *
 * CLASSIFYING BY TITLE HERE IS NOT WHAT B5.4a FORBIDS. That rule is about
 * IDENTITY -- never key an issue on its title, because three publications carry a
 * literal ${year} in title, fileName and tag name, and two were released with it.
 * Identity is untouched: every issue still takes its publicId from the legacy
 * publicationId, verbatim. What the title decides here is only which series an
 * issue is filed under, and that is a judgement Rasmus made and this records.
 *
 * The judgement, in his words:
 *
 *   - all 11 NCAGS annexes are one series, in "Annexes - Notices to Mariners"
 *   - all 8 ice-service annexes are one series, in the same category
 *   - the double-week issues belong to the weekly series they were hacked out of
 *   - "Danish List of Lights" is one series with four issues, one still active
 *   - the 2016 Firing Practice Areas annex is that series' earliest issue
 *   - the remaining five are genuinely standalone
 *
 * The double weeks matter beyond tidiness: having to hand-assemble them was one
 * of the drivers for this rewrite. Filing them under the weekly series is what
 * lets the new model express them at all.
 */
public final class LegacyOrphanGrouping {

    /** Where a publication goes. */
    public enum Destination {
        /** Its own one-off series, as before. */
        OWN_SERIES,
        /** A series shared with its siblings, created by the import. */
        SHARED_SERIES,
        /** A series that already exists, translated from a legacy template. */
        EXISTING_SERIES
    }

    /**
     * The placement, and why.
     *
     * seriesId is the authored id for SHARED_SERIES, and the legacyTemplateId of
     * the destination for EXISTING_SERIES -- two different keys, which is why the
     * kind travels with it rather than being inferred from the shape of the id.
     */
    public record Placement(Destination kind, String seriesId, String categoryId, String note) {
    }

    /** "Annexes - Notices to Mariners". */
    public static final String ANNEX_CATEGORY = "dk-dma-nm-annex";

    /** The templates the double-week issues belong to. */
    public static final String WEEKLY_NTM_TEMPLATE = "a8e661ee-49b8-45ea-a176-952e99253fec";
    public static final String WEEKLY_PT_TEMPLATE = "11262933-2e62-4d16-a498-39e238467fa6";

    /** The annual "Firing Practice Areas" annex, whose series runs 2017-2027. */
    public static final String FIRING_PRACTICE_TEMPLATE = "51387a4e-8f9b-46b0-afb6-43b71c62d9bb";

    private LegacyOrphanGrouping() {
    }

    /** Decides where one template-less publication belongs. */
    public static Placement placeOf(Publication orphan) {
        String title = titleOf(orphan);

        if (matches(title, "NCAGS")) {
            return new Placement(Destination.SHARED_SERIES, "nm-annex-ncags", ANNEX_CATEGORY,
                    "one of the 11 NCAGS annexes, which are one series (B5-v)");
        }
        if (matches(title, "istjeneste") || matches(title, "Marinestaben")) {
            return new Placement(Destination.SHARED_SERIES, "nm-annex-ice-service", ANNEX_CATEGORY,
                    "one of the 8 ice-service annexes, which are one series (B5-v)");
        }

        // The double weeks. "EfS 51-52 2016" is the Danish naming of the same
        // thing as "NtM Week 51-52", so both land on the weekly NtM series.
        if (startsWith(title, "Active P&T Week")) {
            return new Placement(Destination.EXISTING_SERIES, WEEKLY_PT_TEMPLATE, null,
                    "a double week of the weekly P&T series, assembled by hand because the old model "
                            + "could not express it (B5-v)");
        }
        if (startsWith(title, "NtM Week") || startsWith(title, "EfS ")) {
            return new Placement(Destination.EXISTING_SERIES, WEEKLY_NTM_TEMPLATE, null,
                    "a double week of the weekly NtM series, assembled by hand because the old model "
                            + "could not express it (B5-v)");
        }

        if (matches(title, "Danish List of Lights")) {
            return new Placement(Destination.SHARED_SERIES, "danish-list-of-lights", null,
                    "one of the four Danish List of Lights editions, which are one series (B5-v)");
        }

        // The 2016 annex, from before the report template existed. Its series
        // runs 2017-2027 one issue a year and this is the year in front of it:
        // same English title, same dk-dma-nm-annex category, published as a LINK
        // to a hand-made PDF because there was nothing yet to generate one.
        //
        // Surfaced by the id namespace rather than by reading the estate -- the
        // template and this row both authored "firing-practice-areas", and asking
        // why turned a name clash into a publication that had lost its series.
        if (matches(title, "Firing Practice Areas")) {
            return new Placement(Destination.EXISTING_SERIES, FIRING_PRACTICE_TEMPLATE, null,
                    "the 2016 edition of the Firing Practice Areas annex, which predates the report "
                            + "template its later issues are generated from (B5-v)");
        }

        return new Placement(Destination.OWN_SERIES, null, null, "genuinely standalone (B5-v)");
    }

    /**
     * Which orphan supplies the shared series' own configuration.
     *
     * The NEWEST, because a series' settings are what its NEXT issue will follow,
     * and the newest issue is the closest thing to a statement of current intent.
     * Ties break on publicationId so the choice is total -- an unstable pick would
     * make the imported series' cadence depend on the order rows came back in.
     *
     * The per-issue snapshot is unaffected either way: B5.4a2 derives each
     * issue's own timeRelation and aliveAtCutoff from its own filter, which is
     * exactly why a series-level default can be chosen this simply.
     */
    public static Publication configurationSource(List<Publication> group) {
        return group.stream()
                .max((a, b) -> {
                    long left = a.getPublishDateFrom() == null ? 0 : a.getPublishDateFrom().getTime();
                    long right = b.getPublishDateFrom() == null ? 0 : b.getPublishDateFrom().getTime();
                    return left != right
                            ? Long.compare(left, right)
                            : a.getPublicationId().compareTo(b.getPublicationId());
                })
                .orElseThrow(() -> new IllegalArgumentException("a group cannot be empty"));
    }

    private static boolean matches(String title, String needle) {
        return title != null && title.toLowerCase().contains(needle.toLowerCase());
    }

    private static boolean startsWith(String title, String prefix) {
        return title != null && title.toLowerCase().startsWith(prefix.toLowerCase());
    }

    /** English first, then anything -- the same preference the id authoring uses. */
    private static String titleOf(Publication p) {
        if (p.getDescs() == null) {
            return "";
        }
        return p.getDescs().stream()
                .filter(d -> "en".equalsIgnoreCase(d.getLang()))
                .map(PublicationDesc::getTitle)
                .filter(s -> s != null && !s.isBlank())
                .findFirst()
                .orElseGet(() -> p.getDescs().stream()
                        .map(PublicationDesc::getTitle)
                        .filter(s -> s != null && !s.isBlank())
                        .findFirst().orElse(""));
    }
}
