/*
 * Copyright 2026 Danish Maritime Authority.
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

import org.niord.core.publication.Publication;
import org.niord.core.publication.series.MembershipProvenance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How far a legacy tag can be trusted as an oracle for one issue.
 *
 * The importer copies a locked tag's contents into IssueMember rows. Whether
 * those rows can later be used to CHECK the resolver -- which is what the historical replay's
 * replay does -- depends on whether the tag is evidence of one instant or of
 * something else. This decides that, and records why in a note.
 *
 * NEVER EXACT BY DEFAULT. EXACT is a claim that a replay of the criteria at the
 * cut-off reproduces this set. Anything that weakens the claim has to be caught
 * HERE, because the replay reads the provenance to decide which issues it may hold to
 * that standard, and an over-confident label there turns a genuine divergence
 * into a manifest entry.
 */
public final class MemberProvenanceRules {

    /**
     * The four annual issues that are the answer to no query at any instant.
     *
     * Every publishDate boundary +/-1 ms was swept across the whole series
     * history and none reproduces the recorded set, so these import as a union
     * snapshot rather than as a criteria match that would have to be fabricated
     * to exist.
     *
     * Keyed on tag name rather than on publicationId: the plan names them by
     * title and edition ("Skydeomraader 2020-ed1"), and the tag name is the one
     * identifier that says the same thing unambiguously. Keying on the title is
     * exactly what the keying rule forbids -- two of these were released with a literal
     * ${year} in the title.
     */
    public static final Set<String> IRREPRODUCIBLE_ANNUAL_TAGS = Set.of(
            "firing-areas-2018-v1",
            "firing-areas-2020-v1",
            "firing-areas-2022-v1",
            "nm-almanac-2020-v1");

    /** A tag whose name marks it as one of the hand-named annexes. */
    public static boolean isAnnexTag(String tagName) {
        return tagName != null && tagName.startsWith("nm-annex-");
    }

    /** What was decided, and the note that has to travel with it. */
    public record Decision(MembershipProvenance provenance, String note) {
    }

    private MemberProvenanceRules() {
    }

    /**
     * Decides the provenance for one publication.
     *
     * publicationsPerTag maps a tag NAME to every publication pointing at it, and
     * is built once from the estate rather than queried per row -- a tag shared
     * by three publications cannot be detected by looking at one of them.
     *
     * The reasons ACCUMULATE. A row can be both an annex and a shared tag
     * (nm-annex-ncags-2026 is), and a note that mentioned only the first reason
     * would leave the second invisible to whoever reads the import report.
     */
    public static Decision decide(Publication legacy, Map<String, List<Publication>> publicationsPerTag) {
        if (legacy.getMessageTag() == null) {
            return new Decision(MembershipProvenance.NO_MEMBERSHIP,
                    "no message tag: there is nothing to import, rather than something discarded");
        }

        String tagName = legacy.getMessageTag().getName();
        List<String> reasons = new ArrayList<>();

        List<Publication> sharers = publicationsPerTag.getOrDefault(tagName, List.of());
        boolean shared = sharers.size() > 1;
        if (shared) {
            reasons.add("the tag [" + tagName + "] is shared by " + sharers.size()
                    + " publications, so its contents cannot be attributed to this one's cut-off alone");
        }

        boolean unlocked = !legacy.getMessageTag().isLocked();
        if (unlocked) {
            reasons.add("the tag is NOT locked, so its contents can still change and are not frozen "
                    + "evidence of anything");
        }

        boolean irreproducible = IRREPRODUCIBLE_ANNUAL_TAGS.contains(tagName);
        if (irreproducible) {
            reasons.add("no query at any instant reproduces this set: every publishDate boundary +/-1 ms "
                    + "was swept across the series history and none matches");
        }

        // The annex label wins where it applies, because "named by hand" is a
        // stronger statement about this row than "shared" -- it says there was
        // never a derivation, not that the derivation is untrustworthy. The other
        // reasons still travel in the note.
        //
        // The note itself is PERSISTED, on a column an admin reads years from
        // now, so it says what happened in plain words and carries no internal
        // identifier: this archive outlives the documents those identifiers point
        // at, and once the import has run the rows cannot be edited except by
        // hand against a live database.
        if (isAnnexTag(tagName)) {
            reasons.add(0, "hand-named annex: the locked tag holds the one message this "
                    + "annex contained, and no query can select it because the only discriminator "
                    + "between the year's two annex messages is the message body");
            return new Decision(MembershipProvenance.IMPORTED, String.join("; ", reasons));
        }

        if (irreproducible || shared) {
            return new Decision(MembershipProvenance.UNION_SNAPSHOT, String.join("; ", reasons));
        }

        if (unlocked) {
            return new Decision(MembershipProvenance.EXPLAINED_DIFF, String.join("; ", reasons));
        }

        return new Decision(MembershipProvenance.EXACT, null);
    }

    /**
     * True when the provenance claims a replay reproduces the set.
     *
     * The replay uses this to decide which issues it may hold to the reproduce-exactly
     * standard. Written as one predicate rather than as a comparison at each call
     * site, so that adding a provenance later cannot quietly widen what counts as
     * an oracle.
     */
    public static boolean isReplayOracle(MembershipProvenance provenance) {
        return provenance == MembershipProvenance.EXACT;
    }
}
