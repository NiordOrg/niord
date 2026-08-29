/*
 * Copyright 2026 Danish Emergency Management Agency.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.niord.core.publication.series.legacy;

import org.niord.core.publication.series.ContentMode;
import org.niord.core.publication.series.SeriesAvailability;

import java.util.LinkedHashMap;
import java.util.List;
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
 * AN OWNER, for every series whose template names none. The owner is the desk
 * that lists a publication, administers it, and supplies the timezone its
 * cut-offs are read in, and every publication has exactly one.
 *
 * THE SCOPE OF THIS RULING HAS MOVED TWICE, and the reason is worth keeping. It
 * once covered the cadenced series only, because assigning a domain also NARROWED
 * where a publication could be cited -- so filling the "gap" on a publication
 * that had none hid it from every desk but one, and six rulings were withdrawn
 * for exactly that. Availability answers the citing question now, so an owner
 * costs a publication nothing and the ruling covers everything again.
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
     * SIX OF THESE WERE WITHDRAWN ON 2026-08-26 AND REINSTATED ON 2026-08-29,
     * and the round trip is worth reading rather than tidying away. They were
     * added because a validator rule demanded a domain, withdrawn when it turned
     * out that assigning one NARROWED where a publication could be cited, and
     * reinstated once the two questions were separated: a domain now says who
     * ADMINISTERS a publication, and a separate availability setting says who may
     * cite it. The six get an owner AND availability everywhere, so they keep the
     * reach they had and gain a desk that is responsible for them.
     *
     * Rasmus, 2026-08-29: "I only want each series or one-off shown and
     * administrated in ONE domain. However, some of them should be available in
     * other domains as well, e.g. the Journal Number one-off that should be
     * available in every domain."
     */
    private static final Map<String, String> DOMAIN_BY_SERIES = new LinkedHashMap<>();

    /**
     * The owner for a series no template and no ruling names.
     *
     * NM Annex, because that is where the publications nobody else claims already
     * live: the annexes, the reference lists, the one-offs. It is a real desk with
     * a real timezone rather than a placeholder, which matters because the owner
     * is the only source of the zone a cut-off is read in.
     */
    public static final String DEFAULT_DOMAIN = "niord-annex";

    /**
     * The six publications every domain cites and none of them owns.
     *
     * Each is a reference document rather than an edition of anything: the journal
     * number, the list of lights, the wreck list, the harbour pilot link, the aids
     * to navigation, the navigation guide. An editor in any domain reaches for
     * them, so narrowing them to the desk that maintains them would empty the
     * citation dialog everywhere else -- which is exactly what happened the first
     * time they were given a domain.
     *
     * Named here rather than derived from "carried no domain in legacy", because
     * that property is about the old data and this is a decision about the new
     * model. A future import of an estate that happens to have filled the column in
     * must not silently make them private.
     */
    private static final List<String> SHARED_EVERYWHERE = List.of(
            "journal-number",
            "aids-to-navigation",
            "list-of-wrecks",
            "www-danskehavnelods-dk",
            "danish-list-of-lights",
            "navigation-through-danish-waters");

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

        // The six that carried no domain in legacy. Owned by the annex desk,
        // available everywhere -- which is what the null used to express, now said
        // in the field that means it.
        for (String seriesId : SHARED_EVERYWHERE) {
            DOMAIN_BY_SERIES.put(seriesId, DEFAULT_DOMAIN);
        }
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
     * Who besides the owner may cite an imported series.
     *
     * TWO RULES, and the split is by what the publication IS rather than by which
     * domain it landed in.
     *
     * A GENERATED series is assembled from one domain's own messages over one
     * domain's cut-off calendar. Its weekly edition means the NM desk's week and
     * nothing else, so it is the owner's and no one else's: OWNER_ONLY.
     *
     * EVERYTHING ELSE -- an uploaded document, an external link, a publication with
     * no content model at all -- is a reference somebody points at. That is how the
     * old system behaved by construction, because it had no way to narrow one, and
     * narrowing them now would remove from every citation dialog exactly the
     * publications that are cited from all of them.
     *
     * The six shared references are covered by the same rule and not by an
     * exception: none of them is generated. They are listed above for the OWNER
     * decision, which the data really does not contain, and their availability
     * falls out of what they are.
     *
     * Delegated to the model's own default rather than restated, because the
     * editor applies the identical rule to a publication created by hand -- and an
     * imported publication that shared differently from an authored one of the
     * same kind would be a difference nobody could see the reason for.
     */
    public static SeriesAvailability availabilityFor(ContentMode contentMode) {
        return SeriesAvailability.defaultFor(contentMode);
    }

    /** The six the ruling shares with every domain, for a report that shows what was applied. */
    public static List<String> sharedEverywhere() {
        return SHARED_EVERYWHERE;
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
