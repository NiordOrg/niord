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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.niord.core.domain.Domain;
import org.niord.core.publication.series.BindsRule;
import org.niord.core.publication.Publication;
import org.niord.core.publication.series.IssueMember;
import org.niord.core.publication.series.MemberSource;
import org.niord.core.publication.series.MembershipProvenance;
import org.niord.core.publication.series.PublicationIssue;
import org.niord.core.publication.series.PublicationSeries;

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
 * The frozen member snapshot, over the captured estate: 1,077 publications and their real member sets.
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

    /**
     * Synthetic members of the recorded cardinality. See the class comment.
     *
     * The caption fields are filled because three of them are NOT NULL -- which
     * the importer learned the hard way, having shipped a version that set none
     * of them and died on the constraint after a clean dry run.
     */
    private static List<MemberSnapshotImport.MemberFacts> members(String publicationId, int n) {
        List<MemberSnapshotImport.MemberFacts> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(new MemberSnapshotImport.MemberFacts(
                    publicationId + "#" + i, "NM-" + i + "-26", "NM", "TEMPORARY_NOTICE",
                    "PUBLISHED", new java.util.Date(0L), null));
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
                    issue, p, members(p.getPublicationId(), n), byTag).rows();
            out.put(p.getPublicationId(), new Imported(issue, members));
        }
        return out;
    }

    // --------------------------------------------------------- the annexes

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

        assertEquals(6, tagCarrying, "six tag-carrying annexes, as ruled");
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
            assertFalse(MemberProvenanceRules.reproducesExactly(issue.getMembershipProvenance()),
                    p.getPublicationId() + " (" + tag + ") is contaminated but claims to be an oracle; "
                            + "anything comparing it against a fresh resolution would hold it to a "
                            + "standard it cannot meet and read the difference as a defect");
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

        assertEquals(4, found, "the four annuals the snapshot rules name");
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
                // frozenShortId IS populated -- it is the caption. The rule is that
                // nothing LOOKS UP by it, which is asserted separately over the
                // importer's source. An earlier version of this test asserted the
                // column was null, conflating "never a key" with "never written",
                // and that is precisely the confusion the caption exists to avoid.
                assertNotNull(m.getFrozenShortId(), "the caption is what makes a retired issue readable");
                assertEquals(MemberSource.IMPORTED, m.getSource(),
                        "labelling these CRITERIA would claim they can be checked against a "
                                + "query that was never run");
            }
        }
        assertTrue(rows > 10_000, "the estate holds ~10,200 member rows; only " + rows + " were built");
    }

    /**
     * Nothing in the importer LOOKS UP by frozenShortId.
     *
     * Short ids are assigned per series per year and are not unique, so a lookup
     * by one silently attaches the wrong message. The column exists so a retired
     * issue can still be READ without joining to a message that may have moved --
     * a caption, never a key.
     *
     * Asserted over the source because it is a rule about how the code is
     * written, and no runtime state can show that a query was never made.
     */
    @Test
    public void nothingInTheImporterLooksUpByShortId() throws Exception {
        java.nio.file.Path dir = java.nio.file.Path.of(
                "src", "main", "java", "org", "niord", "core", "publication", "series", "legacy");
        List<String> offenders = new ArrayList<>();

        try (var files = java.nio.file.Files.walk(dir)) {
            for (java.nio.file.Path f : files.filter(x -> x.toString().endsWith(".java")).toList()) {
                for (String line : java.nio.file.Files.readAllLines(f)) {
                    String code = line.trim();
                    if (code.startsWith("//") || code.startsWith("*")) {
                        continue;
                    }
                    // A WHERE, a setParameter or a map lookup keyed on the caption.
                    // A lookup keyed on the caption: a JPQL predicate on the column,
                    // or a map/compare against the short id in Java. No regex --
                    // plain containment, so the check itself cannot be subtly wrong.
                    boolean jpqlPredicate = code.contains("frozenShortId =")
                            || code.contains("frozenShortId IN")
                            || code.contains("frozenShortId=");
                    boolean javaLookup = code.contains(".get(facts.shortId())")
                            || code.contains("getFrozenShortId()) ==")
                            || code.contains("getFrozenShortId().equals(");
                    if (jpqlPredicate || javaLookup) {
                        offenders.add(f.getFileName() + ": " + code);
                    }
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                "short ids are not unique, so a lookup by one attaches the wrong message: " + offenders);
    }

    /** The importer creates no overrides, on any row. */
    @BindsRule({"O-7"})
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
                issue, p, List.of(facts("uid-a"), facts("uid-b"), facts("uid-a")), Map.of()).rows();

        assertEquals(2, members.size(), "a tag holding one uid twice is an artefact, not two memberships");
        assertEquals(2, issue.getMemberCount());
    }

    /**
     * Every member row carries the frozen caption its columns demand.
     *
     * frozenMainType, frozenType and frozenStatus are NOT NULL. The importer set
     * none of them until 2026-08-24, and the DRY RUN COULD NOT SEE IT -- plan()
     * never persists, so the estate reported clean and the real import died on
     * the constraint. That is why the facts are now checked at plan time, and why
     * this asserts the row is writable rather than merely present.
     */
    @Test
    public void everyMemberRowCarriesTheFrozenCaption() throws Exception {
        int checked = 0;
        for (Imported result : importAll().values()) {
            for (IssueMember m : result.members()) {
                checked++;
                assertNotNull(m.getFrozenMainType(), "frozenMainType is NOT NULL");
                assertNotNull(m.getFrozenType(), "frozenType is NOT NULL");
                assertNotNull(m.getFrozenStatus(), "frozenStatus is NOT NULL");
            }
        }
        assertTrue(checked > 10_000, "only " + checked + " rows were checked");
    }

    /**
     * An imported member is frozen as PUBLISHED, whatever the message says today.
     *
     * The tag is the list a released document printed, and the archive carries
     * nothing withdrawn before its cut-off -- so every message in it was
     * published when the document went out. Freezing the live status instead
     * recorded what the message was on import day, and a row saying "cancelled"
     * about a message the document listed as in force made the imported issues
     * read differently from the native ones: the cancel showed as the record on
     * the one and as a change since on the other.
     */
    @Test
    public void anImportedMemberIsFrozenAsPublishedWhateverTheMessageSaysToday() {
        Publication p = LegacyEstateFixture.publications().stream()
                .filter(x -> x.getMessageTag() != null).findFirst().orElseThrow();
        PublicationIssue issue = new PublicationIssue();
        MemberSnapshotImport.MemberFacts cancelledSince = new MemberSnapshotImport.MemberFacts(
                "uid-c", "NM-002-26", "NM", "TEMPORARY_NOTICE", "CANCELLED", null, null);

        List<IssueMember> members = MemberSnapshotImport.apply(
                issue, p, List.of(facts("uid-a"), cancelledSince), Map.of()).rows();

        for (IssueMember m : members) {
            assertEquals("PUBLISHED", m.getFrozenStatus(),
                    m.getMessageUid() + ": a member of a released document was published when it went out");
        }
        // The rest of the caption is still the live message: the best witness there is.
        assertEquals("TEMPORARY_NOTICE", members.get(1).getFrozenType());
    }

    /** A message missing any of the three is reported rather than written. */
    @Test
    public void amemberThatCannotBeFrozenIsNamed() {
        MemberSnapshotImport.MemberFacts incomplete = new MemberSnapshotImport.MemberFacts(
                "uid-x", "NM-001-26", null, "TEMPORARY_NOTICE", null, null, null);

        assertFalse(incomplete.isComplete());
        assertTrue(incomplete.missing().contains("mainType"));
        assertTrue(incomplete.missing().contains("status"));
        assertFalse(incomplete.missing().contains("type"), "type is present and must not be named");
    }

    /** Well-formed synthetic facts for a single uid. */
    private static MemberSnapshotImport.MemberFacts facts(String uid) {
        return new MemberSnapshotImport.MemberFacts(uid, "NM-001-26", "NM", "TEMPORARY_NOTICE",
                "PUBLISHED", null, null);
    }

    /** A publication with no tag gets NO_MEMBERSHIP and says so. */
    @Test
    public void noTagMeansNothingToImportRatherThanSomethingDiscarded() {
        Publication p = new Publication();
        p.setPublicationId("x");
        PublicationIssue issue = new PublicationIssue();

        List<IssueMember> members = MemberSnapshotImport.apply(issue, p, null, Map.of()).rows();

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

    // ------------------------------------------- the rows the tag kept adding later

    /**
     * A notice published nearly a year after the sitting never reaches the snapshot.
     *
     * The tag is a hand-maintained list and goes on being edited after the
     * document has gone out; production has weekly tags holding notices published
     * up to a year later. Freezing one of those would make the archive say the
     * issue announced a message nobody could read at the time, and every
     * compilation drawn from the snapshot would repeat it.
     *
     * The count and the ordering are asserted with it because both are how the
     * drop becomes visible elsewhere: a memberCount that outran its rows is a
     * number the screen cannot account for, and a sortIndex with a hole in it
     * reads as a row that failed to write.
     */
    @Test
    public void arowPublishedLongAfterTheIssueIsLeftOutOfTheSnapshot() {
        PublicationIssue issue = closedIssue("2025-01-08T12:00");

        MemberSnapshotImport.Snapshot snapshot = MemberSnapshotImport.apply(
                issue, taggedPublication(),
                List.of(dated("uid-onTime", "NM-0042-25", "2025-01-08T09:00"),
                        dated("uid-late", "NM-0311-25", "2025-11-04T09:00")),
                Map.of());

        assertEquals(1, snapshot.rows().size(), "the late row must not be frozen");
        assertEquals("uid-onTime", snapshot.rows().get(0).getMessageUid());
        assertEquals(0, snapshot.rows().get(0).getSortIndex(),
                "the kept rows are contiguous from zero; a gap reads as a row that failed to write");
        assertEquals(1, issue.getMemberCount(),
                "a left-out row is not a member, and counting it puts a number on the screen that "
                        + "no list of rows can account for");

        assertEquals(1, snapshot.leftOut().size());
        assertEquals("uid-late", snapshot.leftOut().get(0).uid());
        assertEquals("NM-0311-25", snapshot.leftOut().get(0).shortId(),
                "the report names the row by the caption an operator recognises");
    }

    /**
     * A row inside the grace is still imported.
     *
     * Most imported cut-offs are reconstructed and land days before the sitting
     * they stand for, so a row a few days after one is a genuine member of the
     * document. The grace is what keeps the importer from deleting that archive.
     */
    @Test
    public void arowInsideTheGraceIsStillImported() {
        PublicationIssue issue = closedIssue("2025-01-01T12:00");

        MemberSnapshotImport.Snapshot snapshot = MemberSnapshotImport.apply(
                issue, taggedPublication(),
                List.of(dated("uid-a", "NM-0001-25", "2025-01-02T09:00")), Map.of());

        assertEquals(1, snapshot.rows().size(),
                "an annual list cut on 1 January lists the notices stamped the next day");
        assertTrue(snapshot.leftOut().isEmpty());
    }

    /**
     * An issue with no instant to measure against keeps every row.
     *
     * The rule has to be able to say a row is late; it cannot say so against an
     * issue the recovery gave neither a cut-off nor a publish stamp. Dropping
     * members there would enforce a rule nobody evaluated, and the archive is the
     * thing that would be lost.
     */
    @Test
    public void anIssueWithNoReferenceInstantKeepsEveryRow() {
        PublicationIssue issue = new PublicationIssue();

        MemberSnapshotImport.Snapshot snapshot = MemberSnapshotImport.apply(
                issue, taggedPublication(),
                List.of(dated("uid-a", "NM-0001-25", "2025-01-02T09:00"),
                        dated("uid-b", "NM-0311-25", "2026-11-04T09:00")), Map.of());

        assertEquals(2, snapshot.rows().size(),
                "no cut-off and no publish stamp means no instant the rows can be late against");
        assertTrue(snapshot.leftOut().isEmpty());
    }

    /**
     * The publish stamp stands in where the recovery produced no cut-off.
     *
     * A released issue is always stamped, and the stamp is the closest thing to
     * the sitting there is when the cut-off could not be reconstructed. Reading
     * only the cut-off would leave those issues judging nothing at all.
     */
    @Test
    public void thePublishStampStandsInForAMissingCutoff() {
        PublicationIssue issue = closedIssue("2025-01-08T12:00");
        issue.setPublishedAt(issue.getCutoffStampedAt());
        issue.setCutoffStampedAt(null);

        MemberSnapshotImport.Snapshot snapshot = MemberSnapshotImport.apply(
                issue, taggedPublication(),
                List.of(dated("uid-late", "NM-0311-25", "2025-11-04T09:00")), Map.of());

        assertTrue(snapshot.rows().isEmpty());
        assertEquals(1, snapshot.leftOut().size());
    }

    /**
     * The note says which issue lost rows, how many, and which they were.
     *
     * The code is the handle an operator greps the report for; the names and their
     * publish dates are what makes the note checkable against the legacy tag it is
     * about. Without them the report says an archive lost something and leaves
     * nobody able to find out what.
     */
    @Test
    public void theReportNoteNamesTheIssueAndTheRowsItLost() {
        LegacyImportReportVo.ProblemVo note = LegacyImportService.membersLeftOut(
                "pub-123", "Aktive P&T uge 1 - 2025",
                List.of(new MemberSnapshotImport.LeftOutMember(
                                "uid-late", "NM-0311-25", at("2025-11-04T09:00")),
                        new MemberSnapshotImport.LeftOutMember(
                                "uid-later", "NM-0400-25", at("2025-12-29T09:00"))));

        assertEquals("MEMBER_LEFT_OUT_PUBLISHED_AFTER_ISSUE", note.getCode(),
                "the code is the external handle the runbook greps for");
        assertEquals(LegacyImportService.MEMBER_LEFT_OUT_PUBLISHED_AFTER_ISSUE, note.getCode());
        assertEquals("pub-123", note.getPublicationId(),
                "the note must name the legacy row, or nobody can find the tag to fix");
        assertEquals("Aktive P&T uge 1 - 2025", note.getTitle());
        assertTrue(note.getDetail().startsWith("2 messages"),
                "the note must say how many rows were lost: " + note.getDetail());
        assertTrue(note.getDetail().contains("NM-0311-25") && note.getDetail().contains("NM-0400-25"),
                "the note must name the rows: " + note.getDetail());
        assertTrue(note.getDetail().contains("14 days"),
                "the note must say what the rule was, or the number cannot be judged: "
                        + note.getDetail());
        assertTrue(note.getDetail().contains("Nothing in the legacy tables is changed"),
                "the note must say the legacy publication is untouched: " + note.getDetail());
    }

    /** Past a handful the note counts the rest rather than listing a hundred short ids. */
    @Test
    public void anIssueThatLostAHundredRowsGetsOneReadableNote() {
        List<MemberSnapshotImport.LeftOutMember> lost = new ArrayList<>();
        for (int i = 0; i < 105; i++) {
            lost.add(new MemberSnapshotImport.LeftOutMember(
                    "uid-" + i, "NM-" + i + "-25", at("2025-11-04T09:00")));
        }

        LegacyImportReportVo.ProblemVo note =
                LegacyImportService.membersLeftOut("pub-123", "Aktive P&T uge 1 - 2025", lost);

        assertTrue(note.getDetail().startsWith("105 messages"));
        assertTrue(note.getDetail().contains("and 100 more"),
                "a note listing all of them is a note nobody reads: " + note.getDetail());
    }

    /** A publication carrying a tag, which is what makes the member rules apply at all. */
    private static Publication taggedPublication() {
        return LegacyEstateFixture.publications().stream()
                .filter(x -> x.getMessageTag() != null).findFirst().orElseThrow();
    }

    /** An issue closed at a wall-clock time in the series' cut-off zone. */
    private static PublicationIssue closedIssue(String localCutoff) {
        Domain domain = new Domain();
        domain.setDomainId("member-snapshot-test");
        domain.setTimeZone(CUTOFF_ZONE.getId());

        PublicationSeries series = new PublicationSeries();
        series.setSeriesId("member-snapshot-test");
        series.setDomain(domain);

        PublicationIssue issue = new PublicationIssue();
        issue.setSeries(series);
        issue.setCutoffStampedAt(at(localCutoff));
        return issue;
    }

    /** Well-formed facts carrying the publish date the late rule reads. */
    private static MemberSnapshotImport.MemberFacts dated(String uid, String shortId,
                                                          String localPublished) {
        return new MemberSnapshotImport.MemberFacts(uid, shortId, "NM", "TEMPORARY_NOTICE",
                "PUBLISHED", at(localPublished), null);
    }

    /** A wall-clock time in the cut-off zone, which is where the rule reads days. */
    private static java.util.Date at(String localDateTime) {
        return java.util.Date.from(
                java.time.LocalDateTime.parse(localDateTime).atZone(CUTOFF_ZONE).toInstant());
    }

    /** The zone the weekly desks sit in, and so the one their cut-offs are read in. */
    private static final java.time.ZoneId CUTOFF_ZONE = java.time.ZoneId.of("Europe/Copenhagen");
}
