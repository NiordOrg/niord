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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.TestIds;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.criteria.MessageSeriesCriterionVo;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.series.vo.SystemPublicationIssueDescVo;
import org.niord.core.publication.series.vo.SystemPublicationIssueVo;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.core.user.User;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The file name one edition's document is written under.
 *
 * The series names the file by a pattern and publish expands it, and the pattern
 * ALWAYS won: the language's own file name was consulted only where the series
 * named no pattern at all. So an edition that had to be filed under something
 * else could not be, except by uploading a document by hand -- which sets the
 * sticky flag and stops the release regenerating the file at all, which is a
 * different decision entirely.
 *
 * What the flag buys is the difference between a name that RENDERS the period
 * and a name somebody DECIDED. The column alone cannot tell them apart: it also
 * holds the name of an uploaded correction and the name the last release
 * happened to write.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class IssueFileNameOverrideTest {

    /** 2026-09-02 12:00 UTC -- a Wednesday in ISO week 36 of 2026. */
    private static final long CUTOFF = 1_788_350_400_000L;

    private static final long WEEK = 7L * 24 * 3600 * 1000;

    @AfterEach
    public void restoreTheRenderer() {
        StubIssueRenderService.reset();
    }

    @Inject
    IssuePublishService publishService;

    @Inject
    IssueEditService editService;

    @Inject
    IssueAuditService audit;

    @Inject
    EntityManager em;

    // ------------------------------------------------------------------ fixture

    private PublicationSeries series(String... languages) {
        PublicationCategory c = new PublicationCategory();
        c.setCategoryId(TestIds.category());
        c.setPriority(100);
        c.setPublish(true);
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
        s.setDomain(TestOwnerDomain.of(em));

        IssueCriteriaVo doc = new IssueCriteriaVo();
        MessageSeriesCriterionVo node = new MessageSeriesCriterionVo();
        node.setValues(new ArrayList<>(List.of("dma-nm")));
        doc.getCriteria().add(node);
        s.setCriteria(doc);

        for (String lang : languages) {
            s.getLanguages().add(lang);
            PublicationSeriesDesc d = s.createDesc(lang);
            d.setName("Test series " + lang);
            // One pattern for both languages on purpose: it is what makes the
            // distinctness rule reachable, and D-3 exists because two languages
            // write into ONE folder.
            d.setFileNamePattern("EfS-" + lang + "-Uge-${week}-${year}.pdf");
        }
        em.persist(s);
        return s;
    }

    private PublicationIssue issue(PublicationSeries s) {
        PublicationIssue i = new PublicationIssue();
        i.setSeries(s);
        i.setPublicId(UUID.randomUUID().toString());
        i.setRepoPath("publications/" + i.getPublicId());
        i.setStatus(IssueStatus.OPEN);
        i.setIntervalFrom(new Date(CUTOFF - WEEK));
        i.setIntervalFromSource(IntervalBoundSource.STAMPED);
        for (String lang : s.getLanguages()) {
            PublicationIssueDesc d = i.createDesc(lang);
            d.setName("Test issue " + lang);
        }
        em.persist(i);
        return i;
    }

    private User user() {
        User u = new User();
        u.setUsername(TestIds.user());
        em.persist(u);
        return u;
    }

    private static IssueEditService.IssueEdit fileNames(Map<String, String> names) {
        return new IssueEditService.IssueEdit(null, null, null, null, null, false, null,
                null, null, null, names, null);
    }

    private PublicationIssueDesc descOf(PublicationIssue issue, String lang) {
        return issue.getDescs().stream().filter(d -> lang.equals(d.getLang())).findFirst().orElseThrow();
    }

    private void publish(PublicationIssue i) {
        publishService.publish(i.getId(), new IssuePublishService.PublishRequest(
                IssuePublishService.PublishRequest.ALL_WARNINGS, null, new Date(CUTOFF)));
        em.flush();
    }

    // -------------------------------------------------------------- the default

    /** Nothing set: the series' pattern names the file, exactly as it always did. */
    @Test
    @Transactional
    public void thepatternNamesTheFileWhenNobodyHasDecidedOtherwise() {
        PublicationIssue i = issue(series("da"));
        em.flush();

        publish(i);

        assertEquals("EfS-da-Uge-36-2026.pdf", descOf(i, "da").getFileName());
        assertFalse(descOf(i, "da").isFileNameOverridden());
    }

    // -------------------------------------------------------------------- W2

    /** A name somebody set is what the release writes -- the pattern no longer always wins. */
    @Test
    @Transactional
    public void ahandSetNameIsWhatThePublishWrites() {
        PublicationIssue i = issue(series("da"));
        em.flush();

        editService.update(i, fileNames(Map.of("da", "EfS-PT-Uge-36+37-2026.pdf")), user());
        em.flush();
        assertTrue(descOf(i, "da").isFileNameOverridden());

        publish(i);

        assertEquals("EfS-PT-Uge-36+37-2026.pdf", descOf(i, "da").getFileName(),
                "the release re-derived the name from the pattern and discarded the decision");
        assertEquals(i.getRepoPath() + "/EfS-PT-Uge-36+37-2026.pdf", descOf(i, "da").getFilePath());
    }

    /** Per language, and only the language that was named. */
    @Test
    @Transactional
    public void onlyTheLanguageThatWasNamedMoves() {
        PublicationIssue i = issue(series("da", "en"));
        em.flush();

        editService.update(i, fileNames(Map.of("da", "Noget-andet.pdf")), user());
        em.flush();
        publish(i);

        assertEquals("Noget-andet.pdf", descOf(i, "da").getFileName());
        assertEquals("EfS-en-Uge-36-2026.pdf", descOf(i, "en").getFileName(),
                "the English file name followed the Danish decision; they are separate documents");
    }

    /** A PDF, whatever was typed: it is what the release writes. */
    @Test
    @Transactional
    public void thenameIsAlwaysAPdf() {
        PublicationIssue i = issue(series("da"));
        em.flush();

        editService.update(i, fileNames(Map.of("da", "EfS-uge-36")), user());
        em.flush();

        assertEquals("EfS-uge-36.pdf", descOf(i, "da").getFileName());
    }

    /**
     * A BLANK name hands the language back to the series' pattern -- and leaves
     * the column alone, because that column names the file that EXISTS.
     */
    @Test
    @Transactional
    public void ablankNameGoesBackToThePattern() {
        PublicationIssue i = issue(series("da"));
        em.flush();
        editService.update(i, fileNames(Map.of("da", "Noget-andet.pdf")), user());
        em.flush();

        Map<String, String> clear = new LinkedHashMap<>();
        clear.put("da", "");
        editService.update(i, fileNames(clear), user());
        em.flush();

        assertFalse(descOf(i, "da").isFileNameOverridden(), "the override was not cleared");
        assertEquals("Noget-andet.pdf", descOf(i, "da").getFileName(),
                "clearing the override erased the name of the file that exists, which is a "
                        + "statement about the bytes on disk that nobody made");

        publish(i);
        assertEquals("EfS-da-Uge-36-2026.pdf", descOf(i, "da").getFileName(),
                "the release did not go back to the series' pattern");
    }

    /** D-3: both languages write into one folder, so one name cannot serve two. */
    @Test
    @Transactional
    public void twoLanguagesMayNotShareAName() {
        PublicationIssue i = issue(series("da", "en"));
        em.flush();

        Map<String, String> both = new LinkedHashMap<>();
        both.put("da", "Fælles.pdf");
        both.put("en", "Fælles.pdf");

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> editService.update(i, fileNames(both), user()));
        assertEquals("FILE_NAME_NOT_DISTINCT", e.code());
    }

    /** And the collision is judged against the names the OTHER languages already hold. */
    @Test
    @Transactional
    public void anameThatCollidesWithAnUntouchedLanguageIsRefused() {
        PublicationIssue i = issue(series("da", "en"));
        em.flush();
        editService.update(i, fileNames(Map.of("en", "Fælles.pdf")), user());
        em.flush();

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> editService.update(i, fileNames(Map.of("da", "Fælles.pdf")), user()));
        assertEquals("FILE_NAME_NOT_DISTINCT", e.code());
    }

    /**
     * A TRAVERSAL is stripped to its last segment, exactly as the upload strips
     * it -- and by the same code, which is why they cannot drift apart.
     *
     * The document is written inside the issue's own repository folder, so a name
     * carrying `..` or an absolute path would be resolved against it and write
     * outside. Stripping rather than refusing is the upload's long-standing
     * answer -- a browser posts a full path on some platforms -- and one rule for
     * both endpoints is the point of sharing it.
     */
    @Test
    @Transactional
    public void atraversalIsStrippedToItsLastSegment() {
        PublicationIssue i = issue(series("da"));
        em.flush();

        editService.update(i, fileNames(Map.of("da", "../../etc/passwd")), user());
        em.flush();

        assertEquals("passwd.pdf", descOf(i, "da").getFileName(),
                "a name resolving outside the issue's own folder reached the column");
    }

    /** A name that is ONLY a path leaves nothing to write to, and is refused. */
    @Test
    @Transactional
    public void anameThatIsOnlyAPathIsRefused() {
        PublicationIssue i = issue(series("da"));
        em.flush();

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> editService.update(i, fileNames(Map.of("da", "../..")), user()));
        assertEquals(IssueEditService.FILE_NAME_INVALID, e.code());
    }

    /** A directory prefix is stripped rather than refused, exactly as the upload strips it. */
    @Test
    @Transactional
    public void adirectoryPrefixIsStripped() {
        PublicationIssue i = issue(series("da"));
        em.flush();

        editService.update(i, fileNames(Map.of("da", "C:\\Users\\someone\\EfS.pdf")), user());
        em.flush();

        assertEquals("EfS.pdf", descOf(i, "da").getFileName());
    }

    /** A control character reaches a public download URL. */
    @Test
    @Transactional
    public void acontrolCharacterIsRefused() {
        PublicationIssue i = issue(series("da"));
        em.flush();

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> editService.update(i, fileNames(Map.of("da", "EfS\n36.pdf")), user()));
        assertEquals(IssueEditService.FILE_NAME_INVALID, e.code());
    }

    /** And one that would not fit the column. */
    @Test
    @Transactional
    public void anoverlongNameIsRefused() {
        PublicationIssue i = issue(series("da"));
        em.flush();
        String tooLong = "e".repeat(IssueEditService.MAX_FILE_NAME + 1) + ".pdf";

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> editService.update(i, fileNames(Map.of("da", tooLong)), user()));
        assertEquals(IssueEditService.FILE_NAME_INVALID, e.code());
    }

    /** A language the issue has no row for. */
    @Test
    @Transactional
    public void anunknownLanguageIsRefused() {
        PublicationIssue i = issue(series("da"));
        em.flush();

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> editService.update(i, fileNames(Map.of("de", "EfS.pdf")), user()));
        assertEquals("NO_SUCH_LANGUAGE", e.code());
    }

    /**
     * OPEN ONLY. A published document lives at an address that stored citations
     * point at; the upload path refuses to move it for the same reason.
     */
    @Test
    @Transactional
    public void apublishedIssueRefusesARename() {
        PublicationIssue i = issue(series("da"));
        em.flush();
        publish(i);

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> editService.update(i, fileNames(Map.of("da", "Andet.pdf")), user()));
        assertEquals("ISSUE_NOT_OPEN", e.code());
    }

    /** Its own action, and it says which language and which name. */
    @Test
    @Transactional
    public void thetrailRecordsTheRename() {
        PublicationIssue i = issue(series("da"));
        em.flush();
        editService.update(i, fileNames(Map.of("da", "Andet.pdf")), user());
        em.flush();

        IssueAuditEntry entry = audit.forIssue(i).stream()
                .filter(a -> a.getAction() == AuditAction.FILE_NAME_CHANGED)
                .findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> detail = (Map<String, Object>) entry.getDetail();
        assertEquals("da", detail.get("lang"));
        assertEquals("Andet.pdf", detail.get("to"));
    }

    // ------------------------------------------------------------------- wire

    /**
     * The editor shape says what the file WILL be called, and whether the name is
     * a decision.
     *
     * Until an edition publishes, fileName is empty -- the file does not exist --
     * and the screen still has to show a name. Deriving it in the client would be
     * a second implementation of the token vocabulary, which is how a screen
     * comes to promise one address while the release writes another.
     */
    @Test
    @Transactional
    public void thewireCarriesTheSuggestionAndTheFlag() {
        PublicationIssue i = issue(series("da"));
        i.setIntervalTo(new Date(CUTOFF));
        em.flush();

        SystemPublicationIssueVo before = i.toVo(SystemPublicationIssueVo.class);
        SystemPublicationIssueDescVo desc = (SystemPublicationIssueDescVo) before.getDescs().get(0);
        assertTrue(desc.getSuggestedFileName() != null && desc.getSuggestedFileName().endsWith(".pdf"),
                "the screen has no name to show for an edition that has not published yet: "
                        + desc.getSuggestedFileName());
        assertFalse(desc.isFileNameOverridden());
        assertFalse(desc.isNameOverridden());

        editService.update(i, fileNames(Map.of("da", "Andet.pdf")), user());
        em.flush();

        SystemPublicationIssueDescVo after = (SystemPublicationIssueDescVo)
                i.toVo(SystemPublicationIssueVo.class).getDescs().get(0);
        assertTrue(after.isFileNameOverridden());
        assertEquals("Andet.pdf", after.getFileName());
    }
}
