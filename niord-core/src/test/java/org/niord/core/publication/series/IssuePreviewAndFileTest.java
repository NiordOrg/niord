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
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.series.vo.IssuePreviewVo;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.core.user.User;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Previews, and per-language file upload and clear. */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class IssuePreviewAndFileTest {

    @Inject
    IssuePreviewService previews;

    @Inject
    IssueFileService files;

    @Inject
    IssueLifecycleService lifecycle;

    @Inject
    IssuePublishService publishService;

    @Inject
    PublicationPathService paths;

    @Inject
    IssueAuditService audit;

    @Inject
    EntityManager em;

    /**
     * The stubbed renderer is one bean for the whole module and what it produces
     * is a static, so a test that changes it and does not put it back leaves
     * every later test in the JVM rendering somebody else's document.
     */
    @org.junit.jupiter.api.AfterEach
    public void restoreTheRenderer() {
        StubIssueRenderService.reset();
    }

    /**
     * An OPEN issue whose series is configured for two languages.
     *
     * D-3 is a rule ACROSS languages, so it cannot be shown on the single-language
     * issue the rest of this suite uses.
     */
    private PublicationIssue anIssueInTwoLanguages() {
        PublicationIssue issue = anIssue();
        PublicationSeries s = issue.getSeries();
        if (!s.getLanguages().contains("en")) {
            s.getLanguages().add("en");
            s.createDesc("en").setName("Test series");
        }
        issue.createDesc("en").setName("Test issue");
        em.flush();
        return issue;
    }

    /**
     * A distinct file name per language, which a two-language release needs.
     *
     * Without a pattern every language falls back to the issue's public id, so
     * both would be written to one path -- a collision that says nothing about
     * the render and would hide everything these two tests are about.
     */
    private void namePerLanguage(PublicationSeries s) {
        for (PublicationSeriesDesc d : s.getDescs()) {
            d.setFileNamePattern("doc-" + d.getLang() + ".pdf");
        }
    }

    private PublicationIssue anIssue() {
        PublicationCategory c = new PublicationCategory();
        c.setCategoryId(TestIds.category());
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
        s.setMessagePublication(MessagePublication.NONE);
        s.setNumberingScheme(NumberingScheme.ISO_WEEK_YEAR);
        s.setCategory(c);
        // Every publication names the desk that owns it: the column is NOT NULL and
        // S-20a refuses a save without one, so a fixture that left it out no longer
        // describes a state the system can be in.
        s.setDomain(TestOwnerDomain.of(em));
        s.getLanguages().add("da");
        s.createDesc("da").setName("Test series");
        em.persist(s);

        User u = new User();
        u.setUsername(TestIds.user());
        em.persist(u);

        PublicationIssue i = lifecycle.create(s, new Date(1_699_000_000_000L),
                IntervalBoundSource.STAMPED, u);
        em.flush();
        return i;
    }

    private User user() {
        User u = new User();
        u.setUsername(TestIds.user());
        em.persist(u);
        return u;
    }

    // ================================================================= previews

    /** Nothing rendered is no row at all, so a caller cannot mistake an absence for a file. */
    @Test
    @Transactional
    public void anIssueWithNothingRenderedReportsNoPreviewRows() {
        PublicationIssue issue = anIssue();

        assertTrue(previews.newest(issue, "da").isEmpty(),
                "a language with nothing on disk answered with a generation");
        assertTrue(previews.stored(issue).isEmpty(),
                "an issue nobody has previewed reported a row the download endpoint cannot serve");

        previews.record(issue, "da", "test.pdf", "first".getBytes(StandardCharsets.UTF_8));

        List<IssuePreviewVo> stored = previews.stored(issue);
        assertEquals(1, stored.size(), "the recorded generation did not reach the stored rows");
        assertEquals("da", stored.get(0).lang());
        assertEquals(previews.newest(issue, "da").orElseThrow().renderedAt().getTime(),
                stored.get(0).renderedAt(),
                "the row names an instant other than the generation it stands for");
    }

    /** Generations accumulate rather than overwrite, so preview-then-compare works. */
    @Test
    @Transactional
    public void generationsAccumulateAndTheNewestWins() throws Exception {
        PublicationIssue issue = anIssue();

        previews.record(issue, "da", "test.pdf", "first".getBytes(StandardCharsets.UTF_8));
        Thread.sleep(5);
        IssuePreviewService.Preview second =
                previews.record(issue, "da", "test.pdf", "second".getBytes(StandardCharsets.UTF_8));

        Path dir = paths.previewRoot().resolve(issue.getPublicId()).resolve("da");
        try (var list = Files.list(dir)) {
            assertTrue(list.count() >= 2, "the second generation overwrote the first");
        }

        assertEquals(second.generation(), previews.newest(issue, "da").orElseThrow().generation(),
                "the newest generation is not the one that comes back");
    }

    @Test
    @Transactional
    public void theSweepRemovesOnlyExpiredGenerations() {
        PublicationIssue issue = anIssue();
        previews.record(issue, "da", "test.pdf", "recent".getBytes(StandardCharsets.UTF_8));

        assertEquals(0, previews.sweep(new Date()), "the sweep removed a generation inside its TTL");

        Date farFuture = new Date(System.currentTimeMillis() + 2 * IssuePreviewService.PREVIEW_TTL_MILLIS);
        assertTrue(previews.sweep(farFuture) >= 1, "the sweep left an expired generation behind");
    }

    /** X-6 again, from the other side: previews are not written under the served root. */
    @Test
    @Transactional
    public void previewsAreWrittenOutsideTheServedRepository() {
        PublicationIssue issue = anIssue();
        IssuePreviewService.Preview p =
                previews.record(issue, "da", "test.pdf", "bytes".getBytes(StandardCharsets.UTF_8));

        Path repo = paths.repoRoot().toAbsolutePath().normalize();
        Path written = p.path().toAbsolutePath().normalize();
        assertFalse(written.startsWith(repo),
                "a preview landed under the served repository root at " + written
                        + "; every unpublished draft would be publicly readable");
    }

    // ================================================================= files

    /** Upload on a PUBLISHED issue is the post-publish correction path, and it archives first. */
    @BindsRule({"D-6"})
    @Test
    @Transactional
    public void uploadingOntoAPublishedIssueArchivesWhatItReplaces() throws Exception {
        PublicationIssue issue = anIssue();

        files.upload(issue, "da", "first.pdf", "original".getBytes(StandardCharsets.UTF_8), user());
        em.flush();

        publishService.publish(issue.getId(),
                new IssuePublishService.PublishRequest(IssuePublishService.PublishRequest.ALL_WARNINGS, null, new Date(1_700_000_000_000L)));
        em.flush();

        PublicationIssue published = em.find(PublicationIssue.class, issue.getId());
        assertEquals(IssueStatus.PUBLISHED, published.getStatus());
        PublicationIssueDesc da = published.getDescs().stream()
                .filter(d -> "da".equals(d.getLang())).findFirst().orElseThrow();
        assertNull(da.getReplacedBy(), "a first upload replaces nothing, and records no corrector");
        assertNull(da.getReplacedAt());

        // The correction. Legal on a published issue by design.
        User corrector = user();
        files.upload(published, "da", "first.pdf", "corrected".getBytes(StandardCharsets.UTF_8), corrector);
        em.flush();

        // The issue itself says its file is not the one that was published.
        assertEquals(corrector.getId(), da.getReplacedBy().getId());
        assertNotNull(da.getReplacedAt());

        Path archiveDir = paths.archiveRoot().resolve(published.getPublicId()).resolve("da");
        assertTrue(Files.isDirectory(archiveDir), "nothing was archived before the replacement was written");
        try (var list = Files.list(archiveDir)) {
            List<Path> archived = list.toList();
            assertFalse(archived.isEmpty(), "the replaced bytes were not archived");
            assertEquals("original", Files.readString(archived.get(0)),
                    "the archive does not hold the bytes that were replaced");
        }

        Path live = paths.repoRoot().resolve(published.getRepoPath()).resolve("first.pdf");
        assertEquals("corrected", Files.readString(live), "the correction was not written");
    }

    /** The sticky flag stops the next publish regenerating over a hand correction. */
    @Test
    @Transactional
    public void anUploadedFileIsStickySoPublishDoesNotOverwriteIt() {
        PublicationIssue issue = anIssue();
        files.upload(issue, "da", "hand.pdf", "by hand".getBytes(StandardCharsets.UTF_8), user());
        em.flush();

        PublicationIssueDesc desc = issue.getDescs().get(0);
        assertTrue(desc.isFileSourceSticky(),
                "the upload was not marked sticky; the next publish would regenerate over it");
        assertEquals(FileSource.UPLOADED, desc.getFileSource());
    }

    /** Clearing a published file would leave a dead link where a citation points. */
    @BindsRule({"D-5"})
    @Test
    @Transactional
    public void aPublishedFileCannotBeCleared() {
        PublicationIssue issue = anIssue();
        files.upload(issue, "da", "first.pdf", "original".getBytes(StandardCharsets.UTF_8), user());
        em.flush();

        // While OPEN, clearing is fine.
        files.clear(issue, "da", user());
        em.flush();
        assertNull(issue.getDescs().get(0).getFilePath());
        assertFalse(issue.getDescs().get(0).isFileSourceSticky(),
                "clearing left the sticky flag set, so the language would never regenerate");

        files.upload(issue, "da", "first.pdf", "original".getBytes(StandardCharsets.UTF_8), user());
        em.flush();
        publishService.publish(issue.getId(),
                new IssuePublishService.PublishRequest(IssuePublishService.PublishRequest.ALL_WARNINGS, null, new Date(1_700_000_000_000L)));
        em.flush();

        PublicationIssue published = em.find(PublicationIssue.class, issue.getId());
        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> files.clear(published, "da", user()));
        assertEquals("ISSUE_NOT_OPEN", e.code());
    }

    /**
     * A link is the document of an external publication, and it round-trips.
     *
     * Nothing could set one before this, so every EXTERNAL_LINK issue resolved to
     * nothing -- the link-shaped equivalent of an upload path that did not exist.
     */
    @Test
    @Transactional
    public void alinkIsStoredAndClearedPerLanguage() {
        PublicationIssue issue = anIssue();

        files.setLink(issue, "da", "https://example.test/skydeomraader.pdf", user());
        em.flush();
        assertEquals("https://example.test/skydeomraader.pdf", issue.getDescs().get(0).getLink());

        files.setLink(issue, "da", null, user());
        em.flush();
        assertNull(issue.getDescs().get(0).getLink(),
                "clearing left the old address in place, so the issue still points at it");
    }

    /**
     * A blank link is nothing, not an empty address.
     *
     * A desc holding "" reports itself as a LINK publication -- IssuePublicationMapping
     * types an issue by whether any desc has a link -- and the result is a
     * publication that claims to be external and resolves nowhere.
     */
    @Test
    @Transactional
    public void ablankLinkIsStoredAsNoLink() {
        PublicationIssue issue = anIssue();

        files.setLink(issue, "da", "   ", user());
        em.flush();

        assertNull(issue.getDescs().get(0).getLink());
    }

    /** Surrounding whitespace is not part of an address. */
    @Test
    @Transactional
    public void alinkIsTrimmed() {
        PublicationIssue issue = anIssue();

        files.setLink(issue, "da", "  https://example.test/a.pdf  ", user());
        em.flush();

        assertEquals("https://example.test/a.pdf", issue.getDescs().get(0).getLink());
    }

    /**
     * A published issue's link is editable, unlike its file being clearable.
     *
     * The asymmetry is deliberate. A wrong address on the public site has to be
     * correctable without retiring the issue, because retiring changes what the
     * record says happened -- and unlike clearing a file, replacing a link leaves
     * a working address rather than a dead one.
     */
    @Test
    @Transactional
    public void apublishedIssueMayHaveItsLinkCorrected() {
        PublicationIssue issue = anIssue();
        files.upload(issue, "da", "first.pdf", "original".getBytes(StandardCharsets.UTF_8), user());
        em.flush();
        publishService.publish(issue.getId(),
                new IssuePublishService.PublishRequest(IssuePublishService.PublishRequest.ALL_WARNINGS, null, new Date(1_700_000_000_000L)));
        em.flush();

        PublicationIssue published = em.find(PublicationIssue.class, issue.getId());
        files.setLink(published, "da", "https://example.test/corrected.pdf", user());
        em.flush();

        assertEquals("https://example.test/corrected.pdf", published.getDescs().get(0).getLink());
    }

    /** A language the series is not configured for has no desc row to write to. */
    @Test
    @Transactional
    public void uploadingForAnUnconfiguredLanguageIsRefused() {
        PublicationIssue issue = anIssue();
        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> files.upload(issue, "de", "x.pdf", "x".getBytes(StandardCharsets.UTF_8), user()));
        assertEquals("NO_SUCH_LANGUAGE", e.code());
    }
    /**
     * D-3: one file name per issue, across ALL its languages.
     *
     * Every language writes into the same repoPath, so two languages sharing a
     * name are two languages sharing a file: whichever uploads second overwrites
     * the first, and the issue then serves the Danish PDF to an English reader
     * with nothing anywhere recording that it happened.
     *
     * Left unimplemented until the invariant-binding pass -- the rule was pending on a task which
     * completed without it.
     */
    @BindsRule({"D-3"})
    @Test
    @Transactional
    public void twoLanguagesMayNotShareAFileName() {
        PublicationIssue issue = anIssueInTwoLanguages();

        files.upload(issue, "da", "shared.pdf", "dansk".getBytes(StandardCharsets.UTF_8), user());

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> files.upload(issue, "en", "shared.pdf",
                                "english".getBytes(StandardCharsets.UTF_8), user()));
        assertEquals("FILE_NAME_NOT_DISTINCT", e.code());

        // A distinct name is fine, which is what makes the refusal about the
        // collision rather than about uploading twice.
        assertNotNull(files.upload(issue, "en", "distinct.pdf",
                "english".getBytes(StandardCharsets.UTF_8), user()));
    }

    /** Re-uploading the SAME language keeps its own name; D-3 is across languages. */
    @BindsRule({"D-3"})
    @Test
    @Transactional
    public void alanguageMayReuseItsOwnFileName() {
        PublicationIssue issue = anIssueInTwoLanguages();

        files.upload(issue, "da", "same.pdf", "first".getBytes(StandardCharsets.UTF_8), user());
        assertNotNull(files.upload(issue, "da", "same.pdf",
                "replacement".getBytes(StandardCharsets.UTF_8), user()),
                "replacing a language's own file is a replacement, not a collision");
    }

    /**
     * A REFUSED upload writes no bytes at all.
     *
     * The sharp half of D-3, and it is about ordering rather than about the rule.
     * The write used to run before the checks, so a collision overwrote the other
     * language's document and then answered with an error -- the caller was told
     * no and the file was gone. A transaction cannot undo that: the rollback
     * reverts the row and leaves the bytes.
     */
    @BindsRule({"D-3"})
    @Test
    @Transactional
    public void arefusedUploadLeavesTheOtherLanguagesBytesUntouched() throws Exception {
        PublicationIssue issue = anIssueInTwoLanguages();

        files.upload(issue, "da", "shared.pdf", "dansk".getBytes(StandardCharsets.UTF_8), user());
        em.flush();

        Path written = paths.repoRoot().resolve(issue.getRepoPath()).resolve("shared.pdf");
        assertEquals("dansk", Files.readString(written));

        assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                () -> files.upload(issue, "en", "shared.pdf",
                        "english".getBytes(StandardCharsets.UTF_8), user()));

        assertEquals("dansk", Files.readString(written),
                "the refused upload had already overwritten the Danish document. The guard has to "
                        + "run before the write, because nothing rolls a file back.");
    }

    /**
     * A published issue's correction goes to the SAME path, under the same name.
     *
     * The address is the citation. A stored citation is a URL into this file, and
     * an imported issue keeps the legacy layout -- revision segment and all -- so
     * a path re-derived from the repo root would write beside the cited document
     * rather than over it, leaving the download link and the citations pointing at
     * different bytes.
     */
    @Test
    @Transactional
    public void apublishedIssuesReplacementKeepsItsPath() throws Exception {
        PublicationIssue issue = anIssue();
        files.upload(issue, "da", "first.pdf", "original".getBytes(StandardCharsets.UTF_8), user());
        em.flush();
        publishService.publish(issue.getId(),
                new IssuePublishService.PublishRequest(
                        IssuePublishService.PublishRequest.ALL_WARNINGS, null,
                        new Date(1_700_000_000_000L)));
        em.flush();

        PublicationIssue published = em.find(PublicationIssue.class, issue.getId());
        PublicationIssueDesc da = published.getDescs().stream()
                .filter(d -> "da".equals(d.getLang())).findFirst().orElseThrow();
        String pathBefore = da.getFilePath();
        String nameBefore = da.getFileName();

        files.upload(published, "da", "first.pdf",
                "corrected".getBytes(StandardCharsets.UTF_8), user());
        em.flush();

        assertEquals(pathBefore, da.getFilePath(), "the published address moved");
        assertEquals(nameBefore, da.getFileName(), "the published file name moved");
        assertEquals("corrected",
                Files.readString(paths.repoRoot().resolve(da.getFilePath())),
                "the correction was written somewhere other than the cited path");
    }

    /**
     * And a differently named replacement is refused rather than accepted quietly.
     *
     * Accepting it would leave the old bytes at the cited URL and the new ones at
     * an address nothing points at -- the worst of the three outcomes, because
     * both the upload and the download appear to work.
     */
    @Test
    @Transactional
    public void apublishedIssueRefusesAReplacementUnderANewName() {
        PublicationIssue issue = anIssue();
        files.upload(issue, "da", "first.pdf", "original".getBytes(StandardCharsets.UTF_8), user());
        em.flush();
        publishService.publish(issue.getId(),
                new IssuePublishService.PublishRequest(
                        IssuePublishService.PublishRequest.ALL_WARNINGS, null,
                        new Date(1_700_000_000_000L)));
        em.flush();

        PublicationIssue published = em.find(PublicationIssue.class, issue.getId());
        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> files.upload(published, "da", "renamed.pdf",
                                "corrected".getBytes(StandardCharsets.UTF_8), user()));
        assertEquals("FILE_NAME_IMMUTABLE", e.code());
    }

    /**
     * The correction is audited as a REPLACEMENT, carrying the archive path.
     *
     * "FILE_UPLOADED" against a published issue tells a reader nothing about
     * whether a document appeared or a cited one was overwritten, and the archive
     * path on the entry is the only route back to what the public was reading
     * before.
     */
    @Test
    @Transactional
    public void replacingAPublishedFileIsAuditedAsAReplacement() {
        PublicationIssue issue = anIssue();
        files.upload(issue, "da", "first.pdf", "original".getBytes(StandardCharsets.UTF_8), user());
        em.flush();
        publishService.publish(issue.getId(),
                new IssuePublishService.PublishRequest(
                        IssuePublishService.PublishRequest.ALL_WARNINGS, null,
                        new Date(1_700_000_000_000L)));
        em.flush();

        PublicationIssue published = em.find(PublicationIssue.class, issue.getId());
        files.upload(published, "da", "first.pdf",
                "corrected".getBytes(StandardCharsets.UTF_8), user());
        em.flush();

        // The trail comes back newest first, so the entry the upload just wrote is
        // the FIRST match rather than the last.
        IssueAuditEntry replacement = audit.forIssue(published).stream()
                .filter(a -> AuditAction.FILE_REPLACED_MANUALLY == a.getAction())
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "the replacement was not audited as one; the trail shows an upload, which "
                                + "reads as a document appearing rather than a cited one being "
                                + "overwritten"));
        assertNotNull(replacement.getArchivePath(),
                "the entry carries no archive path, so nothing can reach the superseded bytes");
        assertEquals("FILE_REPLACED_MANUALLY", replacement.toVo().getAction(),
                "the trail has to name the replacement as one on the wire too, not just in memory");
    }

    // ============================================== the languages of one release

    /**
     * Two languages, drawn side by side, and each one's bytes reach its own file.
     *
     * The languages of a document are not drawn one after the other: the requests
     * are built on the publishing thread, the documents are drawn at the same
     * time, and the bytes come back in a list the release then writes from.
     * Everything about that is invisible from the outside EXCEPT the one thing
     * that could go wrong -- a list that slipped out of step with the languages
     * would serve the Danish document to an English reader with nothing anywhere
     * recording that it happened.
     *
     * So the stub is asked for bytes that name their own language, and each
     * language's official file has to hold its own.
     */
    @Test
    @Transactional
    public void bothLanguagesOfOneReleaseGetTheirOwnDocument() throws Exception {
        PublicationIssue issue = anIssueInTwoLanguages();
        namePerLanguage(issue.getSeries());
        em.flush();

        StubIssueRenderService.rendersPerLanguage();
        publishService.publish(issue.getId(),
                new IssuePublishService.PublishRequest(
                        IssuePublishService.PublishRequest.ALL_WARNINGS, null,
                        new Date(1_700_000_000_000L)));
        em.flush();

        PublicationIssue published = em.find(PublicationIssue.class, issue.getId());
        assertEquals(IssueStatus.PUBLISHED, published.getStatus());
        for (String lang : List.of("da", "en")) {
            PublicationIssueDesc desc = published.getDescs().stream()
                    .filter(d -> lang.equals(d.getLang())).findFirst().orElseThrow();
            assertEquals(FileSource.GENERATED, desc.getFileSource(),
                    "language " + lang + " was not generated");
            assertEquals("doc-" + lang + ".pdf", desc.getFileName(),
                    "language " + lang + " was written under another language's name");
            assertEquals(StubIssueRenderService.DEFAULT_BODY + ":" + lang,
                    Files.readString(paths.repoRoot().resolve(desc.getFilePath())),
                    "the file " + desc.getFileName() + " holds another language's document; the "
                            + "bytes the languages were drawn into came back out of step with them");
        }
    }

    /**
     * What each language's render is given: another thread, no transaction, and a
     * persistence context of its own.
     *
     * This is the whole safety argument of drawing the languages side by side,
     * and none of it can be read off the document. A worker that kept the
     * publish's TRANSACTION would be a second thread inside the session every
     * frozen member was read into. A worker that kept the caller's CDI REQUEST
     * CONTEXT would land on the request-scoped session instead -- the same
     * hazard by the other route, because without a transaction that is the
     * session the persistence layer falls back to -- and two workers sharing it
     * would be two threads in one session again. And a worker with NO request
     * context at all cannot read a row at all: the report and the templates come
     * out of the database, and the first thing the render does is ask for them.
     *
     * So all three are asserted, from inside the render, where they are true.
     */
    @Test
    @Transactional
    public void eachLanguageIsDrawnOnItsOwnThreadWithItsOwnPersistenceContext() {
        PublicationIssue issue = anIssueInTwoLanguages();
        namePerLanguage(issue.getSeries());
        em.flush();

        String caller = Thread.currentThread().getName();
        Object callersContext = io.quarkus.arc.Arc.container().requestContext().getStateIfActive();

        StubIssueRenderService.readsTheDatabase();
        publishService.publish(issue.getId(),
                new IssuePublishService.PublishRequest(
                        IssuePublishService.PublishRequest.ALL_WARNINGS, null,
                        new Date(1_700_000_000_000L)));
        em.flush();

        List<StubIssueRenderService.RenderThread> threads = StubIssueRenderService.threads();
        assertEquals(2, threads.size(), "both languages should have been rendered");
        for (StubIssueRenderService.RenderThread t : threads) {
            assertFalse(caller.equals(t.thread()),
                    "language " + t.lang() + " was drawn on the publishing thread, so the languages "
                            + "are still rendered one after the other");
            assertFalse(t.inTransaction(),
                    "language " + t.lang() + " was drawn inside the publish transaction; its reads "
                            + "would share the session the frozen members were read into");
            assertNotNull(t.requestContext(),
                    "language " + t.lang() + " was drawn with no CDI request context, so the render "
                            + "cannot read the report row or the template rows at all");
            assertNotSame(callersContext, t.requestContext(),
                    "language " + t.lang() + " kept the publishing request's context, and with no "
                            + "transaction that is the caller's request-scoped session");
            assertNull(t.databaseFailure(),
                    "language " + t.lang() + " could not read a row on the thread it was given: "
                            + t.databaseFailure());
        }
        assertNotEquals(threads.get(0).thread(), threads.get(1).thread(),
                "both languages were drawn on one thread, so nothing overlapped");
        assertNotSame(threads.get(0).requestContext(), threads.get(1).requestContext(),
                "the two languages shared one request context, so they shared one session");
    }

    /**
     * One language that cannot be drawn fails the whole release, and writes nothing.
     *
     * A release is one transaction, but a written file is not part of it: the
     * rollback takes the rows back and leaves the bytes. So the point at which a
     * failing language must stop the release is BEFORE any language's document
     * reaches the repository -- otherwise a release that ended in an exception
     * still leaves a Danish PDF sitting under the issue's public path, and the
     * next attempt writes its second generation over a first nobody knows about.
     */
    @Test
    @Transactional
    public void arenderFailureInOneLanguageFailsTheReleaseAndLeavesNoFileBehind() throws Exception {
        PublicationIssue issue = anIssueInTwoLanguages();
        namePerLanguage(issue.getSeries());
        em.flush();

        StubIssueRenderService.fails("en");
        assertThrows(IssueRenderService.RenderFailedException.class,
                () -> publishService.publish(issue.getId(),
                        new IssuePublishService.PublishRequest(
                                IssuePublishService.PublishRequest.ALL_WARNINGS, null,
                                new Date(1_700_000_000_000L))));

        assertEquals(IssueStatus.OPEN, issue.getStatus(),
                "a release that could not draw one of its languages flipped the status anyway");
        Path dir = paths.repoRoot().resolve(issue.getRepoPath());
        if (Files.isDirectory(dir)) {
            try (var list = Files.list(dir)) {
                assertEquals(List.of(), list.map(p -> p.getFileName().toString()).sorted().toList(),
                        "the language that rendered left its document behind before the other one "
                                + "failed; nothing rolls a file back");
            }
        }
    }

}
