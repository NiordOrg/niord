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

package org.niord.core.publication.series;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.TestIds;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.criteria.MessageSeriesCriterionVo;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.series.vo.IssueArchiveVo;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.core.user.User;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading back a superseded document, which is the only thing the archive is for.
 *
 * The store is written on three paths and read on one, and until the read
 * existed the archive was a folder nothing could open -- a guarantee that every
 * generation is kept, with no way to check it. The read is where the guarantee
 * becomes usable, and it is also where the store's location stops being an
 * implementation detail: an address that a request could steer would turn a
 * gated stream into a file browser rooted wherever the application account can
 * read.
 *
 * So the subject here is BOTH halves: the bytes come back, and nothing else
 * does.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class IssueArchiveStreamTest {

    @Inject
    IssueArchiveService archives;

    @Inject
    IssueLifecycleService lifecycle;

    @Inject
    IssuePublishService publishService;

    @Inject
    IssueAuditService auditService;

    @Inject
    IssuePreviewService previews;

    @Inject
    PublicationPathService paths;

    @Inject
    EntityManager em;

    // ------------------------------------------------------------------ fixtures

    private PublicationSeries series() {
        PublicationCategory c = new PublicationCategory();
        c.setCategoryId(TestIds.category());
        c.setPriority(100);
        em.persist(c);

        PublicationSeries s = new PublicationSeries();
        s.setSeriesId(TestIds.series());
        s.setStatus(SeriesStatus.ACTIVE);
        s.setContentMode(ContentMode.GENERATED_FROM_QUERY);
        s.setReportId("some-report");
        s.setCadence(SeriesCadence.WEEKLY);
        s.setTimeRelation(TimeRelation.PUBLISHED_IN_INTERVAL);
        s.setAliveAtCutoff(false);
        s.setReleaseMode(ReleaseMode.MANUAL_GATE);
        s.setNextIssueCreation(NextIssueCreation.MANUAL);
        s.setPublicAuthority(PublicAuthority.LEGACY);
        s.setMessagePublication(MessagePublication.NONE);
        s.setNumberingScheme(NumberingScheme.ISO_WEEK_YEAR);
        s.setCategory(c);
        // Every publication names the desk that owns it: the column is NOT NULL and
        // S-20a refuses a save without one, so a fixture that left it out no longer
        // describes a state the system can be in.
        s.setDomain(TestOwnerDomain.of(em));
        s.getLanguages().add("da");

        IssueCriteriaVo doc = new IssueCriteriaVo();
        MessageSeriesCriterionVo node = new MessageSeriesCriterionVo();
        node.setValues(new ArrayList<>(List.of("dma-nm")));
        doc.getCriteria().add(node);
        s.setCriteria(doc);

        s.createDesc("da").setName("Test series");
        em.persist(s);
        return s;
    }

    private User user() {
        User u = new User();
        u.setUsername(TestIds.user());
        em.persist(u);
        return u;
    }

    /**
     * A released issue whose Danish document holds the bytes handed in.
     *
     * Released the way an admin does after reading the preview -- regenerate
     * false, promoting exactly the reviewed bytes -- which is also what makes the
     * content of the archived file predictable: it is what went in here.
     */
    private PublicationIssue publishedIssue(String bytes) {
        PublicationSeries s = series();
        PublicationIssue i = lifecycle.create(s, new Date(1_699_000_000_000L),
                IntervalBoundSource.STAMPED, user());
        em.flush();
        previews.record(i, "da", "preview.pdf", bytes.getBytes(StandardCharsets.UTF_8));
        publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(false,
                        IssuePublishService.PublishRequest.ALL_WARNINGS, user(),
                        new Date(1_700_000_000_000L)));
        em.flush();
        return em.find(PublicationIssue.class, i.getId());
    }

    /** The entry an amend wrote, which is the one carrying the archive. */
    private IssueAuditEntry amendOf(PublicationIssue issue, String replacementBytes) {
        previews.record(issue, "da", "preview.pdf",
                replacementBytes.getBytes(StandardCharsets.UTF_8));
        publishService.amend(issue.getId(),
                new IssuePublishService.AmendRequest(false,
                        IssuePublishService.PublishRequest.ALL_WARNINGS, user(),
                        "a chart number was wrong in three of the notices"));
        em.flush();
        return auditService.forIssue(issue).stream()
                .filter(a -> AuditAction.AMENDED == a.getAction())
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("the amend wrote no AMENDED entry"));
    }

    private static void assertRefused(String why, Runnable call) {
        IssueLifecycleService.TransitionRefusedException refusal =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class, call::run, why);
        assertEquals(IssueArchiveService.NOT_FOUND, refusal.code(),
                why + " -- and it must refuse with the archive code, which is the 404 every one of "
                        + "these answers with; any other code tells the caller which of them it was");
    }

    // ================================================================== the read

    /**
     * The bytes an amend superseded come back, under the name they were published
     * with.
     *
     * The whole point of the store, asserted end to end rather than on the writer
     * alone: the writer's own test can only say a file appeared somewhere.
     */
    @Test
    @Transactional
    public void theSupersededDocumentComesBackWithItsOwnBytes() throws Exception {
        PublicationIssue issue = publishedIssue("the released edition");
        String publishedName = issue.getDescs().get(0).getFileName();
        IssueAuditEntry amend = amendOf(issue, "the corrected edition");

        IssueArchiveService.ArchivedFile file = archives.resolve(issue, amend.getId(), "da");

        assertEquals("the released edition", Files.readString(file.path()),
                "the archive handed back something other than the bytes the amend replaced");
        assertEquals("da", file.lang());
        assertEquals(publishedName, file.fileName(),
                "the file is offered under the stamped name it is stored under rather than the name "
                        + "it had when somebody cited it; every generation carries a stamp so two of "
                        + "them can sit side by side, and that stamp is bookkeeping");
        assertEquals(Files.size(file.path()), file.sizeBytes());

        assertTrue(file.path().toAbsolutePath().normalize()
                        .startsWith(paths.archiveRoot().toAbsolutePath().normalize()),
                "the resolved file is outside the archive root");
        assertFalse(file.path().toAbsolutePath().normalize()
                        .startsWith(paths.repoRoot().toAbsolutePath().normalize()),
                "a superseded edition resolved under the served repository root, where anybody can "
                        + "read it without asking this endpoint at all");
    }

    /**
     * The live document is untouched by the read, and still holds the correction.
     *
     * Named because the two files have the same name and differ only in where
     * they live: a resolver that walked back to the repository would return the
     * corrected document and look entirely successful doing it.
     */
    @Test
    @Transactional
    public void theArchiveAndTheLiveDocumentAreDifferentFiles() throws Exception {
        PublicationIssue issue = publishedIssue("the released edition");
        IssueAuditEntry amend = amendOf(issue, "the corrected edition");

        IssueArchiveService.ArchivedFile archived = archives.resolve(issue, amend.getId(), "da");
        Path live = paths.repoRoot().resolve(issue.getDescs().get(0).getFilePath());

        assertEquals("the corrected edition", Files.readString(live));
        assertEquals("the released edition", Files.readString(archived.path()));
    }

    // ============================================================== the refusals

    /**
     * An audit entry of ANOTHER issue is not found, and is not forbidden.
     *
     * The distinction is the whole refusal design. "Not yours" has already
     * confirmed the row exists, so an admin of one domain could walk the id space
     * and learn how many amendments every other issue in the system has had. The
     * only safe answer is the one that says nothing.
     */
    @Test
    @Transactional
    public void anEntryOfAnotherIssueIsNotFound() {
        PublicationIssue mine = publishedIssue("mine");
        PublicationIssue theirs = publishedIssue("theirs");
        IssueAuditEntry theirAmend = amendOf(theirs, "their correction");

        assertRefused("an entry belonging to another issue resolved against this one",
                () -> archives.resolve(mine, theirAmend.getId(), "da"));

        // And the entry really does resolve for the issue it belongs to, so the
        // refusal above is about ownership rather than about a broken fixture.
        assertNotNull(archives.resolve(theirs, theirAmend.getId(), "da"));
    }

    /** A language the entry did not archive is not found either. */
    @Test
    @Transactional
    public void aLanguageTheEntryDidNotArchiveIsNotFound() {
        PublicationIssue issue = publishedIssue("the released edition");
        IssueAuditEntry amend = amendOf(issue, "the corrected edition");

        assertRefused("a language the entry never archived resolved to a file",
                () -> archives.resolve(issue, amend.getId(), "en"));
    }

    /**
     * An entry that archived nothing has nothing to hand back.
     *
     * Most entries are this: a create, a rename, a curation. The endpoint accepts
     * any entry id rather than only the three archiving actions -- the actions are
     * a moving list and the presence of an archive is the fact that matters -- so
     * the ordinary entry has to be refused on that fact rather than on its action.
     */
    @Test
    @Transactional
    public void anEntryThatArchivedNothingIsNotFound() {
        PublicationIssue issue = publishedIssue("the released edition");
        IssueAuditEntry created = auditService.forIssue(issue).stream()
                .filter(a -> a.getArchivePath() == null)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no entry without an archive to test with"));

        assertRefused("an entry that preserved nothing resolved to a file",
                () -> archives.resolve(issue, created.getId(), "da"));
    }

    /** An id that is no entry at all. */
    @Test
    @Transactional
    public void anUnknownEntryIdIsNotFound() {
        PublicationIssue issue = publishedIssue("the released edition");

        assertRefused("an audit id that exists nowhere resolved to a file",
                () -> archives.resolve(issue, Integer.MAX_VALUE, "da"));
    }

    /**
     * A stored location outside the archive root is refused even though the file
     * is there.
     *
     * The column is data. It was written by a release that may be years old, by
     * code that no longer runs, and it is turned back into a file handle exactly
     * here -- so it is re-checked here rather than trusted. The fixture points a
     * crafted entry at a real file under the SERVED repository root: the file
     * exists and is readable, so nothing but the containment rule can refuse it.
     */
    @Test
    @Transactional
    public void aLocationOutsideTheArchiveRootIsNotFound() throws Exception {
        PublicationIssue issue = publishedIssue("the released edition");

        Path outside = paths.repoRoot()
                .resolve(TestIds.id("archive-escape-"))
                .resolve("da").resolve("1700000000000-leaked.pdf");
        Files.createDirectories(outside.getParent());
        Files.writeString(outside, "not the archive");
        assertTrue(Files.isRegularFile(outside), "the fixture's file was not written");

        IssueAuditEntry crafted = crafted(issue, outside.toString());

        assertRefused("a stored location under the served repository root was streamed",
                () -> archives.resolve(issue, crafted.getId(), "da"));
    }

    /** And the same for one that walks out of the root with relative segments. */
    @Test
    @Transactional
    public void aLocationThatWalksOutOfTheArchiveRootIsNotFound() throws Exception {
        PublicationIssue issue = publishedIssue("the released edition");

        Path escape = paths.archiveRoot().resolve("..")
                .resolve(TestIds.id("archive-escape-"))
                .resolve("da").resolve("1700000000000-leaked.pdf");
        Files.createDirectories(escape.getParent());
        Files.writeString(escape, "not the archive");

        IssueAuditEntry crafted = crafted(issue, escape.toString());

        assertRefused("a location walking out of the archive root with .. was streamed",
                () -> archives.resolve(issue, crafted.getId(), "da"));
    }

    /**
     * An entry that says it archived, over a file that is gone.
     *
     * The trail is the record and the file is the evidence, and they can come
     * apart -- a restore that missed a volume, a sweep somebody wrote by hand.
     * The caller is told the same nothing as every other refusal; the operational
     * fact belongs in the log, which is what the resolver writes.
     */
    @Test
    @Transactional
    public void anArchivedFileThatIsNoLongerOnDiskIsNotFound() throws Exception {
        PublicationIssue issue = publishedIssue("the released edition");
        IssueAuditEntry amend = amendOf(issue, "the corrected edition");

        Path archived = archives.resolve(issue, amend.getId(), "da").path();
        Files.delete(archived);

        assertRefused("an entry whose archived file is gone still resolved",
                () -> archives.resolve(issue, amend.getId(), "da"));
    }

    // ================================================================ the wire

    /**
     * The history line says which languages it superseded, and never where they
     * went.
     *
     * The panel needs the pair {entry, language} and nothing else, because that
     * pair IS the address. The location is a path outside the served root, and a
     * client holding one would either try to fetch it -- nothing serves it -- or
     * store it, and both are wrong about what the archive is.
     */
    @Test
    @Transactional
    public void theHistoryLineNamesTheLanguagesAndNotTheLocation() {
        PublicationIssue issue = publishedIssue("the released edition");
        String publishedName = issue.getDescs().get(0).getFileName();
        IssueAuditEntry amend = amendOf(issue, "the corrected edition");

        IssueArchiveVo archive = amend.toVo().getArchive();
        assertNotNull(archive, "the amend's history line carries no archive, so nothing can open it");
        assertEquals(amend.getId(), archive.getAuditEntryId(),
                "the archive must name the entry that wrote it; it is the only thing that "
                        + "distinguishes two amendments of the same language");
        assertEquals(1, archive.getFiles().size());
        assertEquals("da", archive.getFiles().get(0).getLang());
        assertEquals(publishedName, archive.getFiles().get(0).getFileName());

        // The location is absent by construction: there is no field for it.
        for (java.lang.reflect.Field f : archive.getFiles().get(0).getClass().getDeclaredFields()) {
            assertFalse(f.getName().toLowerCase().contains("path"),
                    "the archive file shape declares '" + f.getName() + "'. A filesystem path outside "
                            + "the served root has no business on the wire, and a field that is not "
                            + "there cannot leak through a mapping somebody adds later.");
        }
    }

    /** An entry that archived nothing carries no archive object at all. */
    @Test
    @Transactional
    public void anEntryWithoutAnArchiveCarriesNoArchiveObject() {
        PublicationIssue issue = publishedIssue("the released edition");
        IssueAuditEntry created = auditService.forIssue(issue).stream()
                .filter(a -> a.getArchivePath() == null)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no entry without an archive to test with"));

        assertNull(created.toVo().getArchive(),
                "an empty archive object on every ordinary history line makes the panel guess which "
                        + "lines have something to open");
    }

    // ---------------------------------------------------------------- crafting

    /** An entry claiming to have archived to a location this test chose. */
    private IssueAuditEntry crafted(PublicationIssue issue, String archivePath) {
        IssueAuditEntry entry = new IssueAuditEntry();
        entry.setIssue(issue);
        entry.setAction(AuditAction.AMENDED);
        entry.setActorKind(ActorKind.SYSTEM);
        entry.setArchivePath(archivePath);
        em.persist(entry);
        em.flush();
        return entry;
    }
}
