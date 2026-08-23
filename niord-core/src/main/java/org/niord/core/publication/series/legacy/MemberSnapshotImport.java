package org.niord.core.publication.series.legacy;

import org.niord.core.publication.Publication;
import org.niord.core.publication.series.IssueMember;
import org.niord.core.publication.series.MemberSource;
import org.niord.core.publication.series.MembershipProvenance;
import org.niord.core.publication.series.PublicationIssue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * B5.5. The frozen member snapshot, written from the locked tag.
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
                                          List<String> memberUids,
                                          Map<String, List<Publication>> publicationsPerTag) {
        MemberProvenanceRules.Decision decision =
                MemberProvenanceRules.decide(legacy, publicationsPerTag);

        issue.setMembershipProvenance(decision.provenance());
        issue.setMembershipProvenanceNote(decision.note());

        List<IssueMember> members = new ArrayList<>();
        if (memberUids != null) {
            // Duplicates collapse rather than becoming two rows for one message:
            // a tag holding the same uid twice is a legacy artefact, not two
            // memberships, and a duplicate row would double-count in memberCount.
            Set<String> unique = new LinkedHashSet<>(memberUids);
            int sortIndex = 0;
            for (String uid : unique) {
                if (uid == null || uid.isBlank()) {
                    continue;
                }
                IssueMember m = new IssueMember();
                m.setIssue(issue);
                m.setMessageUid(uid);
                m.setSortIndex(sortIndex++);

                // IMPORTED, not CRITERIA: nothing here was derived by running a
                // query. Labelling these CRITERIA would tell the replay it may
                // check them against one, which is the claim B5.5 is careful not
                // to make.
                m.setSource(MemberSource.IMPORTED);
                members.add(m);
            }
        }

        // The issue does not own the collection -- IssueMember is the owning side
        // -- so the rows are RETURNED for the caller to persist, and only the
        // count is written here. Returning them rather than stashing them keeps
        // this a pure function, which is what lets the tests run it over all
        // 1,077 rows without a database.
        issue.setMemberCount(members.size());
        return members;
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
        // I-12, as qualified by ruling B5-iv: criteriaSnapshot is required when
        // the provenance is a QUERY provenance, not merely when members exist.
        // An imported annex has one member and no criteria, and that is legal.
        return provenance == MembershipProvenance.EXACT
                || provenance == MembershipProvenance.EXPLAINED_DIFF
                || provenance == MembershipProvenance.UNION_SNAPSHOT;
    }
}
