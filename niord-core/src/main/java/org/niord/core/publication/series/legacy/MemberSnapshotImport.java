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

import org.niord.core.publication.Publication;
import org.niord.core.publication.series.IssueMember;
import org.niord.core.publication.series.MemberSource;
import org.niord.core.publication.series.MembershipProvenance;
import org.niord.core.publication.series.PublicationIssue;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The frozen member snapshot, written from the locked tag.
 *
 * KEYED ON uid, NEVER ON shortId. A short id like NM-375-24 is a display label:
 * it is assigned per series per year and is neither globally unique nor stable
 * across a message being re-numbered. frozenShortId exists so a retired issue
 * can still be READ years later without joining to a message that may have
 * moved -- it is a caption, not a key, and using it as one silently attaches the
 * wrong message.
 *
 * THE IMPORTER CREATES NO OVERRIDES. An IssueOverride records a human decision:
 * somebody added or removed this message on purpose, and the audit says who and
 * when. Legacy has no such record. The single detectable manual edit in ~10,200
 * member rows (NM-375-24) is an attribution BY ELIMINATION -- it is the row that
 * no query explains -- and manufacturing an override from an inference would put
 * a decision in the audit trail that nobody made.
 */
public final class MemberSnapshotImport {

    /**
     * What a member row freezes about its message.
     *
     * frozenMainType, frozenType and frozenStatus are NOT NULL, and they are the
     * reason this is a record rather than a list of uids: the first version of
     * the importer passed uids alone, set none of the three, and died on the
     * constraint AFTER the dry run had reported the estate clean. The dry run
     * could not have caught it, because plan() never persists -- which is why
     * these facts are now gathered and CHECKED at plan time.
     *
     * They are frozen rather than joined so a retired issue can still be read
     * years later without touching a message that may have been re-typed,
     * re-numbered or withdrawn since.
     */
    public record MemberFacts(String uid, String shortId, String mainType, String type,
                              String status, Date publishFrom, Date publishTo) {

        /** True when the row can be written -- the three NOT NULL columns are present. */
        public boolean isComplete() {
            return notBlank(mainType) && notBlank(type) && notBlank(status);
        }

        /** Names what is missing, for a report an admin can act on. */
        public String missing() {
            List<String> gaps = new ArrayList<>();
            if (!notBlank(mainType)) {
                gaps.add("mainType");
            }
            if (!notBlank(type)) {
                gaps.add("type");
            }
            if (!notBlank(status)) {
                gaps.add("status");
            }
            return String.join(", ", gaps);
        }

        private static boolean notBlank(String s) {
            return s != null && !s.isBlank();
        }
    }

    private MemberSnapshotImport() {
    }

    /**
     * Groups the estate by tag name.
     *
     * Built once for the whole run because sharing is invisible from a single
     * row: three publications point at nm-w01-2025 and none of them knows about
     * the other two.
     */
    public static Map<String, List<Publication>> byTagName(List<Publication> estate) {
        Map<String, List<Publication>> out = new LinkedHashMap<>();
        for (Publication p : estate) {
            if (p.getMessageTag() == null || p.getMessageTag().getName() == null) {
                continue;
            }
            out.computeIfAbsent(p.getMessageTag().getName(), k -> new ArrayList<>()).add(p);
        }
        return out;
    }

    /**
     * Writes the members and the provenance onto the issue.
     *
     * memberUids is what the locked tag holds, in its stored order. It is passed
     * in rather than read here because resolving publication -> tag -> messages
     * is a database join, and keeping this a pure function is what lets the tests
     * run it over all 1,077 rows.
     *
     * RESOLVE THE TAG BY THE STORED JOIN, NEVER BY SCANNING THE TAG TABLE. Tag
     * names are not unique -- seven of them are shared, one by three
     * publications -- so a name lookup returns the wrong tag for at least ten
     * rows and cannot tell that it did.
     */
    public static List<IssueMember> apply(PublicationIssue issue, Publication legacy,
                                          List<MemberFacts> members,
                                          Map<String, List<Publication>> publicationsPerTag) {
        MemberProvenanceRules.Decision decision =
                MemberProvenanceRules.decide(legacy, publicationsPerTag);

        issue.setMembershipProvenance(decision.provenance());
        issue.setMembershipProvenanceNote(decision.note());

        List<IssueMember> rows = new ArrayList<>();
        if (members != null) {
            // Duplicates collapse rather than becoming two rows for one message:
            // a tag holding the same uid twice is a legacy artefact, not two
            // memberships, and a duplicate row would double-count in memberCount.
            Set<String> seen = new LinkedHashSet<>();
            int sortIndex = 0;
            for (MemberFacts facts : members) {
                if (facts == null || facts.uid() == null || facts.uid().isBlank()
                        || !seen.add(facts.uid())) {
                    continue;
                }
                IssueMember m = new IssueMember();
                m.setIssue(issue);
                m.setMessageUid(facts.uid());
                m.setSortIndex(sortIndex++);

                // The frozen caption. NOT NULL on three of these, and the whole
                // point of the other three: what this message WAS at freeze, so a
                // retired issue reads correctly however the message changed since.
                m.setFrozenShortId(facts.shortId());
                m.setFrozenMainType(facts.mainType());
                m.setFrozenType(facts.type());
                m.setFrozenStatus(facts.status());
                m.setFrozenPublishDateFrom(facts.publishFrom());
                m.setFrozenPublishDateTo(facts.publishTo());

                // IMPORTED, not CRITERIA: nothing here was derived by running a
                // query. Labelling these CRITERIA would tell the replay it may
                // check them against one, which is the claim the import is careful not
                // to make.
                m.setSource(MemberSource.IMPORTED);
                rows.add(m);
            }
        }

        // The issue does not own the collection -- IssueMember is the owning side
        // -- so the rows are RETURNED for the caller to persist, and only the
        // count is written here. Returning them rather than stashing them keeps
        // this a pure function, which is what lets the tests run it over all
        // 1,077 rows without a database.
        issue.setMemberCount(rows.size());
        return rows;
    }

    /**
     * The rule the annexes turn on, stated once so both halves are visible.
     *
     * A tag-carrying annex imports ONE row; a tagless one imports ZERO. Those are
     * different cases and collapsing them loses the distinction between "there
     * was nothing" and "there was something we did not keep".
     */
    public static boolean importsMemberRows(Publication legacy) {
        return legacy.getMessageTag() != null;
    }

    /** True when the issue's provenance says nothing derives its contents. */
    public static boolean hasQueryProvenance(MembershipProvenance provenance) {
        // I-12, as qualified for the hand-named annexes: criteriaSnapshot is required when
        // the provenance is a QUERY provenance, not merely when members exist.
        // An imported annex has one member and no criteria, and that is legal.
        return provenance == MembershipProvenance.EXACT
                || provenance == MembershipProvenance.EXPLAINED_DIFF
                || provenance == MembershipProvenance.UNION_SNAPSHOT;
    }
}
