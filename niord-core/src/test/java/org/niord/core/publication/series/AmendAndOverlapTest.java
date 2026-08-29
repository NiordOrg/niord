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
import org.niord.core.publication.series.BindsRule;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.criteria.MessageSeriesCriterionVo;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.core.user.User;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Amend, the new edition, and the period a released issue already covered.
 *
 * These are the three actions that can put two documents where the public
 * expects one. Amend replaces a document people have already cited, so what it
 * must NOT move is the whole of its contract; a new edition takes over from an
 * existing one, so the link between them cannot be a step somebody remembers;
 * and an interval reaching back into a released issue publishes the same
 * messages twice under two names.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class AmendAndOverlapTest {

    @Inject
    IssueLifecycleService lifecycle;

    @Inject
    IssuePublishService publishService;

    @Inject
    IssueAuditService auditService;

    @Inject
    EntityManager em;

    // ------------------------------------------------------------------ fixtures

    private PublicationSeries series(TimeRelation relation) {
        PublicationCategory c = new PublicationCategory();
        c.setCategoryId("cat-" + UUID.randomUUID().toString().substring(0, 8));
        c.setPriority(100);
        em.persist(c);

        PublicationSeries s = new PublicationSeries();
        s.setSeriesId("s-" + UUID.randomUUID().toString().substring(0, 8));
        s.setStatus(SeriesStatus.ACTIVE);
        s.setContentMode(ContentMode.GENERATED_FROM_QUERY);
        s.setReportId("some-report");
        s.setCadence(SeriesCadence.WEEKLY);
        s.setTimeRelation(relation);
        s.setAliveAtCutoff(relation == TimeRelation.IN_FORCE_AT_CUTOFF);
        s.setReleaseMode(ReleaseMode.MANUAL_GATE);
        s.setNextIssueCreation(NextIssueCreation.MANUAL);
        s.setPublicAuthority(PublicAuthority.LEGACY);
        s.setMessagePublication(MessagePublication.NONE);
        s.setNumberingScheme(NumberingScheme.ISO_WEEK_YEAR);
        // No report configured: these tests are about what an amend preserves and
        // what an overlap refuses, and a rendered document is a different subject.
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
        u.setUsername("u-" + UUID.randomUUID().toString().substring(0, 8));
        em.persist(u);
        return u;
    }

    private PublicationIssue publishedIssue(PublicationSeries s, Date from, Date stamp) {
        PublicationIssue i = lifecycle.create(s, from, IntervalBoundSource.STAMPED, user());
        em.flush();
        previewFor(i);
        publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(false, IssuePublishService.PublishRequest.ALL_WARNINGS,
                        user(), stamp));
        em.flush();
        return em.find(PublicationIssue.class, i.getId());
    }

    // ================================================================== amend

    /**
     * Everything a reader or a citation depends on survives an amend.
     *
     * The list is not a style preference. publicId is what message HTML cites;
     * the file path is the address the citation resolves to; the public window
     * decides which issue the site calls current; and the stamped cut-off is the
     * instant this issue's content was decided at, which the issue before it
     * chains off. An amend that moved any of them would be a new publication
     * wearing an old one's name.
     */
    @Test
    @Transactional
    public void anAmendMovesNothingACitationDependsOn() {
        PublicationSeries s = series(TimeRelation.PUBLISHED_IN_INTERVAL);
        Date stamp = new Date(1_700_000_000_000L);
        PublicationIssue issue = publishedIssue(s, new Date(1_699_000_000_000L), stamp);

        String publicId = issue.getPublicId();
        Date cutoffBefore = issue.getCutoffStampedAt();
        Date publicFromBefore = issue.getPublicFrom();
        Date publicToBefore = issue.getPublicTo();
        Date publishedAtBefore = issue.getPublishedAt();
        String repoPathBefore = issue.getRepoPath();
        PublicationIssueDesc descBefore = issue.getDescs().get(0);
        String filePathBefore = descBefore.getFilePath();
        String linkBefore = descBefore.getLink();

        publishService.amend(issue.getId(),
                new IssuePublishService.AmendRequest(false, IssuePublishService.PublishRequest.ALL_WARNINGS,
                        user(), "a chart number was wrong in three of the notices"));
        em.flush();
        em.clear();

        PublicationIssue after = em.createQuery(
                        "SELECT i FROM PublicationIssue i WHERE i.publicId = :id", PublicationIssue.class)
                .setParameter("id", publicId).getSingleResult();

        assertEquals(publicId, after.getPublicId(), "the id every citation carries");
        assertEquals(cutoffBefore, after.getCutoffStampedAt(),
                "the cut-off is the instant this content was decided at; an amend re-runs that "
                        + "decision rather than re-taking it");
        assertEquals(publicFromBefore, after.getPublicFrom());
        assertEquals(publicToBefore, after.getPublicTo());
        assertEquals(publishedAtBefore, after.getPublishedAt(),
                "the release happened once; correcting the document is not a second release");
        assertEquals(repoPathBefore, after.getRepoPath());
        assertEquals(IssueStatus.PUBLISHED, after.getStatus());

        PublicationIssueDesc descAfter = after.getDescs().get(0);
        assertEquals(filePathBefore, descAfter.getFilePath(), "the address the citation resolves to");
        assertEquals(linkBefore, descAfter.getLink());
    }

    /** An amend says why, and the trail carries it. */
    @Test
    @Transactional
    public void anAmendIsAuditedWithItsReason() {
        PublicationSeries s = series(TimeRelation.PUBLISHED_IN_INTERVAL);
        PublicationIssue issue = publishedIssue(s, new Date(1_699_000_000_000L),
                new Date(1_700_000_000_000L));

        publishService.amend(issue.getId(),
                new IssuePublishService.AmendRequest(false, IssuePublishService.PublishRequest.ALL_WARNINGS,
                        user(), "the wrong week was printed on the cover"));
        em.flush();

        List<IssueAuditEntry> amended = auditService.forIssue(issue).stream()
                .filter(a -> AuditAction.AMENDED == a.getAction()).toList();
        assertEquals(1, amended.size(), "one amend, one entry");
        assertEquals("the wrong week was printed on the cover", amended.get(0).getReason());
    }

    /** No reason, no amend: a correction nobody explained is unreviewable later. */
    @Test
    @Transactional
    public void anAmendWithoutAReasonIsRefused() {
        PublicationSeries s = series(TimeRelation.PUBLISHED_IN_INTERVAL);
        PublicationIssue issue = publishedIssue(s, new Date(1_699_000_000_000L),
                new Date(1_700_000_000_000L));

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> publishService.amend(issue.getId(),
                                new IssuePublishService.AmendRequest(false, Set.of(), user(), "  ")));
        assertEquals("REASON_REQUIRED", e.code());
    }

    /** An open issue has nothing published to correct. */
    @Test
    @Transactional
    public void anOpenIssueCannotBeAmended() {
        PublicationSeries s = series(TimeRelation.PUBLISHED_IN_INTERVAL);
        PublicationIssue open = lifecycle.create(s, new Date(1_699_000_000_000L),
                IntervalBoundSource.STAMPED, user());
        em.flush();

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> publishService.amend(open.getId(),
                                new IssuePublishService.AmendRequest(false, Set.of(), user(), "why not")));
        assertEquals("ISSUE_NOT_PUBLISHED", e.code());
    }

    // ============================================================== overlap

    /**
     * An issue opening where the previous one closed is the chain, not an overlap.
     *
     * This is every ordinary week, including one published early: the previous
     * issue closed when it was released, and the next one starts exactly there.
     * A rule that called this an overlap would refuse the normal case.
     */
    @Test
    @Transactional
    public void anIssueOpeningAtThePreviousCloseIsAccepted() {
        PublicationSeries s = series(TimeRelation.PUBLISHED_IN_INTERVAL);
        Date stamp = new Date(1_700_000_000_000L);
        publishedIssue(s, new Date(1_699_000_000_000L), stamp);

        PublicationIssue next = lifecycle.create(s, stamp, IntervalBoundSource.STAMPED, user());
        em.flush();
        assertNotNull(next.getId(), "the chain continues where the previous issue ended");
    }

    /** An interval reaching back into a released issue is refused, and says which one. */
    @BindsRule({"I-8"})
    @Test
    @Transactional
    public void anIntervalInsideAReleasedIssueIsRefused() {
        PublicationSeries s = series(TimeRelation.PUBLISHED_IN_INTERVAL);
        Date from = new Date(1_699_000_000_000L);
        Date stamp = new Date(1_700_000_000_000L);
        PublicationIssue released = publishedIssue(s, from, stamp);

        // Halfway through the period that has already gone out.
        Date inside = new Date((from.getTime() + stamp.getTime()) / 2);

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> lifecycle.create(s, inside, IntervalBoundSource.MANUAL, user()));
        assertEquals("ISSUE_INTERVAL_OVERLAP", e.code());
        assertTrue(e.getMessage().contains(released.getPublicId()),
                "the refusal names the issue whose period this one reaches into");
    }

    /**
     * An in-force series is exempt, because its issues overlap by construction.
     *
     * The 2026 and 2027 firing-area editions share thirty-one of their
     * thirty-two members. Asking whether they overlap is a category error.
     *
     * The exemption is asserted PRESENT, not merely left untested: a rule that
     * refused these would refuse the only way this series can be run.
     */
    @BindsRule({"I-8"})
    @Test
    @Transactional
    public void anInForceSeriesIsExemptFromTheOverlapRule() {
        PublicationSeries s = series(TimeRelation.IN_FORCE_AT_CUTOFF);
        Date from = new Date(1_699_000_000_000L);
        Date stamp = new Date(1_700_000_000_000L);
        publishedIssue(s, from, stamp);

        Date inside = new Date((from.getTime() + stamp.getTime()) / 2);
        PublicationIssue overlapping = lifecycle.create(s, inside, IntervalBoundSource.MANUAL, user());
        em.flush();
        assertNotNull(overlapping.getId(), "overlapping editions are what this series is");
    }

    // ========================================================= new edition

    /**
     * The link is made with the edition; the cap waits for its publish.
     *
     * Capping at creation would close the predecessor's window while the new
     * edition is still OPEN and unreadable -- leaving the download site with no
     * current edition at all until somebody presses publish. The publish
     * transaction caps the predecessor at the new stamp minus one millisecond,
     * so the two windows meet exactly.
     */
    @Test
    @Transactional
    public void aNewEditionLinksAtCreateAndTakesOverAtPublish() {
        PublicationSeries s = series(TimeRelation.IN_FORCE_AT_CUTOFF);
        Date stamp = new Date(1_700_000_000_000L);
        PublicationIssue first = publishedIssue(s, null, stamp);
        assertNotNull(first.getPublicFrom());

        PublicationIssue second = lifecycle.newEdition(first, null, user());
        em.flush();

        assertEquals(first.getId(), second.getSupersedes().getId(), "the link is made at create");
        assertEquals(IssueStatus.OPEN, second.getStatus());
        assertNull(em.find(PublicationIssue.class, first.getId()).getPublicTo(),
                "the previous edition stays current while its replacement is unpublished");

        Date takeover = new Date(stamp.getTime() + 86_400_000L);
        previewFor(second);
        publishService.publish(second.getId(),
                new IssuePublishService.PublishRequest(false, IssuePublishService.PublishRequest.ALL_WARNINGS,
                        user(), takeover));
        em.flush();

        PublicationIssue predecessor = em.find(PublicationIssue.class, first.getId());
        assertEquals(takeover.getTime() - 1, predecessor.getPublicTo().getTime(),
                "the windows meet exactly: no gap, and never two current editions");
    }

    /** The supersede is audited on the edition it replaces. */
    @Test
    @Transactional
    public void aNewEditionIsAuditedOnBothIssues() {
        PublicationSeries s = series(TimeRelation.IN_FORCE_AT_CUTOFF);
        PublicationIssue first = publishedIssue(s, null, new Date(1_700_000_000_000L));
        PublicationIssue second = lifecycle.newEdition(first, null, user());
        em.flush();

        assertTrue(auditService.forIssue(first).stream()
                        .anyMatch(a -> AuditAction.SUPERSEDED_BY == a.getAction()),
                "the replaced edition records what replaced it");
        assertTrue(auditService.forIssue(second).stream()
                        .anyMatch(a -> AuditAction.CREATED_NEW_EDITION == a.getAction()),
                "and the new one records what it is");
    }

    /** A reason of one keystroke is not a reason. */
    @Test
    @Transactional
    public void anAmendWithAnUnreadableReasonIsRefused() {
        PublicationSeries s = series(TimeRelation.PUBLISHED_IN_INTERVAL);
        PublicationIssue issue = publishedIssue(s, new Date(1_699_000_000_000L),
                new Date(1_700_000_000_000L));

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> publishService.amend(issue.getId(),
                                new IssuePublishService.AmendRequest(false, Set.of(), user(), " x ")));
        assertEquals("REASON_REQUIRED", e.code());
    }

    /**
     * An imported issue is a historical record. Its members were frozen from the
     * archive and its document is the one people cited; re-deciding either is not
     * a correction but a different publication under the old name.
     */
    @Test
    @Transactional
    public void anImportedIssueCannotBeAmended() {
        PublicationSeries s = series(TimeRelation.PUBLISHED_IN_INTERVAL);
        PublicationIssue issue = publishedIssue(s, new Date(1_699_000_000_000L),
                new Date(1_700_000_000_000L));
        issue.setLegacyPublicationId(UUID.randomUUID().toString());
        issue.setMembershipProvenance(MembershipProvenance.IMPORTED);
        em.flush();

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> publishService.amend(issue.getId(),
                                new IssuePublishService.AmendRequest(false, Set.of(), user(),
                                        "the archive had the wrong cover")));
        assertEquals("ISSUE_IMPORTED", e.code());
        assertTrue(auditService.forIssue(issue).stream().noneMatch(a -> AuditAction.AMENDED == a.getAction()),
                "a refusal records nothing");
    }

    /** Retiring takes a document off the public list; it says why, in words. */
    @Test
    @Transactional
    public void retiringWithoutAReadableReasonIsRefused() {
        PublicationSeries s = series(TimeRelation.PUBLISHED_IN_INTERVAL);
        PublicationIssue issue = publishedIssue(s, new Date(1_699_000_000_000L),
                new Date(1_700_000_000_000L));

        for (String reason : new String[] {null, "", "  ", "x"}) {
            IssueLifecycleService.TransitionRefusedException e =
                    assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                            () -> lifecycle.retire(issue, user(), reason), "reason: '" + reason + "'");
            assertEquals("REASON_REQUIRED", e.code());
        }
        assertEquals(IssueStatus.PUBLISHED, issue.getStatus(), "a refusal changes nothing");

        lifecycle.retire(issue, user(), "superseded by a corrected edition");
        assertEquals(IssueStatus.RETIRED, issue.getStatus());
    }

    @jakarta.inject.Inject
    org.niord.core.publication.series.IssuePreviewService previewService;

    /**
     * Records a preview so the publish has bytes to promote.
     *
     * A query-backed series names a report and publish refuses to leave a
     * language without a document, so these fixtures release the way an admin
     * does after looking at the preview: regenerate = false, promoting exactly
     * the bytes that were reviewed. The bytes themselves are irrelevant here.
     */
    private void previewFor(org.niord.core.publication.series.PublicationIssue issue) {
        for (org.niord.core.publication.series.PublicationIssueDesc desc : issue.getDescs()) {
            previewService.record(issue, desc.getLang(), "preview.pdf",
                    "preview-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

}
