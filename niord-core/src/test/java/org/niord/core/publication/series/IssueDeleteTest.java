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
import org.niord.core.message.Message;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.TestIds;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.criteria.MessageSeriesCriterionVo;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.core.user.User;
import org.niord.model.message.MainType;
import org.niord.model.message.Status;
import org.niord.model.message.Type;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Removing an issue: what may go, what may not, and what goes with it.
 *
 * The two refusals are the point. A published issue is a released document that
 * people downloaded and cited, and the only correct answer is to retire it. A
 * retired one may be removed -- it was withdrawn, not unmade -- but only once
 * nothing points at it, because its id may already be written into message HTML
 * and a citation whose target is gone renders as a dead reference in a notice
 * nobody will think to re-check.
 *
 * And the trail has to outlive the row. Every entry about an issue hangs off a
 * foreign key to it and goes with it, so unless a SERIES-level line is written
 * the publication simply stops existing with nothing anywhere recording that it
 * did. That is asserted here as hard as the refusals are.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class IssueDeleteTest {

    @Inject
    IssueDeleteService deletion;

    @Inject
    IssueLifecycleService lifecycle;

    @Inject
    IssueCurationService curation;

    @Inject
    IssuePublishService publishService;

    @Inject
    IssueAuditService audit;

    /** Only for the file-cleanup case, which has to leave a real preview behind. */
    @Inject
    IssuePreviewService previewStore;

    @Inject
    PublicationPathService paths;

    @Inject
    EntityManager em;

    private static final long OPENS = 1_699_000_000_000L;

    private static final long CUTOFF = 1_700_000_000_000L;

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

    /** A message of this suite's own, so nothing shared is edited to carry a citation. */
    private Message message(String shortId) {
        Message m = new Message();
        m.setUid(UUID.randomUUID().toString());
        m.setShortId(shortId);
        m.setMainType(MainType.NM);
        m.setType(Type.TEMPORARY_NOTICE);
        m.setStatus(Status.PUBLISHED);
        m.setPublishDateFrom(new Date(OPENS));
        m.createDesc("da").setTitle("A notice");
        em.persist(m);
        return m;
    }

    /** A message that has not been assigned its number yet, so it has no short id. */
    private Message unnumberedMessage() {
        Message m = new Message();
        m.setUid(UUID.randomUUID().toString());
        m.setMainType(MainType.NM);
        m.setType(Type.TEMPORARY_NOTICE);
        m.setStatus(Status.DRAFT);
        m.createDesc("da").setTitle("A notice awaiting its number");
        em.persist(m);
        return m;
    }

    /**
     * The citation exactly as the editor writes one.
     *
     * An anchor carrying {@code publication="<id>"}, which is the one stored form
     * -- built here by hand rather than through the citation writer so that the
     * lookup is asserted against the shape on disk rather than against whatever
     * the writer happens to emit today.
     */
    private void cite(Message m, String publicId) {
        m.getDescs().get(0).setPublication(
                "<a publication=\"" + publicId + "\" href=\"http://example.invalid/x.pdf\" "
                        + "target=\"_blank\">Publication, item 4.</a>");
        em.merge(m);
    }

    /**
     * The same citation, written into the INTERNAL field.
     *
     * A publication marked internal is rendered into its own column, and the
     * reference dangles just as visibly to the editor who wrote it -- so a lookup
     * that only read the public column would let a cited issue be deleted.
     */
    private void citeInternally(Message m, String publicId) {
        m.getDescs().get(0).setInternalPublication(
                "<span publication=\"" + publicId + "\">[Publication, item 4.]</span>");
        em.merge(m);
    }

    private PublicationIssue publish(PublicationIssue issue, long cutoff) {
        publishService.publish(issue.getId(), new IssuePublishService.PublishRequest(
                IssuePublishService.PublishRequest.ALL_WARNINGS, null, new Date(cutoff)));
        em.flush();
        return em.find(PublicationIssue.class, issue.getId());
    }

    private long members(Integer issueId) {
        return em.createQuery("SELECT COUNT(m) FROM IssueMember m WHERE m.issue.id = :id", Long.class)
                .setParameter("id", issueId).getSingleResult();
    }

    private long overrides(Integer issueId) {
        return em.createQuery("SELECT COUNT(o) FROM IssueOverride o WHERE o.issue.id = :id", Long.class)
                .setParameter("id", issueId).getSingleResult();
    }

    private long issueAuditRows(Integer issueId) {
        return em.createQuery("SELECT COUNT(a) FROM IssueAuditEntry a WHERE a.issue.id = :id", Long.class)
                .setParameter("id", issueId).getSingleResult();
    }

    private IssueAuditEntry deletionEntry(PublicationSeries series) {
        List<IssueAuditEntry> found = em.createQuery(
                        "SELECT a FROM IssueAuditEntry a WHERE a.series = :s AND a.issue IS NULL "
                                + "AND a.action = org.niord.core.publication.series.AuditAction.DELETED",
                        IssueAuditEntry.class)
                .setParameter("s", series).getResultList();
        assertEquals(1, found.size(), "exactly one series-level DELETED entry was expected");
        return found.get(0);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> detailOf(IssueAuditEntry entry) {
        assertNotNull(entry.getDetail(), "the deletion entry carries no detail, so nothing survives "
                + "that says WHICH publication went");
        return (Map<String, Object>) entry.getDetail();
    }

    // ------------------------------------------------------------------- an open issue

    /**
     * An uncited open issue goes, and everything that hung off it goes with it.
     *
     * The message planted here is EXCLUDED from the issue, not citing it -- an
     * override is the issue's own decision about a message, and goes with the
     * issue. What IS asserted alongside the removal is the blast radius: the
     * sibling that WAS released is untouched,
     * frozen snapshot and all -- a delete that reached the series' other issues
     * would be indistinguishable from this one until somebody opened the archive.
     */
    @Test
    @Transactional
    public void anOpenIssueGoesAndTakesEverythingThatHungOffIt() {
        PublicationSeries s = series();
        User actor = user();

        PublicationIssue released = lifecycle.create(s, new Date(OPENS), IntervalBoundSource.STAMPED, actor);
        em.flush();
        released = publish(released, CUTOFF);
        Date snapshotBefore = released.getSnapshotIntervalFrom();
        assertNotNull(snapshotBefore, "the published sibling should carry a frozen interval to compare");
        long membersOfSibling = members(released.getId());

        PublicationIssue target = lifecycle.create(s, new Date(CUTOFF), IntervalBoundSource.STAMPED, actor);
        em.flush();

        // An override, made the way a curator makes one, and a frozen member row.
        // Members are written by the publish and an open issue therefore has none,
        // so the row is planted -- the assertion is that the delete clears the
        // table, not that this issue could have had one.
        Message cited = message(TestIds.id("NM-DEL-"));
        curation.exclude(target, cited.getUid(), actor, "not for this issue");
        IssueMember planted = new IssueMember();
        planted.setIssue(target);
        planted.setMessageUid(cited.getUid());
        planted.setSortIndex(0);
        planted.setFrozenMainType(MainType.NM.name());
        planted.setFrozenType(Type.TEMPORARY_NOTICE.name());
        planted.setFrozenStatus(Status.PUBLISHED.name());
        planted.setSource(MemberSource.CRITERIA);
        em.persist(planted);
        em.flush();

        Integer targetId = target.getId();
        String targetPublicId = target.getPublicId();
        assertTrue(issueAuditRows(targetId) > 0, "the issue should carry its own CREATED entry");

        deletion.delete(target, actor, "created against the wrong series");
        em.flush();
        em.clear();

        assertNull(em.find(PublicationIssue.class, targetId), "the open issue was not deleted");
        assertEquals(0, members(targetId), "the frozen member rows outlived the issue");
        assertEquals(0, overrides(targetId), "the curation decisions outlived the issue");
        assertEquals(0, issueAuditRows(targetId), "the issue's own audit rows outlived it");

        // The one line that survives, and what it says.
        IssueAuditEntry entry = deletionEntry(em.find(PublicationSeries.class, s.getId()));
        Map<String, Object> detail = detailOf(entry);
        assertEquals(targetPublicId, detail.get("publicId"));
        assertEquals(IssueStatus.OPEN.name(), detail.get("statusAtDeletion"));
        assertEquals("created against the wrong series", entry.getReason());
        assertEquals(ActorKind.USER, entry.getActorKind());
        assertNull(entry.getIssue(), "a deletion entry that names the issue would be deleted with it");

        // And the sibling is exactly as it was.
        PublicationIssue survivor = em.find(PublicationIssue.class, released.getId());
        assertNotNull(survivor, "deleting one issue removed another of the same series");
        assertEquals(IssueStatus.PUBLISHED, survivor.getStatus());
        assertEquals(snapshotBefore, survivor.getSnapshotIntervalFrom(),
                "the released sibling's frozen interval moved");
        assertEquals(membersOfSibling, members(survivor.getId()),
                "the released sibling lost member rows to another issue's delete");
    }

    /**
     * An open issue that a message already cites is refused like a retired one.
     *
     * The publication picker offers an issue while it is still being prepared, so
     * an editor can write its id into a message before anything is released under
     * it. Deleting the target would leave that text pointing at nothing, and the
     * editor has no way of noticing -- so the refusal names the message instead,
     * and nothing of the issue is touched on the way to it.
     */
    @Test
    @Transactional
    public void aCitedOpenIssueIsRefusedWithTheCitingMessageNamed() {
        PublicationSeries s = series();
        User actor = user();
        PublicationIssue open = lifecycle.create(s, new Date(OPENS), IntervalBoundSource.STAMPED, actor);
        em.flush();

        Message citing = message(TestIds.id("NM-OPEN-"));
        cite(citing, open.getPublicId());
        curation.exclude(open, message(TestIds.id("NM-OUT-")).getUid(), actor, "not for this issue");
        em.flush();
        long overridesBefore = overrides(open.getId());
        long auditRowsBefore = issueAuditRows(open.getId());

        IssueDeleteService.IssueCitedException e =
                assertThrows(IssueDeleteService.IssueCitedException.class,
                        () -> deletion.delete(open, actor, "created against the wrong series"));
        assertEquals("ISSUE_CITED", e.code());
        assertEquals(1, e.citingCount());
        assertEquals(citing.getShortId(), e.citingMessages().get(0).messageId(),
                "the citing message was not named by the id a person reads");

        em.flush();
        PublicationIssue stillThere = em.find(PublicationIssue.class, open.getId());
        assertNotNull(stillThere, "a refused delete removed the open issue");
        assertEquals(IssueStatus.OPEN, stillThere.getStatus());
        assertEquals(overridesBefore, overrides(open.getId()),
                "a refused delete cleared the issue's curation decisions");
        assertEquals(auditRowsBefore, issueAuditRows(open.getId()),
                "a refused delete cleared the issue's trail");
    }

    // ------------------------------------------------------------ a published issue

    /**
     * A published issue is never deletable, whatever else is true of it.
     *
     * The refusal names retirement, because that is what the admin reaching for
     * delete actually wants: the document off the public list, with the file left
     * at its link for everyone who cited it.
     */
    @BindsRule({"I-16"})
    @Test
    @Transactional
    public void aPublishedIssueIsNeverDeletable() {
        PublicationSeries s = series();
        User actor = user();
        PublicationIssue issue = lifecycle.create(s, new Date(OPENS), IntervalBoundSource.STAMPED, actor);
        em.flush();
        PublicationIssue live = publish(issue, CUTOFF);

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> deletion.delete(live, actor, "a change of mind"));
        assertEquals("ISSUE_PUBLISHED_NOT_DELETABLE", e.code());
        assertTrue(e.getMessage().toLowerCase().contains("retire"),
                "the refusal should name the action that does work: " + e.getMessage());

        em.flush();
        assertNotNull(em.find(PublicationIssue.class, live.getId()), "a refused delete removed the issue");
    }

    // -------------------------------------------------------------- a retired issue

    /**
     * A retired issue that messages cite is refused, and the messages are NAMED.
     *
     * Naming them is the whole difference between a refusal and a dead end: "some
     * messages cite this" leaves an admin to search the estate by hand for
     * something that may not be findable at all, since the citation lives inside
     * message HTML rather than in any list.
     *
     * And NOTHING is removed on the way to the refusal. The check runs before a
     * single row is touched, which is asserted here rather than assumed -- an
     * order that cleared the members first and then refused would leave the issue
     * standing with its contents gone.
     */
    @Test
    @Transactional
    public void aCitedRetiredIssueIsRefusedWithTheCitingMessagesNamed() {
        PublicationSeries s = series();
        User actor = user();
        PublicationIssue issue = lifecycle.create(s, new Date(OPENS), IntervalBoundSource.STAMPED, actor);
        em.flush();
        PublicationIssue published = publish(issue, CUTOFF);
        String publicId = published.getPublicId();

        Message citing = message(TestIds.id("NM-CITE-"));
        cite(citing, publicId);

        PublicationIssue retired = lifecycle.retire(published, actor, "withdrawn after an errata");
        em.flush();

        long auditRowsBefore = issueAuditRows(retired.getId());

        IssueDeleteService.IssueCitedException e =
                assertThrows(IssueDeleteService.IssueCitedException.class,
                        () -> deletion.delete(retired, actor, "housekeeping"));
        assertEquals("ISSUE_CITED", e.code());
        assertEquals(1, e.citingCount());
        assertEquals(1, e.citingMessages().size());
        assertEquals(citing.getShortId(), e.citingMessages().get(0).messageId(),
                "the citing message was not named by the id a person reads");
        assertEquals(citing.getUid(), e.citingMessages().get(0).uid());
        assertEquals("A notice", e.citingMessages().get(0).title(),
                "the citing message carries no title, so a dialog that shows more than a bare "
                        + "number has nothing to render");
        assertTrue(e.getMessage().contains(citing.getShortId()),
                "the sentence should name the message too: " + e.getMessage());

        em.flush();
        PublicationIssue stillThere = em.find(PublicationIssue.class, retired.getId());
        assertNotNull(stillThere, "a refused delete removed the issue");
        assertEquals(IssueStatus.RETIRED, stillThere.getStatus());
        assertEquals(publicId, stillThere.getPublicId(),
                "the id every citation points at must survive a refused delete");
        assertEquals(auditRowsBefore, issueAuditRows(retired.getId()),
                "a refused delete cleared the issue's trail");
    }

    /**
     * A citation naming a DIFFERENT publication does not block the delete -- not
     * even one that a wildcard would match.
     *
     * The lookup is a text search over message HTML, so this is the failure it is
     * one careless pattern away from: matching loosely would refuse deletions on
     * the strength of some other publication's citations, and the refusal would
     * name messages that have nothing to do with the issue.
     *
     * The id here contains UNDERSCORES on purpose, because that is what makes the
     * failure reachable rather than theoretical. An underscore is a single-
     * character wildcard in SQL LIKE, and publication ids carrying one are
     * ordinary: an imported issue adopts the previous system's id AS its public
     * id, and those read like 'dk_firing_areas_2016'. So the decoy below differs
     * from the real id at exactly the underscore -- it matches an unescaped
     * pattern and must not match the real one.
     */
    @Test
    @Transactional
    public void aCitationThatOnlyAWildcardWouldMatchDoesNotBlockTheDelete() {
        PublicationSeries s = series();
        User actor = user();
        PublicationIssue issue = lifecycle.create(s, new Date(OPENS), IntervalBoundSource.STAMPED, actor);
        String underscored = "dk_" + TestIds.suffix();
        issue.setPublicId(underscored);
        em.flush();
        PublicationIssue published = publish(issue, CUTOFF);
        Integer id = published.getId();

        Message wildcardTwin = message(TestIds.id("NM-TWIN-"));
        cite(wildcardTwin, underscored.replace('_', 'X'));

        Message elsewhere = message(TestIds.id("NM-OTHER-"));
        cite(elsewhere, underscored + "-annex");

        PublicationIssue retired = lifecycle.retire(published, actor, "withdrawn, nothing cites it");
        em.flush();

        deletion.delete(retired, actor, "no citations name this one");
        em.flush();
        em.clear();
        assertNull(em.find(PublicationIssue.class, id),
                "a citation of a different publication blocked the delete");
    }

    /**
     * An INTERNAL citation blocks it too, and a message with no number yet is
     * still named.
     *
     * Two halves of the same refusal. A publication marked internal is written
     * into its own column, so a lookup reading only the public one would let a
     * cited issue be deleted and leave the dangling reference in the half of the
     * estate the editors themselves read. And a message that has not been
     * assigned its number has no short id at all -- so it carries a NULL
     * messageId and is named by its TITLE instead.
     *
     * The uid used to be put in the messageId field, and that is the failure this
     * pins: a dialog cannot tell a uid apart from a real message number, so it
     * renders a link to a number nobody can look up, and the one row that most
     * needs explaining is the one that reads as ordinary.
     */
    @Test
    @Transactional
    public void anInternalCitationBlocksItAndAnUnnumberedMessageIsNamedByItsTitle() {
        PublicationSeries s = series();
        User actor = user();
        PublicationIssue issue = lifecycle.create(s, new Date(OPENS), IntervalBoundSource.STAMPED, actor);
        em.flush();
        PublicationIssue published = publish(issue, CUTOFF);

        Message draft = unnumberedMessage();
        citeInternally(draft, published.getPublicId());

        PublicationIssue retired = lifecycle.retire(published, actor, "withdrawn after an errata");
        em.flush();

        IssueDeleteService.IssueCitedException e =
                assertThrows(IssueDeleteService.IssueCitedException.class,
                        () -> deletion.delete(retired, actor, "housekeeping"));
        assertEquals(1, e.citingCount(), "an internal citation did not count");
        assertEquals(1, e.citingMessages().size());
        assertNull(e.citingMessages().get(0).messageId(),
                "an unnumbered message was given a messageId; whatever string sits there will be "
                        + "rendered as the number an editor cites");
        assertEquals(draft.getUid(), e.citingMessages().get(0).uid());
        assertEquals("A notice awaiting its number", e.citingMessages().get(0).title(),
                "the unnumbered row has neither a number nor a title, so nothing on it names the "
                        + "message blocking the deletion");
        assertTrue(e.getMessage().contains("A notice awaiting its number"),
                "the sentence named the message by nothing a reader recognises: " + e.getMessage());
    }

    /**
     * The refusal names the citing messages in the language the caller asked for.
     *
     * A Danish admin reading an English title in a Danish dialog is being shown
     * the same message under a different name than every other screen gives it,
     * and the fallback matters just as much: a message titled only in Danish must
     * still name itself to an English caller, because a blank row is worse than
     * one in the wrong language.
     */
    @Test
    @Transactional
    public void theCitationRefusalTitlesItsMessagesInTheRequestedLanguage() {
        PublicationSeries s = series();
        User actor = user();
        PublicationIssue issue = lifecycle.create(s, new Date(OPENS), IntervalBoundSource.STAMPED, actor);
        em.flush();
        PublicationIssue published = publish(issue, CUTOFF);

        Message citing = message(TestIds.id("NM-LANG-"));
        citing.createDesc("en").setTitle("Buoy off Hesselo withdrawn");
        em.merge(citing);
        cite(citing, published.getPublicId());

        PublicationIssue retired = lifecycle.retire(published, actor, "withdrawn after an errata");
        em.flush();

        IssueDeleteService.IssueCitedException english =
                assertThrows(IssueDeleteService.IssueCitedException.class,
                        () -> deletion.delete(retired, actor, "housekeeping", "en"));
        assertEquals("Buoy off Hesselo withdrawn", english.citingMessages().get(0).title(),
                "the refusal ignored the language it was asked for");

        IssueDeleteService.IssueCitedException danish =
                assertThrows(IssueDeleteService.IssueCitedException.class,
                        () -> deletion.delete(retired, actor, "housekeeping", "da"));
        assertEquals("A notice", danish.citingMessages().get(0).title(),
                "the refusal answered the same message in two languages the same way");

        // An unknown language falls through to the first description carrying a
        // title rather than leaving the row unnamed.
        IssueDeleteService.IssueCitedException unknown =
                assertThrows(IssueDeleteService.IssueCitedException.class,
                        () -> deletion.delete(retired, actor, "housekeeping", "de"));
        assertNotNull(unknown.citingMessages().get(0).title(),
                "a language the message is not written in left the row with no name at all");
    }

    /**
     * A retired issue nothing cites goes, and the record says what it was.
     *
     * The status it was deleted FROM is in the entry because it is the difference
     * between housekeeping and a withdrawal: an OPEN issue that was removed never
     * existed publicly, and a RETIRED one did.
     */
    @Test
    @Transactional
    public void anUncitedRetiredIssueGoesAndTheSeriesRecordsWhatItWas() {
        PublicationSeries s = series();
        User actor = user();
        PublicationIssue issue = lifecycle.create(s, new Date(OPENS), IntervalBoundSource.STAMPED, actor);
        em.flush();
        PublicationIssue published = publish(issue, CUTOFF);
        Integer id = published.getId();
        String publicId = published.getPublicId();
        Date publishedAt = published.getPublishedAt();

        PublicationIssue retired = lifecycle.retire(published, actor, "withdrawn, nothing cites it");
        em.flush();

        deletion.delete(retired, actor, "the whole edition was a mistake");
        em.flush();
        em.clear();

        assertNull(em.find(PublicationIssue.class, id), "the uncited retired issue was not deleted");
        assertEquals(0, members(id), "its frozen membership outlived it");
        assertEquals(0, issueAuditRows(id), "its own trail outlived it");

        Map<String, Object> detail = detailOf(deletionEntry(em.find(PublicationSeries.class, s.getId())));
        assertEquals(publicId, detail.get("publicId"));
        assertEquals(IssueStatus.RETIRED.name(), detail.get("statusAtDeletion"));
        assertNotNull(publishedAt);
        assertEquals(publishedAt.getTime(), ((Number) detail.get("publishedAt")).longValue(),
                "the entry should say when the publication people may remember went out");
        assertNotNull(detail.get("names"), "the entry should carry the names it was known by");
    }

    /**
     * A retired edition goes; the edition that REPLACED it survives without it.
     *
     * This is the one write the delete performs on another issue, and it is not
     * optional: a successor holds a foreign key back to what it replaced, so a
     * predecessor could not be removed at all while that pointer stood. Clearing
     * it rather than cascading is the whole decision -- the successor is a
     * released document of its own that people are reading right now, and
     * following the key would take it out along with its snapshot and its frozen
     * membership.
     *
     * What replaced the deleted edition is therefore recorded in the surviving
     * entry, because the pointer that used to say so is exactly what this clears:
     * without it the history says only that SOMETHING was removed, and the chain
     * a reader is following ends without explanation.
     */
    @Test
    @Transactional
    public void aRetiredPredecessorGoesAndItsSuccessorSurvivesWithoutThePointer() {
        PublicationSeries s = series();
        User actor = user();

        PublicationIssue first = lifecycle.create(s, new Date(OPENS), IntervalBoundSource.STAMPED, actor);
        em.flush();
        PublicationIssue predecessor = publish(first, CUTOFF);
        Integer predecessorId = predecessor.getId();
        String predecessorPublicId = predecessor.getPublicId();

        PublicationIssue edition = lifecycle.newEdition(predecessor, new Date(CUTOFF), actor);
        em.flush();
        PublicationIssue successor = publish(edition, CUTOFF + 86_400_000L);
        Integer successorId = successor.getId();
        String successorPublicId = successor.getPublicId();
        Date snapshotBefore = successor.getSnapshotIntervalFrom();
        long membersBefore = members(successorId);
        assertNotNull(successor.getSupersedes(), "the fixture is not a chain, so it tests nothing");
        assertEquals(predecessorId, successor.getSupersedes().getId());

        PublicationIssue retired = lifecycle.retire(
                em.find(PublicationIssue.class, predecessorId), actor, "replaced, then withdrawn");
        em.flush();

        deletion.delete(retired, actor, "the superseded edition was a mistake");
        em.flush();
        em.clear();

        assertNull(em.find(PublicationIssue.class, predecessorId),
                "a superseded predecessor could not be deleted at all: the successor's foreign key "
                        + "was still pointing at it");

        PublicationIssue survivor = em.find(PublicationIssue.class, successorId);
        assertNotNull(survivor, "deleting a predecessor took the edition that replaced it with it");
        assertEquals(IssueStatus.PUBLISHED, survivor.getStatus(),
                "the surviving edition is a released document and must stay one");
        assertNull(survivor.getSupersedes(),
                "the successor still names a predecessor that no longer exists");
        assertEquals(snapshotBefore, survivor.getSnapshotIntervalFrom(),
                "the surviving edition's frozen interval moved");
        assertEquals(membersBefore, members(successorId),
                "the surviving edition lost member rows to its predecessor's delete");

        Map<String, Object> detail = detailOf(deletionEntry(em.find(PublicationSeries.class, s.getId())));
        assertEquals(predecessorPublicId, detail.get("publicId"));
        assertEquals(List.of(successorPublicId), detail.get("supersededBy"),
                "nothing records which edition replaced the one that was deleted");
    }

    // -------------------------------------------------------------------- the files

    /**
     * The documents go with the issue -- in all three places they live -- and a
     * folder that was never written is not an error.
     *
     * The served folder is the one anybody would think of. The archive and the
     * preview stores are what makes it complete: superseded generations are
     * copied into one and rendered drafts into the other, both keyed by the
     * issue's public id, and once the row and its entries are gone NOTHING left
     * in the system names either location again. They are also the two stores
     * deliberately kept OUTSIDE the served repository because of what they hold
     * -- withdrawn editions of official notices and unpublished drafts -- so
     * leaving them behind is not a matter of wasted bytes.
     *
     * An issue removed before it ever published has no folder at all, which is the
     * ordinary case rather than the exception -- so a cleanup that treated a
     * missing directory as a failure would refuse the most common deletion there
     * is.
     */
    @Test
    @Transactional
    public void theIssueFilesGoWithItEverywhereAndAMissingFolderIsFine() throws IOException {
        PublicationSeries s = series();
        User actor = user();

        PublicationIssue withFiles = lifecycle.create(s, new Date(OPENS), IntervalBoundSource.STAMPED, actor);
        em.flush();
        Path folder = paths.repoRoot().resolve(withFiles.getRepoPath());
        Files.createDirectories(folder);
        Files.write(folder.resolve("publication.pdf"), "bytes".getBytes(StandardCharsets.UTF_8));
        assertTrue(Files.isDirectory(folder));

        // A rendered draft, written by the preview store itself so the location is
        // the real one rather than one this test decided on.
        previewStore.record(withFiles, "da", "preview.pdf",
                "a draft nobody released".getBytes(StandardCharsets.UTF_8));
        Path previews = paths.previewRoot().resolve(withFiles.getPublicId());
        assertTrue(Files.isDirectory(previews), "the fixture wrote no preview, so it tests nothing");

        // And a superseded generation, at the path the archiver picks.
        Path archived = paths.archivePathFor(withFiles.getPublicId(), "da", "publication.pdf", OPENS);
        Files.createDirectories(archived.getParent());
        Files.write(archived, "an earlier edition".getBytes(StandardCharsets.UTF_8));
        Path archives = paths.archiveRoot().resolve(withFiles.getPublicId());

        deletion.delete(withFiles, actor, "removing the documents too");
        em.flush();
        assertFalse(Files.exists(folder), "the issue's folder outlived the issue: " + folder);
        assertFalse(Files.exists(previews), "the unpublished previews outlived the issue: " + previews);
        assertFalse(Files.exists(archives), "the archived editions outlived the issue: " + archives);

        // And one that never had a folder deletes just the same.
        PublicationIssue withoutFiles = lifecycle.create(s, new Date(CUTOFF), IntervalBoundSource.STAMPED, actor);
        em.flush();
        Integer id = withoutFiles.getId();
        assertFalse(Files.exists(paths.repoRoot().resolve(withoutFiles.getRepoPath())));

        deletion.delete(withoutFiles, actor, "nothing was ever written for it");
        em.flush();
        em.clear();
        assertNull(em.find(PublicationIssue.class, id), "a missing folder blocked the delete");
    }

    // ------------------------------------------------------------------ the history

    /**
     * The series history is the series' own events, newest first.
     *
     * Two properties, and each is a way the panel goes wrong. Including the
     * per-issue entries would bury the handful of series events under every
     * create, publish and curation of every issue it ever had. And oldest-first is
     * the wrong order for a log: the question at one is what happened recently,
     * which on an established publication is hundreds of rows down.
     */
    @Test
    @Transactional
    public void theSeriesHistoryIsItsOwnEventsNewestFirst() {
        PublicationSeries s = series();
        User actor = user();

        audit.series(s, actor, AuditAction.SERIES_ACTIVATED, "ready to publish");
        em.flush();

        PublicationIssue target = lifecycle.create(s, new Date(OPENS), IntervalBoundSource.STAMPED, actor);
        em.flush();
        assertTrue(issueAuditRows(target.getId()) > 0,
                "the issue should carry entries of its own, which are what must NOT appear below");

        deletion.delete(target, actor, "wrong series");
        em.flush();

        List<IssueAuditEntry> history = audit.forSeries(em.find(PublicationSeries.class, s.getId()));
        assertEquals(2, history.size(), "the series history should hold exactly its own two events");
        assertEquals(AuditAction.DELETED, history.get(0).getAction(), "the history is not newest first");
        assertEquals(AuditAction.SERIES_ACTIVATED, history.get(1).getAction());
        for (IssueAuditEntry entry : history) {
            assertNull(entry.getIssue(), "a per-issue entry reached the series history");
        }
    }
}
