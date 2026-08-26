package org.niord.core.publication.series.legacy;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the legacy TEMPLATES do not say, supplied as a ruling rather than a guess.
 *
 * The sibling of {@link LegacyOrphanGrouping}, and it exists for the same reason:
 * some answers are not in the legacy data at all, so the importer either invents
 * one or records the one Rasmus gave. This records them, so a fresh
 * prod-to-test restore and a re-run produce the same corrected estate rather
 * than needing the same corrections applied by hand again.
 *
 * That is the whole point of putting them here. Both of the rulings below were
 * first applied to a deployed database directly, which fixes one dataset and
 * nothing else -- the next import would have reproduced the same two defects.
 *
 * TWO KINDS OF ANSWER, and they are separate maps because they are separate
 * questions.
 *
 * A DOMAIN, because ten of the twelve templates carry one and the rest carry
 * none. A series with no domain has no timezone, and cutoffZone() then falls back
 * to hardcoded UTC -- which is exactly what "never use a timezone that is not
 * from the domain settings" rules out. In legacy a null domain means "applies
 * everywhere", which is a visibility answer and not a timezone one, so it cannot
 * simply be carried across.
 *
 * A DESTINATION, because a legacy template is not always a series. Some are one
 * edition that needed to differ, cloned out of a real series because legacy had
 * no way to vary a single issue -- the `dont-use-` templates are named for it.
 * Imported as series they put an edition of one publication under a heading of
 * its own, so the archive it belongs to does not contain it.
 *
 * IT IS A HISTORY FIX, NOT A GAP FIX, and an earlier version of this comment had
 * it wrong. The six `dont-use-` clones do NOT fill any of the nine periods gap
 * detection reports on weekly-ntm: measured, the gap count is nine with the
 * clones and nine without. The apparent match was a name coincidence -- a MISSING
 * pseudo-row is NAMED from the ISO week of its interval end, and
 * "EfS uge 2 - 2025" names both a gap and a clone whose window is a week later.
 *
 * What the clones actually are is the OTHER half of a withdrawal. Each one's
 * member set is identical to an issue already in the destination, which legacy
 * retired when it swapped in the replacement -- 27 of 27 uids for
 * "EfS uge 2 - 2025", 228 of 228 for "Aktive P&T uge 2 - 2025". Filing them in
 * reunites the two halves; it does not recover a missing week.
 */
public final class LegacyTemplateRulings {

    private LegacyTemplateRulings() {
    }

    /**
     * The domain a template's series belongs to, where the template names none.
     *
     * Keyed on the AUTHORED seriesId rather than the legacy template id, because
     * that is the key a human can check against the estate -- and these are
     * decisions a human made and will want to re-read. The ids are stable: S-16
     * makes seriesId immutable after create, and it is the import/export key.
     *
     * Rasmus, 2026-08-26. "accumulated-yearly-ntm = NM. The rest is NM Annex."
     */
    private static final Map<String, String> DOMAIN_BY_SERIES = new LinkedHashMap<>();

    static {
        // The accumulated yearly EfS is the weekly series' own annual roll-up, so
        // it belongs where the weekly series does.
        DOMAIN_BY_SERIES.put("accumulated-yearly-ntm", "niord-nm");

        // The annex publications, all in "Annexes - Notices to Mariners".
        DOMAIN_BY_SERIES.put("aids-to-navigation", "niord-annex");
        DOMAIN_BY_SERIES.put("danish-list-of-lights", "niord-annex");
        DOMAIN_BY_SERIES.put("journal-number", "niord-annex");
        DOMAIN_BY_SERIES.put("list-of-wrecks", "niord-annex");
        DOMAIN_BY_SERIES.put("navigation-through-danish-waters", "niord-annex");
        DOMAIN_BY_SERIES.put("nm-annex-ice-service", "niord-annex");
        DOMAIN_BY_SERIES.put("nm-annex-ncags", "niord-annex");
        DOMAIN_BY_SERIES.put("www-danskehavnelods-dk", "niord-annex");
    }

    /**
     * Templates that are NOT a series, and the series their editions belong to.
     *
     * Keyed on the legacy template id and valued with the authored seriesId of the
     * destination. The destination must be a series the import also creates, so
     * the ordering is not free -- see LegacyImportService, which plans every
     * series before it files any issue.
     *
     * `ncags-2021` is Rasmus's ruling of 2026-08-26: "this is not a series itself
     * and should not be imported/migrated as one. It's a template in the legacy
     * yes, but the series is the NCAGS that hold all the ncags publications."
     *
     * It is the same judgement LegacyOrphanGrouping already records for the
     * template-LESS NCAGS annexes -- eleven of them are one series. The template
     * path simply never asked the question, so one more NCAGS edition became its
     * own series beside the eleven.
     */
    private static final Map<String, String> DESTINATION_BY_TEMPLATE = new LinkedHashMap<>();

    static {
        // NCAGS 2021. SER-019's one-language template; its publications belong to
        // the NCAGS series that already holds the rest.
        DESTINATION_BY_TEMPLATE.put("ebf7e99d-7914-48bf-8919-7525c2f2aee8", "nm-annex-ncags");
    }

    /** The ruled domain for a series, or null when the template's own answer stands. */
    public static String domainFor(String seriesId) {
        return seriesId == null ? null : DOMAIN_BY_SERIES.get(seriesId);
    }

    /**
     * The series a template's editions belong to, or null when the template IS a
     * series.
     */
    public static String destinationFor(String legacyTemplateId) {
        return legacyTemplateId == null ? null : DESTINATION_BY_TEMPLATE.get(legacyTemplateId);
    }

    /** Every ruling, so a report can show what the import applied rather than only what it did. */
    public static Map<String, String> domains() {
        return Map.copyOf(DOMAIN_BY_SERIES);
    }

    public static Map<String, String> destinations() {
        return Map.copyOf(DESTINATION_BY_TEMPLATE);
    }
}
