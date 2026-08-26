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
 * A DOMAIN, for the few CADENCED series whose template names none. A cadenced
 * series reads its cut-offs in its domain's timezone and has no other source
 * for one, so a missing domain there is a genuine gap to fill.
 *
 * NOT for a cadence-less one. S-5, S-6 and S-7 refuse every nominal cut-off
 * field on a series with no cadence, so it has no cut-off to read in any zone
 * and the timezone argument does not reach it. What a domain still does is
 * NARROW visibility -- the publication picker matches "domain IS NULL OR domain
 * = the current one" -- so filling this "gap" on a publication that had no
 * domain in legacy hides it from every domain but one. Six rulings were
 * withdrawn for exactly that reason; see below.
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
 * What the clones actually are is the OTHER half of a withdrawal. FOUR of the six
 * carry a member set identical to an issue already in the destination -- 27 of 27
 * uids for "EfS uge 2 - 2025", 228 of 228 for "Aktive P&T uge 2 - 2025" -- and in
 * all four the twin is ALREADY INACTIVE, retired by legacy when the replacement
 * was swapped in. The two 2026 clones have no twin at all.
 *
 * So filing them in reunites a withdrawal with its replacement inside one series.
 * It recovers no missing week, and it creates no live duplicate, because the
 * superseded half is already the retired one. Ordering such a pair by age would
 * not work in any case: clone and twin carry the IDENTICAL created timestamp, a
 * 2017 bulk-import artifact.
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
     * Rasmus, 2026-08-26: "accumulated-yearly-ntm = NM. The rest is NM Annex."
     *
     * SIX OF THOSE NINE WERE WITHDRAWN on 2026-08-26, once it was established
     * that they had been asked for by a validator rule rather than by the data.
     * S-20 required a domain on every series, so nine publications that carried
     * none in legacy had to be given one before they could be activated -- and
     * assigning one narrows visibility rather than restoring it. S-20 now applies
     * only to cadenced series, and these six are back to what legacy recorded:
     * no domain, visible everywhere.
     *
     * The three that remain are cadenced or genuinely belong where they are put.
     */
    private static final Map<String, String> DOMAIN_BY_SERIES = new LinkedHashMap<>();

    static {
        // YEARLY, so it has real cut-offs and needs a zone to read them in. It is
        // the weekly series' own annual roll-up, so it belongs where that does.
        DOMAIN_BY_SERIES.put("accumulated-yearly-ntm", "niord-nm");

        // Cadence-less but unmistakably annex series -- eleven NCAGS editions and
        // eight ice-service notices -- and their legacy publications carry the
        // annex domain already. The ruling fills the gap for the handful that do
        // not, so a series is not split across two answers.
        DOMAIN_BY_SERIES.put("nm-annex-ice-service", "niord-annex");
        DOMAIN_BY_SERIES.put("nm-annex-ncags", "niord-annex");

        // WITHDRAWN 2026-08-26, and deliberately left here as a record rather
        // than deleted, because the obvious next question is "why do these have
        // no domain".
        //
        //   aids-to-navigation                 |  none of these carried a domain
        //   journal-number                     |  in legacy, and each is visible
        //   list-of-wrecks                     |  from every domain because of
        //   www-danskehavnelods-dk             |  it. The first four are cited
        //   danish-list-of-lights              |  from the message editor in any
        //   navigation-through-danish-waters   |  domain; giving them one would
        //                                      |  remove them from the picker
        //                                      |  everywhere else.
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

        // The six "DONT USE" templates, and the name is the ruling. Legacy had no
        // way to vary a single issue, so a week that needed to differ -- a double
        // week over a year turnover, a re-issue -- was made by cloning the whole
        // template. Each clone then published one edition and was abandoned, which
        // is what the title warns the next person about.
        //
        // Rasmus, 2026-08-26: "All dont-use templates should not be imported/
        // migrated to their own series. They are a fluke. The issues belonging to
        // these are indeed issues of another series - either NTM or NTM P&T."
        //
        // It is the same judgement LegacyOrphanGrouping already records for the
        // template-LESS half of this problem: "the double-week issues belong to the
        // weekly series they were hacked out of."
        DESTINATION_BY_TEMPLATE.put("6c5018df-26ec-4b35-8570-ee3f0c7ca891", "weekly-ntm");
        DESTINATION_BY_TEMPLATE.put("b0fe5e0f-75b1-4a36-9dd7-b941a92df039", "weekly-ntm");
        DESTINATION_BY_TEMPLATE.put("aed6002d-99e2-49b0-9d72-8a9efe92f412", "weekly-ntm");
        DESTINATION_BY_TEMPLATE.put("1776882d-07be-43dc-85a9-8c024348dd2e", "weekly-ntm-p-t");
        DESTINATION_BY_TEMPLATE.put("5dbe9e77-89db-4c27-ab7f-0a40014a1b98", "weekly-ntm-p-t");
        DESTINATION_BY_TEMPLATE.put("0462f851-d27b-4d64-9f58-84f21f92bf59", "weekly-ntm-p-t");
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
