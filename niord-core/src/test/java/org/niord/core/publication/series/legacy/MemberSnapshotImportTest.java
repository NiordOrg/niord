package org.niord.core.publication.series.legacy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.niord.core.publication.Publication;
import org.niord.core.publication.series.IssueMember;
import org.niord.core.publication.series.MemberSource;
import org.niord.core.publication.series.MembershipProvenance;
import org.niord.core.publication.series.PublicationIssue;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B5.5, over the captured estate: 1,077 publications and their real member sets.
 *
 * The member uids are not in the capture -- it recorded shortIds and counts --
 * so where a test needs rows it synthesises uids of the right CARDINALITY from
 * the recorded messageCount. That is enough for everything asserted here, which
 * is about how many rows an issue gets and what provenance it carries, and it is
 * flagged rather than glossed: this suite does not prove the uids are right.
 *
 * No database and no Quarkus.
 */
public class MemberSnapshotImportTest {

    private static Map<String, Integer> memberCounts() throws Exception {
        try (InputStream in = MemberSnapshotImportTest.class
                .getResourceAsStream("/fixtures/legacy-estate/members.json")) {
            assertNotNull(in, "members.json is missing");
            JsonNode root = new ObjectMapper().readTree(in);
            Map<String, Integer> out = new LinkedHashMap<>();
            root.fields().forEachRemaining(e ->
                    out.put(e.getKey(), e.getValue().path("messageCount").asInt(0)));
            return out;
        }
    }

    /** Synthetic uids of the recorded cardinality. See the class comment. */
    private static List<String> uids(String publicationId, int n) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(publicationId + "#" + i);
        }
        return out;
    }

    private record Imported(PublicationIssue issue, List<IssueMember> members) {
    }

    private static Map<String, Imported> importAll() throws Exception {
        List<Publication> estate = LegacyEstateFixture.publications();
        Map<String, List<Publication>> byTag = MemberSnapshotImport.byTagName(estate);
        Map<String, Integer> counts = memberCounts();
        Map<String, Imported> out = new LinkedHashMap<>();

        for (Publication p : estate) {
            PublicationIssue issue = new PublicationIssue();
            int n = MemberSnapshotImport.importsMemberRows(p)
                    ? counts.getOrDefault(p.getPublicationId(), 0) : 0;
            List<IssueMember> members = MemberSnapshotImport.apply(
                    issue, p, uids(p.getPublicationId(), n), byTag);
            out.put(p.getPublicationId(), new Imported(issue, members));
        }
        return out;
    }

    // -------------------------------------------------- the annexes (B5-iv)

    /**
     * The six tag-carrying annexes import exactly one member row each; the
     * tagless ones import zero. Two cases, distinguished rather than collapsed.
     *
     * The plan says ten tagless annexes; the captured estate holds THIRTEEN
     * NCAGS / ice-service publications with no tag. The count is asserted as
     * measured -- the rule is what matters and it is unambiguous, while the
     * figure is a third plan count that does not reconcile (see RULINGS-B
     * section 5).
     */
    @Test
    public void theTagCarryingAnnexesImportOneRowAndTheTaglessOnesImportZero() throws Exception {
        Map<String, Imported> imported = importAll();
        int tagCarrying = 0;
        int tagless = 0;

        for (Publication p : LegacyEstateFixture.publications()) {
            boolean annexTitled = p.getDescs().stream().anyMatch(d -> d.getTitle() != null
                    && (d.getTitle().contains("NCAGS") || d.getTitle().contains("istjeneste")
                        || d.getTitle().contains("Marinestaben")));
            if (!annexTitled) {
                continue;
            }

            Imported result = imported.get(p.getPublicationId());
            if (p.getMessageTag() != null) {
                tagCarrying++;
                assertEquals(1, result.members().size(),
                        p.getPublicationId() + ": the locked tag is the only surviving record of what "
                                + "this annex contained; importing zero rows discards it for good");
                assertEquals(MembershipProvenance.IMPORTED, result.issue().getMembershipProvenance());
            } else {
                tagless++;
                assertEquals(0, result.members().size(),
                        "a tagless annex has nothing to import, which is a different case from one "
                                + "whose contents were discarded");
                assertEquals(MembershipProvenance.NO_MEMBERSHIP,
                        result.issue().getMembershipProvenance());
            }
        }

        assertEquals(6, tagCarrying, "six tag-carrying annexes, as ruled in B5-iv");
        assertEquals(13, tagless, "measured; the plan says ten");
    }

    /** An imported annex has a member and no query provenance -- I-12 as qualified. */
    @Test
    public void anImportedAnnexIsNotAQueryProvenance() {
        assertFalse(MemberSnapshotImport.hasQueryProvenance(MembershipProvenance.IMPORTED),
                "I-12 requires criteriaSnapshot when the provenance is a QUERY provenance, not merely "
                        + "when members exist; an annex has one member and no criteria, and that is legal");
        assertFalse(MemberSnapshotImport.hasQueryProvenance(MembershipProvenance.NO_MEMBERSHIP));
        assertTrue(MemberSnapshotImport.hasQueryProvenance(MembershipProvenance.EXACT));
    }

    // ------------------------------------------- the contaminated oracles

    /** Every blocklisted issue carries a non-EXACT provenance AND a non-empty note. */
    @Test
    public void everyContaminatedIssueIsNonExactAndSaysWhy() throws Exception {
        Map<String, Imported> imported = importAll();
        Map<String, List<Publication>> byTag =
                MemberSnapshotImport.byTagName(LegacyEstateFixture.publications());

        int checked = 0;
        for (Publication p : LegacyEstateFixture.publications()) {
            if (p.getMessageTag() == null) {
                continue;
            }
            String tag = p.getMessageTag().getName();
            boolean shared = byTag.getOrDefault(tag, List.of()).size() > 1;
            boolean unlocked = !p.getMessageTag().isLocked();
            boolean irreproducible = MemberProvenanceRules.IRREPRODUCIBLE_ANNUAL_TAGS.contains(tag);
            if (!shared && !unlocked && !irreproducible) {
                continue;
            }

            checked++;
            PublicationIssue issue = imported.get(p.getPublicationId()).issue();
            assertFalse(MemberProvenanceRules.isReplayOracle(issue.getMembershipProvenance()),
                    p.getPublicationId() + " (" + tag + ") is contaminated but claims to be an oracle; "
                            + "B6.1 would hold it to a standard it cannot meet and the divergence would "
                            + "be pushed into the expected-diff manifest");
            assertNotNull(issue.getMembershipProvenanceNote(), p.getPublicationId());
            assertFalse(issue.getMembershipProvenanceNote().isBlank(),
                    "a non-EXACT provenance with no reason is unauditable");
        }

        assertTrue(checked > 0, "no contaminated issues were found, so this asserted nothing");
    }

    /** The two named tags, each shared by three publications. */
    @Test
    public void theThreeWayTagsAreUnionSnapshots() throws Exception {
        Map<String, Imported> imported = importAll();
        Map<String, List<Publication>> byTag =
                MemberSnapshotImport.byTagName(LegacyEstateFixture.publications());

        for (String tag : List.of("nm-pt-w01-2025", "nm-w01-2025")) {
            List<Publication> sharers = byTag.get(tag);
            assertEquals(3, sharers.size(), tag + " is shared by three publications");

            for (Publication p : sharers) {
                PublicationIssue issue = imported.get(p.getPublicationId()).issue();
                assertEquals(MembershipProvenance.UNION_SNAPSHOT, issue.getMembershipProvenance(), tag);
                assertTrue(issue.getMembershipProvenanceNote().contains(tag),
                        "the note must name the tag, or an admin cannot act on the report");
            }
        }
    }

    /** The four annuals no query reproduces import as unions, never as a fabricated match. */
    @Test
    public void theIrreproducibleAnnualsAreUnionSnapshots() throws Exception {
        Map<String, Imported> imported = importAll();
        int found = 0;

        for (Publication p : LegacyEstateFixture.publications()) {
            if (p.getMessageTag() == null
                    || !MemberProvenanceRules.IRREPRODUCIBLE_ANNUAL_TAGS
                            .contains(p.getMessageTag().getName())) {
                continue;
            }
            found++;
            PublicationIssue issue = imported.get(p.getPublicationId()).issue();
            assertEquals(MembershipProvenance.UNION_SNAPSHOT, issue.getMembershipProvenance());
            assertTrue(issue.getMembershipProvenanceNote().contains("no query at any instant"));
        }

        assertEquals(4, found, "the four annuals named in B5.5");
    }

    // --------------------------------------------------------- the mechanics

    /** Every member row is keyed on uid, and frozenShortId is never a key. */
    @Test
    public void everyMemberIsKeyedOnUidAndNeverOnShortId() throws Exception {
        int rows = 0;
        for (Imported result : importAll().values()) {
            for (IssueMember m : result.members()) {
                rows++;
                assertNotNull(m.getMessageUid(), "uid is the key");
                assertFalse(m.getMessageUid().isBlank());
                assertNull(m.getFrozenShortId(),
                        "frozenShortId is a caption for reading a retired issue, not a lookup key: "
                                + "short ids are assigned per series per year and are not unique");
                assertEquals(MemberSource.IMPORTED, m.getSource(),
                        "labelling these CRITERIA would tell the replay it may check them against a "
                                + "query that was never run");
            }
        }
        assertTrue(rows > 10_000, "the estate holds ~10,200 member rows; only " + rows + " were built");
    }

    /** The importer creates no overrides, on any row. */
    @Test
    public void theImporterCreatesNoOverrides() throws Exception {
        for (Imported result : importAll().values()) {
            for (IssueMember m : result.members()) {
                assertNull(m.getOverride(),
                        "an override records a human decision with an audit entry; legacy has no such "
                                + "record, and NM-375-24 is an attribution by elimination rather than "
                                + "evidence that anybody chose anything");
            }
        }
    }

    /** memberCount agrees with the rows actually built, on every issue. */
    @Test
    public void theCountAgreesWithTheRows() throws Exception {
        for (Map.Entry<String, Imported> e : importAll().entrySet()) {
            assertEquals(e.getValue().members().size(), e.getValue().issue().getMemberCount(),
                    e.getKey() + ": a count that disagrees with its rows is a lie the UI will repeat");
        }
    }

    /** A duplicate uid in a tag collapses rather than double-counting. */
    @Test
    public void aDuplicateUidBecomesOneRow() {
        Publication p = LegacyEstateFixture.publications().stream()
                .filter(x -> x.getMessageTag() != null).findFirst().orElseThrow();
        PublicationIssue issue = new PublicationIssue();

        List<IssueMember> members = MemberSnapshotImport.apply(
                issue, p, List.of("uid-a", "uid-b", "uid-a"), Map.of());

        assertEquals(2, members.size(), "a tag holding one uid twice is an artefact, not two memberships");
        assertEquals(2, issue.getMemberCount());
    }

    /** A publication with no tag gets NO_MEMBERSHIP and says so. */
    @Test
    public void noTagMeansNothingToImportRatherThanSomethingDiscarded() {
        Publication p = new Publication();
        p.setPublicationId("x");
        PublicationIssue issue = new PublicationIssue();

        List<IssueMember> members = MemberSnapshotImport.apply(issue, p, null, Map.of());

        assertEquals(0, members.size());
        assertEquals(MembershipProvenance.NO_MEMBERSHIP, issue.getMembershipProvenance());
        assertTrue(issue.getMembershipProvenanceNote().contains("nothing to import"));
    }

    /** An uncontaminated locked tag is EXACT, with no note to explain. */
    @Test
    public void acleanLockedTagIsExactAndNeedsNoNote() throws Exception {
        Map<String, Imported> imported = importAll();
        long exact = imported.values().stream()
                .filter(r -> r.issue().getMembershipProvenance() == MembershipProvenance.EXACT)
                .peek(r -> assertNull(r.issue().getMembershipProvenanceNote(),
                        "EXACT is the absence of a reason; a note would imply one"))
                .count();

        assertTrue(exact > 900, "most of the estate should be a usable oracle; only " + exact + " were");
    }
}
