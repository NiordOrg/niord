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
import jakarta.transaction.UserTransaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.message.Message;
import org.niord.core.message.MessageSeries;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.TestIds;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.criteria.MessageMainTypeCriterionVo;
import org.niord.core.publication.series.criteria.MessageSeriesCriterionVo;
import org.niord.core.publication.series.resolve.ResolutionWarningCode;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.model.message.MainType;
import org.niord.model.message.Status;
import org.niord.model.message.Type;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The publish transaction, one assertion per step.
 *
 * A step with no assertion is not done. These failures are not crashes -- they
 * are a published document that is subtly not what anyone intended, on a public
 * site, with no second chance.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class IssuePublishTest {

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

    @Inject
    UserTransaction tx;

    // ------------------------------------------------------------------ fixtures

    private PublicationSeries series(SeriesCadence cadence, TimeRelation relation,
                                     ReleaseMode release, NextIssueCreation next, SeriesStatus status) {
        PublicationCategory c = new PublicationCategory();
        c.setCategoryId(TestIds.category());
        c.setPriority(100);
        c.setPublish(true);
        em.persist(c);

        PublicationSeries s = new PublicationSeries();
        s.setSeriesId(TestIds.series());
        s.setStatus(status);
        s.setContentMode(ContentMode.GENERATED_FROM_QUERY);
        s.setReportId("some-report");
        s.setCadence(cadence);
        s.setTimeRelation(relation);
        s.setAliveAtCutoff(relation == TimeRelation.IN_FORCE_AT_CUTOFF);
        s.setReleaseMode(release);
        s.setNextIssueCreation(next);
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

        PublicationSeriesDesc d = s.createDesc("da");
        d.setName("Test series");
        em.persist(s);
        return s;
    }

    private PublicationIssue issue(PublicationSeries s, Date intervalFrom) {
        PublicationIssue i = new PublicationIssue();
        i.setSeries(s);
        i.setPublicId(UUID.randomUUID().toString());
        i.setRepoPath("publications/" + i.getPublicId());
        i.setStatus(IssueStatus.OPEN);
        i.setIntervalFrom(intervalFrom);
        i.setIntervalFromSource(IntervalBoundSource.STAMPED);
        PublicationIssueDesc d = i.createDesc("da");
        d.setName("Test issue");
        em.persist(i);
        return i;
    }

    /**
     * A message the criteria select, withdrawn, and still open at the cut-off.
     *
     * The three facts together are the only acknowledgeable warning there is. It
     * belongs to the fixture rather than to the corpus because a guard that
     * depends on what a shared database happens to hold in a fixed window is a
     * guard that stops running without ever going red.
     */
    private Message cancelledButStillOpen(Date publishedAt, Date openUntil) {
        Message m = new Message();
        m.setUid(UUID.randomUUID().toString());
        m.setMessageSeries(messageSeries("dma-nm"));
        m.setMainType(MainType.NM);
        m.setType(Type.TEMPORARY_NOTICE);
        m.setStatus(Status.CANCELLED);
        m.setPublishDateFrom(publishedAt);
        m.setPublishDateTo(openUntil);
        em.persist(m);
        return m;
    }

    /** The message series the fixture's criteria name, reused where it exists. */
    private MessageSeries messageSeries(String seriesId) {
        List<MessageSeries> found = em.createQuery(
                        "SELECT ms FROM MessageSeries ms WHERE ms.seriesId = :id", MessageSeries.class)
                .setParameter("id", seriesId).setMaxResults(1).getResultList();
        if (!found.isEmpty()) {
            return found.get(0);
        }
        MessageSeries ms = new MessageSeries();
        ms.setSeriesId(seriesId);
        ms.setMainType(MainType.NM);
        em.persist(ms);
        return ms;
    }

    /** A published neighbour, planted directly so the cap logic has something to act on. */
    private PublicationIssue publishedIssue(PublicationSeries s, Date stamp, Date publicTo,
                                            PublicWindowSource windowSource, IssueStatus status) {
        PublicationIssue i = issue(s, new Date(stamp.getTime() - 7 * 24 * 3600_000L));
        i.setStatus(status);
        i.setCutoffStampedAt(stamp);
        i.setPublishedAt(stamp);
        i.setPublicFrom(stamp);
        i.setPublicTo(publicTo);
        i.setPublicWindowSource(windowSource);
        em.merge(i);
        return i;
    }

    // ============================================================ steps 1-8

    /** Step 1. Two concurrent publishes produce exactly one stamp. */
    @Test
    public void concurrentPublishesProduceExactlyOneStamp() throws Exception {
        Integer issueId;
        tx.begin();
        PublicationSeries s = series(SeriesCadence.WEEKLY, TimeRelation.PUBLISHED_IN_INTERVAL,
                ReleaseMode.MANUAL_GATE, NextIssueCreation.MANUAL, SeriesStatus.ACTIVE);
        PublicationIssue contested = issue(s, new Date(System.currentTimeMillis() - 3600_000L));
        previewFor(contested);
        issueId = contested.getId();
        tx.commit();

        AtomicInteger winners = new AtomicInteger();
        AtomicInteger losers = new AtomicInteger();
        AtomicReference<Date> winningStamp = new AtomicReference<>();
        CountDownLatch go = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        for (int i = 0; i < 2; i++) {
            pool.submit(() -> {
                try {
                    go.await();
                    var result = publishService.publish(issueId,
                            new IssuePublishService.PublishRequest(false, IssuePublishService.PublishRequest.ALL_WARNINGS, null, null));
                    winners.incrementAndGet();
                    winningStamp.set(result.stampedAt());
                } catch (IssuePublishService.AlreadyPublishedException e) {
                    losers.incrementAndGet();
                } catch (Exception e) {
                    // A lock timeout is also a loss, and an acceptable one.
                    losers.incrementAndGet();
                }
            });
        }
        go.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "the publishes did not finish");

        assertEquals(1, winners.get(), "expected exactly one publish to win, got " + winners.get());
        assertEquals(1, losers.get(), "expected exactly one publish to lose");

        tx.begin();
        PublicationIssue after = em.find(PublicationIssue.class, issueId);
        assertEquals(IssueStatus.PUBLISHED, after.getStatus());
        assertEquals(winningStamp.get(), after.getCutoffStampedAt(),
                "the stored stamp is not the winner's");
        tx.commit();
    }

    /** Step 2. The member set is the one true AT THE STAMP, not at call time. */
    @Test
    @Transactional
    public void theMemberSetIsResolvedAgainstTheStampNotTheCallTime() {
        PublicationSeries s = series(SeriesCadence.WEEKLY, TimeRelation.PUBLISHED_IN_INTERVAL,
                ReleaseMode.MANUAL_GATE, NextIssueCreation.MANUAL, SeriesStatus.ACTIVE);

        // A stamp well in the past: everything published since is excluded.
        Date pastStamp = new Date(1_700_000_000_000L);
        PublicationIssue i = issue(s, new Date(pastStamp.getTime() - 7 * 24 * 3600_000L));
        em.flush();

        previewFor(i);
        var result = publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(false, IssuePublishService.PublishRequest.ALL_WARNINGS, null, pastStamp));

        assertEquals(pastStamp, result.stampedAt());

        long publishedAfterStamp = em.createQuery(
                        "SELECT COUNT(m) FROM IssueMember m WHERE m.issue.id = :id "
                                + "AND m.frozenPublishDateFrom > :stamp", Long.class)
                .setParameter("id", i.getId()).setParameter("stamp", pastStamp).getSingleResult();

        assertEquals(0, publishedAfterStamp,
                "members were frozen that were published after the stamp; the resolve used the call time");
    }

    /** Steps 5 to 7. Dense sortIndex, frozen facts, and the snapshot header. */
    @Test
    @Transactional
    public void theFrozenRowsAndHeaderRecordWhatWasTrueAtFreeze() {
        PublicationSeries s = series(SeriesCadence.WEEKLY, TimeRelation.PUBLISHED_IN_INTERVAL,
                ReleaseMode.MANUAL_GATE, NextIssueCreation.MANUAL, SeriesStatus.ACTIVE);
        Date from = new Date(1_699_000_000_000L);
        PublicationIssue i = issue(s, from);
        em.flush();

        previewFor(i);
        publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(false, IssuePublishService.PublishRequest.ALL_WARNINGS, null, new Date(1_700_000_000_000L)));
        em.flush();
        em.clear();

        PublicationIssue after = em.find(PublicationIssue.class, i.getId());

        // Step 7: the interval the resolve ACTUALLY used.
        assertEquals(from, after.getSnapshotIntervalFrom(),
                "snapshotIntervalFrom does not record the bound the resolve used; that divergence is "
                        + "exactly why the column exists");
        assertNotNull(after.getSnapshotFrozenAt());
        assertEquals(TimeRelation.PUBLISHED_IN_INTERVAL, after.getSnapshotTimeRelation());

        List<IssueMember> members = em.createQuery(
                        "SELECT m FROM IssueMember m WHERE m.issue.id = :id ORDER BY m.sortIndex",
                        IssueMember.class)
                .setParameter("id", i.getId()).getResultList();

        assertEquals(after.getMemberCount(), members.size(), "memberCount disagrees with the frozen rows");

        if (!members.isEmpty()) {
            // Step 5: dense, zero-based, unique.
            for (int n = 0; n < members.size(); n++) {
                assertEquals(n, members.get(n).getSortIndex(), "sortIndex is not dense and zero-based");
            }
            // Step 6: the four mutable facts are frozen, not re-read.
            IssueMember first = members.get(0);
            assertNotNull(first.getMessageUid());
            assertNotNull(first.getFrozenStatus(),
                    "status was not frozen; it is mutable, so a snapshot that re-reads it is not a snapshot");
        }
    }

    /**
     * A publish resolves and freezes by the issue's OWN criteria where it has any.
     *
     * The whole point of criteriaOverride. Resolving the series' document while
     * freezing the override -- or the reverse -- would produce a published issue
     * whose recorded criteria do not explain its own member list, and there is no
     * later way to tell which of the two actually ran.
     *
     * The snapshot is the EFFECTIVE document, not the series': the series' criteria
     * stay editable and the override is not frozen anywhere else, so this is the
     * only truthful answer a published issue can later give about what it selected.
     */
    @Test
    @Transactional
    public void apublishResolvesAndFreezesTheIssuesOwnCriteria() {
        PublicationSeries s = series(SeriesCadence.WEEKLY, TimeRelation.PUBLISHED_IN_INTERVAL,
                ReleaseMode.MANUAL_GATE, NextIssueCreation.MANUAL, SeriesStatus.ACTIVE);
        PublicationIssue i = issue(s, new Date(1_699_000_000_000L));

        IssueCriteriaVo override = new IssueCriteriaVo();
        MessageSeriesCriterionVo node = new MessageSeriesCriterionVo();
        node.setValues(new ArrayList<>(List.of("dma-nm", "dma-fa")));
        override.getCriteria().add(node);
        i.setCriteriaOverride(override);
        em.flush();

        previewFor(i);
        publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(false, IssuePublishService.PublishRequest.ALL_WARNINGS, null, new Date(1_700_000_000_000L)));
        em.flush();
        em.clear();

        PublicationIssue after = em.find(PublicationIssue.class, i.getId());

        assertEquals(override, after.getCriteriaSnapshot(),
                "the snapshot recorded the SERIES' criteria while the resolve used the override; "
                        + "the published issue's recorded criteria would not explain its members");
        assertEquals("dma-fa,dma-nm",
                java.util.Arrays.stream(after.getSnapshotSeriesIds().split(","))
                        .sorted().collect(java.util.stream.Collectors.joining(",")),
                "snapshotSeriesIds came from the series, not from the document that ran");
        assertTrue(EffectiveCriteria.isOverridden(after),
                "a published issue that went out with its own criteria must report itself as tailored");
    }

    /**
     * A published issue records EVERY operand it selected on, not only the series.
     *
     * The criteria snapshot holds the DOCUMENT; these columns hold what it
     * resolved to, and the two answer different questions -- a domain node
     * expands to a message-series set the document never spells out, and an MRN
     * that has since been renamed is only recoverable from what was written down
     * at release time. A facet the criteria never mentioned stays NULL, because
     * an empty string would read as "selected on it, and nothing matched", which
     * is a different publication.
     */
    @Test
    @Transactional
    public void aPublishFreezesEveryOperandItSelectedOn() {
        PublicationSeries s = series(SeriesCadence.WEEKLY, TimeRelation.PUBLISHED_IN_INTERVAL,
                ReleaseMode.MANUAL_GATE, NextIssueCreation.MANUAL, SeriesStatus.ACTIVE);
        PublicationIssue i = issue(s, new Date(1_699_000_000_000L));

        IssueCriteriaVo override = new IssueCriteriaVo();
        MessageSeriesCriterionVo scope = new MessageSeriesCriterionVo();
        scope.setValues(new ArrayList<>(List.of("dma-nm")));
        override.getCriteria().add(scope);
        MessageMainTypeCriterionVo mainType = new MessageMainTypeCriterionVo();
        mainType.setValues(new ArrayList<>(List.of("NM")));
        override.getCriteria().add(mainType);
        i.setCriteriaOverride(override);
        em.flush();

        previewFor(i);
        publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(false, IssuePublishService.PublishRequest.ALL_WARNINGS,
                        null, new Date(1_700_000_000_000L)));
        em.flush();
        em.clear();

        PublicationIssue after = em.find(PublicationIssue.class, i.getId());

        assertEquals("NM", after.getSnapshotMainTypes(),
                "the main type this issue narrowed on was not recorded, so the published issue can "
                        + "no longer say why a message of another main type is absent from it");
        assertNull(after.getSnapshotAreaIds(),
                "an operand the criteria never mentioned was recorded as a selection that matched "
                        + "nothing, which describes a different publication");
    }

    /** Step 8. An exclude the query never returned freezes appliedAtPublish = false. */
    @BindsRule({"O-5"})
    @Test
    @Transactional
    public void anOverrideThatChangedNothingFreezesAsNotApplied() {
        PublicationSeries s = series(SeriesCadence.WEEKLY, TimeRelation.PUBLISHED_IN_INTERVAL,
                ReleaseMode.MANUAL_GATE, NextIssueCreation.MANUAL, SeriesStatus.ACTIVE);
        PublicationIssue i = issue(s, new Date(1_699_000_000_000L));
        em.flush();

        org.niord.core.user.User author = new org.niord.core.user.User();
        author.setUsername(TestIds.id("curator-"));
        em.persist(author);

        IssueOverride ghost = new IssueOverride();
        ghost.setIssue(i);
        ghost.setAuthor(author);
        ghost.setMessageUid("a-uid-the-query-never-returns");
        ghost.setKind(OverrideKind.EXCLUDE);
        ghost.setReason("a message that is not in this issue anyway");
        em.persist(ghost);
        em.flush();

        previewFor(i);
        publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(false, IssuePublishService.PublishRequest.ALL_WARNINGS, null, new Date(1_700_000_000_000L)));
        em.flush();
        em.clear();

        IssueOverride after = em.find(IssueOverride.class, ghost.getId());
        assertEquals(Boolean.FALSE, after.getAppliedAtPublish(),
                "an exclude that changed nothing was recorded as applied, which would make a later reader "
                        + "think it did something");
    }

    // ============================================================ steps 11-15

    /** Step 11. publishedBy is NULL under AUTO_RELEASE -- a fabricated actor is worse than none. */
    @Test
    @Transactional
    public void anAutomaticReleaseRecordsNoActor() {
        PublicationSeries auto = series(SeriesCadence.WEEKLY, TimeRelation.PUBLISHED_IN_INTERVAL,
                ReleaseMode.AUTO_RELEASE, NextIssueCreation.MANUAL, SeriesStatus.ACTIVE);
        PublicationIssue i = issue(auto, new Date(1_699_000_000_000L));
        em.flush();

        previewFor(i);
        publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(false, IssuePublishService.PublishRequest.ALL_WARNINGS, null, new Date(1_700_000_000_000L)));
        em.flush();
        em.clear();

        PublicationIssue after = em.find(PublicationIssue.class, i.getId());
        assertNotNull(after.getPublishedAt());
        assertEquals(new Date(1_700_000_000_000L), after.getCutoffStampedAt(),
                "the cut-off is the instant that was chosen");
        assertTrue(after.getPublishedAt().getTime() > 1_700_000_000_000L,
                "the publication moment is when the release ran, not the cut-off it chose");
        assertNull(after.getPublishedBy(),
                "an unattended release recorded an actor; that makes it look signed off");
    }

    /**
     * Step 12. Publishing a recovered older issue caps ITSELF against its successor.
     *
     * The retro-create case. Without this the recovered issue has a NULL publicTo
     * and the public site's current publication becomes a two-year-old one.
     */
    @BindsRule({"I-19"})
    @Test
    @Transactional
    public void aRecoveredIssueCapsItselfAgainstItsSuccessor() {
        PublicationSeries s = series(SeriesCadence.WEEKLY, TimeRelation.PUBLISHED_IN_INTERVAL,
                ReleaseMode.MANUAL_GATE, NextIssueCreation.MANUAL, SeriesStatus.ACTIVE);

        Date laterStamp = new Date(1_760_000_000_000L);
        publishedIssue(s, laterStamp, null, PublicWindowSource.DERIVED, IssueStatus.PUBLISHED);

        Date recoveredStamp = new Date(1_700_000_000_000L);
        PublicationIssue recovered = issue(s, new Date(recoveredStamp.getTime() - 7 * 24 * 3600_000L));
        em.flush();

        previewFor(recovered);
        publishService.publish(recovered.getId(),
                new IssuePublishService.PublishRequest(false, IssuePublishService.PublishRequest.ALL_WARNINGS, null, recoveredStamp));
        em.flush();
        em.clear();

        PublicationIssue after = em.find(PublicationIssue.class, recovered.getId());
        assertNotNull(after.getPublicTo(),
                "the recovered issue has an open-ended window; it would become the site's current publication");
        assertEquals(laterStamp.getTime() - 1, after.getPublicTo().getTime(),
                "it did not cap at its successor's stamp minus one millisecond");

        long openEnded = em.createQuery(
                        "SELECT COUNT(i) FROM PublicationIssue i WHERE i.series.id = :s "
                                + "AND i.status = :st AND i.publicTo IS NULL", Long.class)
                .setParameter("s", s.getId()).setParameter("st", IssueStatus.PUBLISHED).getSingleResult();
        assertEquals(1, openEnded, "exactly one issue of a series may have an open-ended window (I-18)");
    }

    /**
     * Step 13. A derived, open-ended predecessor is capped.
     *
     * Split into three tests rather than one: each publish takes a pessimistic
     * lock, and clearing the persistence context between them inside a single
     * transaction pulls the ground out from under it.
     */
    @BindsRule({"I-14"})
    @Test
    @Transactional
    public void anOpenEndedPredecessorIsCapped() {
        Date newStamp = new Date(1_760_000_000_000L);
        PublicationSeries s = series(SeriesCadence.WEEKLY, TimeRelation.PUBLISHED_IN_INTERVAL,
                ReleaseMode.MANUAL_GATE, NextIssueCreation.MANUAL, SeriesStatus.ACTIVE);
        PublicationIssue predecessor = publishedIssue(s, new Date(1_700_000_000_000L), null,
                PublicWindowSource.DERIVED, IssueStatus.PUBLISHED);
        PublicationIssue next = issue(s, new Date(newStamp.getTime() - 7 * 24 * 3600_000L));
        em.flush();

        previewFor(next);
        publishService.publish(next.getId(),
                new IssuePublishService.PublishRequest(false, IssuePublishService.PublishRequest.ALL_WARNINGS, null, newStamp));
        em.flush();

        PublicationIssue after = em.find(PublicationIssue.class, predecessor.getId());
        em.refresh(after);
        assertNotNull(after.getPublicTo(), "the predecessor was not capped; two issues now claim to be current");
        assertEquals(newStamp.getTime() - 1, after.getPublicTo().getTime(),
                "the cap is not the new stamp minus one millisecond");
    }

    /** Step 13. A RETIRED predecessor is capped too -- retiring leaves the window in place. */
    @Test
    @Transactional
    public void aRetiredPredecessorIsAlsoCapped() {
        Date newStamp = new Date(1_760_000_000_000L);
        PublicationSeries s = series(SeriesCadence.WEEKLY, TimeRelation.PUBLISHED_IN_INTERVAL,
                ReleaseMode.MANUAL_GATE, NextIssueCreation.MANUAL, SeriesStatus.ACTIVE);
        PublicationIssue retired = publishedIssue(s, new Date(1_700_000_000_000L), null,
                PublicWindowSource.DERIVED, IssueStatus.RETIRED);
        PublicationIssue next = issue(s, new Date(newStamp.getTime() - 7 * 24 * 3600_000L));
        em.flush();

        previewFor(next);
        publishService.publish(next.getId(),
                new IssuePublishService.PublishRequest(false, IssuePublishService.PublishRequest.ALL_WARNINGS, null, newStamp));
        em.flush();

        PublicationIssue after = em.find(PublicationIssue.class, retired.getId());
        em.refresh(after);
        assertNotNull(after.getPublicTo(),
                "a RETIRED predecessor was skipped, leaving it uncapped and its bracket contested");
    }

    /** Step 13. A hand-chosen window end is left alone -- somebody decided that value. */
    @BindsRule({"I-19"})
    @Test
    @Transactional
    public void aManuallyClosedPredecessorIsLeftAlone() {
        Date newStamp = new Date(1_760_000_000_000L);
        Date handChosen = new Date(1_750_000_000_000L);
        PublicationSeries s = series(SeriesCadence.WEEKLY, TimeRelation.PUBLISHED_IN_INTERVAL,
                ReleaseMode.MANUAL_GATE, NextIssueCreation.MANUAL, SeriesStatus.ACTIVE);
        PublicationIssue manual = publishedIssue(s, new Date(1_700_000_000_000L), handChosen,
                PublicWindowSource.MANUAL, IssueStatus.PUBLISHED);
        PublicationIssue next = issue(s, new Date(newStamp.getTime() - 7 * 24 * 3600_000L));
        em.flush();

        previewFor(next);
        publishService.publish(next.getId(),
                new IssuePublishService.PublishRequest(false, IssuePublishService.PublishRequest.ALL_WARNINGS, null, newStamp));
        em.flush();

        PublicationIssue after = em.find(PublicationIssue.class, manual.getId());
        em.refresh(after);
        assertEquals(handChosen, after.getPublicTo(), "a hand-chosen window end was overwritten");
    }
    /**
     * The successor arrives NAMED, with a desc row per configured language.
     *
     * It was created with none at all. The create path documents why that is not
     * allowed -- "a nameless issue is unfindable in every list that shows it", and
     * a language with no row has nowhere to put its file name, surfacing later as
     * "no such language" at upload. The auto-created issue is the one an admin
     * finds waiting every week, so it was the one issue that had neither.
     *
     * The existing successor test asserted the interval and stopped, which is why
     * this went unseen: nobody had looked at what the successor was CALLED.
     */
    @Test
    @Transactional
    public void theSuccessorIsNamedInEveryConfiguredLanguage() {
        Date stamp = new Date(1_700_000_000_000L);
        PublicationSeries all = series(SeriesCadence.WEEKLY, TimeRelation.PUBLISHED_IN_INTERVAL,
                ReleaseMode.MANUAL_GATE, NextIssueCreation.AUTO_ON_PUBLISH, SeriesStatus.ACTIVE);
        PublicationIssue i = issue(all, new Date(stamp.getTime() - 7 * 24 * 3600_000L));
        em.flush();

        previewFor(i);
        var result = publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(false, IssuePublishService.PublishRequest.ALL_WARNINGS, null, stamp));
        assertNotNull(result.successorId());

        em.flush();
        em.clear();
        PublicationIssue successor = em.find(PublicationIssue.class, result.successorId());

        assertEquals(all.getLanguages().size(), successor.getDescs().size(),
                "the successor carries " + successor.getDescs().size() + " desc row(s) for a series "
                        + "declaring " + all.getLanguages().size() + " language(s)");
        for (PublicationIssueDesc d : successor.getDescs()) {
            assertNotNull(d.getName(), "no name for " + d.getLang());
            assertFalse(d.getName().isBlank(),
                    "a blank name for " + d.getLang() + "; the issue is unfindable in every list");
        }
    }

    /** Step 14. The successor is created only when all four clauses hold. */
    @Test
    @Transactional
    public void aSuccessorIsCreatedOnlyWhenEveryClauseHolds() {
        Date stamp = new Date(1_700_000_000_000L);

        // Positive: all four.
        PublicationSeries all = series(SeriesCadence.WEEKLY, TimeRelation.PUBLISHED_IN_INTERVAL,
                ReleaseMode.MANUAL_GATE, NextIssueCreation.AUTO_ON_PUBLISH, SeriesStatus.ACTIVE);
        PublicationIssue i = issue(all, new Date(stamp.getTime() - 7 * 24 * 3600_000L));
        em.flush();
        previewFor(i);
        var result = publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(false, IssuePublishService.PublishRequest.ALL_WARNINGS, null, stamp));
        assertNotNull(result.successorId(), "no successor was created when every clause held");

        em.flush();
        em.clear();
        PublicationIssue successor = em.find(PublicationIssue.class, result.successorId());
        assertEquals(stamp, successor.getIntervalFrom(),
                "the successor does not start at this issue's stamp; that chaining is what removes drift");

        // One clause false at a time -- each must produce NO successor.
        assertNoSuccessor(series(SeriesCadence.NONE, TimeRelation.PUBLISHED_IN_INTERVAL,
                ReleaseMode.MANUAL_GATE, NextIssueCreation.AUTO_ON_PUBLISH, SeriesStatus.ACTIVE), "cadence NONE");
        assertNoSuccessor(series(SeriesCadence.WEEKLY, TimeRelation.PUBLISHED_IN_INTERVAL,
                ReleaseMode.MANUAL_GATE, NextIssueCreation.MANUAL, SeriesStatus.ACTIVE), "MANUAL creation");
        // RETIRED rather than DRAFT, and the difference is the point: publishing is
        // allowed from a retired series -- an admin retiring a weekly on a Tuesday
        // must still be able to release the issue already assembled for Wednesday
        // -- while CREATING one is not. A draft series cannot publish at all, which
        // the case below asserts on its own.
        assertNoSuccessor(series(SeriesCadence.WEEKLY, TimeRelation.PUBLISHED_IN_INTERVAL,
                ReleaseMode.MANUAL_GATE, NextIssueCreation.AUTO_ON_PUBLISH, SeriesStatus.RETIRED),
                "is no longer ACTIVE");
        assertNoSuccessor(series(SeriesCadence.YEARLY, TimeRelation.IN_FORCE_AT_CUTOFF,
                ReleaseMode.MANUAL_GATE, NextIssueCreation.AUTO_ON_PUBLISH, SeriesStatus.ACTIVE),
                "does not tile");
    }

    private void assertNoSuccessor(PublicationSeries s, String why) {
        Date stamp = new Date(1_700_000_000_000L);
        // An IN_FORCE_AT_CUTOFF issue has NO lower bound -- 531 production issues
        // carry none -- and the rail refuses one that does, because a lower bound
        // would make the resolver ask for messages published in a window this
        // publication does not have.
        boolean tiles = s.getTimeRelation() == TimeRelation.PUBLISHED_IN_INTERVAL;
        PublicationIssue i = issue(s, tiles ? new Date(stamp.getTime() - 7 * 24 * 3600_000L) : null);
        em.flush();
        previewFor(i);
        var result = publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(false, IssuePublishService.PublishRequest.ALL_WARNINGS, null, stamp));
        assertNull(result.successorId(), "a successor was created for a series that " + why);
    }

    /** Step 15. Exactly one PUBLISHED entry, carrying the warnings nobody acknowledged. */
    @Test
    @Transactional
    public void thePublishAuditRecordsWhatNobodyAcknowledged() {
        PublicationSeries auto = series(SeriesCadence.WEEKLY, TimeRelation.PUBLISHED_IN_INTERVAL,
                ReleaseMode.AUTO_RELEASE, NextIssueCreation.MANUAL, SeriesStatus.ACTIVE);
        PublicationIssue i = issue(auto, new Date(1_699_000_000_000L));
        em.flush();

        previewFor(i);
        var result = publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(false, Set.of(), null, new Date(1_700_000_000_000L)));
        em.flush();
        em.clear();

        List<IssueAuditEntry> entries = em.createQuery(
                        "SELECT a FROM IssueAuditEntry a WHERE a.issue.id = :id AND a.action = org.niord.core.publication.series.AuditAction.PUBLISHED",
                        IssueAuditEntry.class)
                .setParameter("id", i.getId()).getResultList();

        assertEquals(1, entries.size(), "expected exactly one PUBLISHED entry, got " + entries.size());
        IssueAuditEntry entry = entries.get(0);
        assertEquals(ActorKind.SYSTEM, entry.getActorKind(),
                "an AUTO_RELEASE publish has no human actor");
        assertNull(entry.getUser());

        assertNotNull(entry.getDetail(), "the entry carries no detail");
        @SuppressWarnings("unchecked")
        Map<String, Object> detail = (Map<String, Object>) entry.getDetail();
        assertTrue(detail.containsKey("unacknowledgedWarnings"),
                "the entry does not record which warnings went unacknowledged; under AUTO_RELEASE nobody "
                        + "saw them at all, which is materially different from a human confirming them");
        assertEquals(result.unacknowledgedWarnings().size(),
                ((List<?>) detail.get("unacknowledgedWarnings")).size());
    }

    /**
     * Step 15. The entry also records the release checklist it was published
     * against, and which of its rows somebody signed off.
     *
     * The rail is computed fresh on every read, over a corpus that keeps moving:
     * ask the same issue next week and rows that warned may pass and rows that
     * passed may warn. So the state it was in at the moment of release survives
     * nowhere else, and "this went out with a warning, and somebody ticked it" is
     * exactly what a history panel is opened to establish.
     *
     * Only the APPLICABLE rows. A row about a question this issue never raises is
     * not an answer about this issue, and recording it as a passing check
     * overstates what was actually decided.
     */
    @Test
    @Transactional
    public void thePublishAuditRecordsTheChecklistItWasPublishedAgainst() {
        PublicationSeries s = series(SeriesCadence.WEEKLY, TimeRelation.PUBLISHED_IN_INTERVAL,
                ReleaseMode.MANUAL_GATE, NextIssueCreation.MANUAL, SeriesStatus.ACTIVE);
        Date intervalFrom = new Date(1_699_000_000_000L);
        Date stamp = new Date(1_700_000_000_000L);
        PublicationIssue i = issue(s, intervalFrom);

        // The one acknowledgeable condition there is: selected by the criteria,
        // withdrawn, and open past the cut-off all the same.
        cancelledButStillOpen(new Date(intervalFrom.getTime() + 3600_000L),
                new Date(stamp.getTime() + 86_400_000L));
        previewFor(i);
        em.flush();

        var result = publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(false,
                        Set.of(ResolutionWarningCode.CANCELLED_BUT_DATE_ALIVE.name()), null, stamp));
        em.flush();
        em.clear();

        List<Map<String, Object>> checklist = checklistOf(publishedEntry(i.getId()));
        assertFalse(checklist.isEmpty(), "the entry records no checklist at all");

        for (Map<String, Object> row : checklist) {
            assertEquals(Set.of("code", "severity", "passed", "acknowledged"), row.keySet(),
                    "the recorded row carries something other than the four facts that stay true; "
                            + "the rail's own detail line names instants and counts that have moved");
            assertTrue(PublishChecklistService.CODES.contains(row.get("code")),
                    "the entry records a code the rail cannot emit: " + row.get("code"));
            assertFalse("BLOCK".equals(row.get("severity")) && Boolean.FALSE.equals(row.get("passed")),
                    "a failing BLOCK row was recorded on a publish that happened; the gate refuses "
                            + "those before anything is stamped");
        }

        // The severity recorded is the row's OWN, which is three values and not
        // two: a row that neither warns nor blocks says so rather than being
        // dropped or promoted into a warning.
        assertEquals("OK", row(checklist, "MEMBERS_RESOLVED").get("severity"),
                "the resolver ran, so its row is neither a warning nor a block");

        Map<String, Object> acknowledged = row(checklist, "CANCELLED_MEMBERS_ALIVE_AT_CUTOFF");
        assertEquals("WARN", acknowledged.get("severity"));
        assertEquals(Boolean.FALSE, acknowledged.get("passed"),
                "the seeded member is cancelled and still open at the cut-off");
        assertEquals(Boolean.TRUE, acknowledged.get("acknowledged"),
                "the row is read against the code the GATE compares, which is the resolution "
                        + "warning rather than the row's own name");

        for (Map<String, Object> other : checklist) {
            if (!"CANCELLED_MEMBERS_ALIVE_AT_CUTOFF".equals(other.get("code"))) {
                assertEquals(Boolean.FALSE, other.get("acknowledged"),
                        other.get("code") + " reports as acknowledged; nothing ticks a row that "
                                + "carries no acknowledgement code");
            }
        }

        // Only the applicable rows. This issue is the first of its series and its
        // document is generated by the publish itself, so neither the chaining row
        // nor the one demanding bytes up front is a question it raises.
        List<Object> codes = checklist.stream().map(r -> r.get("code")).toList();
        assertTrue(codes.contains("ISSUE_OPEN"), "an applicable row is missing from the record");
        assertFalse(codes.contains("INTERVAL_CHAINED"),
                "an inapplicable row was recorded as a check that passed; there is no predecessor "
                        + "for this issue to be chained to");
        assertFalse(codes.contains("FILE_PRESENT_PER_LANGUAGE"),
                "publish writes the document, so requiring it beforehand is not a question this "
                        + "issue raises");

        // The half that was already there is untouched by the half that was added.
        @SuppressWarnings("unchecked")
        Map<String, Object> detail = (Map<String, Object>) publishedEntry(i.getId()).getDetail();
        assertTrue(detail.containsKey("unacknowledgedWarnings"));
        assertTrue(result.unacknowledgedWarnings().isEmpty(),
                "the only warning was acknowledged, so nothing went unacknowledged");
        assertEquals(result.unacknowledgedWarnings().size(),
                ((List<?>) detail.get("unacknowledgedWarnings")).size());

        // And it reaches the panel. The history line carries the detail as the
        // entry holds it; a mapping that dropped it would leave everything
        // recorded here readable only from the database.
        assertEquals(detail, publishedEntry(i.getId()).toVo().getDetail(),
                "the history line does not carry what the entry recorded");
    }

    /**
     * An unattended release records the same row as NOT acknowledged.
     *
     * This is the pairing that makes the flag worth storing. Under AUTO_RELEASE
     * the publish is not refused and the warning still stands -- nobody was there
     * to tick it -- and a reader who cannot tell that from a human having signed
     * it off is reading a trail that overstates what happened.
     */
    @Test
    @Transactional
    public void anUnattendedReleaseRecordsItsWarningAsUnsigned() {
        PublicationSeries s = series(SeriesCadence.WEEKLY, TimeRelation.PUBLISHED_IN_INTERVAL,
                ReleaseMode.AUTO_RELEASE, NextIssueCreation.MANUAL, SeriesStatus.ACTIVE);
        Date intervalFrom = new Date(1_699_000_000_000L);
        Date stamp = new Date(1_700_000_000_000L);
        PublicationIssue i = issue(s, intervalFrom);

        cancelledButStillOpen(new Date(intervalFrom.getTime() + 3600_000L),
                new Date(stamp.getTime() + 86_400_000L));
        previewFor(i);
        em.flush();

        var result = publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(false, Set.of(), null, stamp));
        em.flush();
        em.clear();

        IssueAuditEntry entry = publishedEntry(i.getId());
        Map<String, Object> acknowledged = row(checklistOf(entry), "CANCELLED_MEMBERS_ALIVE_AT_CUTOFF");
        assertEquals(Boolean.FALSE, acknowledged.get("passed"));
        assertEquals(Boolean.FALSE, acknowledged.get("acknowledged"),
                "an unattended release signed off a warning nobody saw");

        assertEquals(List.of(ResolutionWarningCode.CANCELLED_BUT_DATE_ALIVE.name()),
                result.unacknowledgedWarnings(),
                "the warning stands: AUTO_RELEASE publishes past it rather than acknowledging it");
    }

    /**
     * A caller that acknowledges EVERYTHING still signs only what it could sign.
     *
     * OVERLAPPING_ISSUE is the name of a rail row and also the name of a warning
     * nobody can acknowledge, and the two are unrelated facts that happen to
     * spell the same. A record that matched a row against its own name would badge
     * that row as confirmed by a caller who was never shown a control for it --
     * and the trail would then say a human signed off on something no dialog has
     * ever asked about.
     */
    @Test
    @Transactional
    public void onlyARowWithAnAcknowledgementCodeCanBeRecordedAsSigned() {
        PublicationSeries s = series(SeriesCadence.WEEKLY, TimeRelation.PUBLISHED_IN_INTERVAL,
                ReleaseMode.MANUAL_GATE, NextIssueCreation.MANUAL, SeriesStatus.ACTIVE);
        PublicationIssue i = issue(s, new Date(1_699_000_000_000L));
        previewFor(i);
        em.flush();

        publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(false,
                        IssuePublishService.PublishRequest.ALL_WARNINGS, null,
                        new Date(1_700_000_000_000L)));
        em.flush();
        em.clear();

        List<Map<String, Object>> checklist = checklistOf(publishedEntry(i.getId()));
        assertEquals(Boolean.FALSE, row(checklist, "OVERLAPPING_ISSUE").get("acknowledged"),
                "a row with no acknowledgement code was recorded as signed off");
        assertEquals(Boolean.TRUE, row(checklist, "CANCELLED_MEMBERS_ALIVE_AT_CUTOFF").get("acknowledged"),
                "the one acknowledgeable row was not recorded as signed off");
    }

    /** The one PUBLISHED entry of an issue, read back from the database. */
    private IssueAuditEntry publishedEntry(Integer issueId) {
        List<IssueAuditEntry> entries = em.createQuery(
                        "SELECT a FROM IssueAuditEntry a WHERE a.issue.id = :id "
                                + "AND a.action = org.niord.core.publication.series.AuditAction.PUBLISHED",
                        IssueAuditEntry.class)
                .setParameter("id", issueId).getResultList();
        assertEquals(1, entries.size(), "expected exactly one PUBLISHED entry, got " + entries.size());
        return entries.get(0);
    }

    /** The recorded checklist, as it comes back off the wire-shaped detail column. */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> checklistOf(IssueAuditEntry entry) {
        assertNotNull(entry.getDetail(), "the entry carries no detail");
        Object checklist = ((Map<String, Object>) entry.getDetail()).get("checklist");
        assertNotNull(checklist, "the entry does not record the checklist it was published against");
        return (List<Map<String, Object>>) checklist;
    }

    private Map<String, Object> row(List<Map<String, Object>> checklist, String code) {
        return checklist.stream().filter(r -> code.equals(r.get("code"))).findFirst()
                .orElseThrow(() -> new AssertionError("no recorded checklist row for " + code));
    }

    /**
     * The audit vocabulary is closed, and it stays the size the translations cover.
     *
     * An action outside the vocabulary can no longer be written at all -- the
     * column, the service signatures and the call sites are all typed on the enum,
     * so a misspelling is a compile error rather than something the trail has to
     * be policed for. What still needs an assertion is the size: every action
     * needs a translation key, and one added without one renders in the history
     * panel as its own raw key. Bumping this number is the moment to add it.
     */
    @Test
    public void theAuditVocabularyIsClosedAndSpecific() {
        assertEquals(29, AuditAction.values().length, "the audit vocabulary changed size");
        assertTrue(List.of(AuditAction.values()).containsAll(
                        List.of(AuditAction.LINK_SET, AuditAction.LINK_CLEARED,
                                AuditAction.INTERVAL_CHANGED, AuditAction.NAME_CHANGED,
                                AuditAction.CRITERIA_OVERRIDDEN, AuditAction.FILE_REPLACED_MANUALLY,
                                // Moving a publication to another desk: it leaves one
                                // team's screens and appears on another's, and the
                                // question afterwards is always who and why.
                                AuditAction.OWNER_TRANSFERRED)),
                "the vocabulary is specific by design: a history panel cannot render "
                        + "'something changed', so every mutation an admin can make has its own value");
    }

    /** The wire spelling is the constant name, so the stored vocabulary is unchanged by the typing. */
    @Test
    @Transactional
    public void anAuditEntryRendersItsActionByName() {
        PublicationSeries s = series(SeriesCadence.WEEKLY, TimeRelation.PUBLISHED_IN_INTERVAL,
                ReleaseMode.MANUAL_GATE, NextIssueCreation.MANUAL, SeriesStatus.ACTIVE);
        PublicationIssue i = issue(s, new Date());
        em.flush();

        IssueAuditEntry entry = auditService.created(i, null, AuditAction.CREATED);
        assertEquals("CREATED", entry.toVo().getAction(),
                "the wire carries the action as a string, and the string is the constant's name");
    }

    /** Double publish returns the already-published signal, carrying the winner's stamp. */
    @Test
    @Transactional
    public void publishingTwiceIsRefusedWithTheOriginalStamp() {
        PublicationSeries s = series(SeriesCadence.WEEKLY, TimeRelation.PUBLISHED_IN_INTERVAL,
                ReleaseMode.MANUAL_GATE, NextIssueCreation.MANUAL, SeriesStatus.ACTIVE);
        PublicationIssue i = issue(s, new Date(1_699_000_000_000L));
        em.flush();

        Date stamp = new Date(1_700_000_000_000L);
        previewFor(i);
        publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(false, IssuePublishService.PublishRequest.ALL_WARNINGS, null, stamp));
        em.flush();

        IssuePublishService.AlreadyPublishedException e =
                assertThrows(IssuePublishService.AlreadyPublishedException.class,
                        () -> publishService.publish(i.getId(),
                                new IssuePublishService.PublishRequest(false, IssuePublishService.PublishRequest.ALL_WARNINGS, null, new Date())));
        assertEquals("ISSUE_ALREADY_PUBLISHED", e.code());
        assertEquals(stamp, e.stampedAt(), "the refusal did not carry the original stamp");
    }

    // ============================================================ step 3b: warnings

    /**
     * A warning nobody acknowledged refuses the publish BEFORE anything is frozen
     * or written. The issue stays OPEN with no member rows and no PUBLISHED entry;
     * the refusal names the codes so the dialog can list them.
     *
     * The warning is SEEDED rather than borrowed from the corpus. The only
     * acknowledgeable code is "cancelled or expired, yet still open at the
     * cut-off", and it needs a member in exactly that state -- so this test makes
     * one. Reaching for whatever the shared database happened to hold made the
     * sole guard for "a warning blocks the release" depend on a fixed window of a
     * seeded corpus, which is a guard that goes quiet without failing.
     */
    @Test
    @Transactional
    public void anUnacknowledgedWarningRefusesBeforeAnythingIsFrozen() {
        PublicationSeries s = series(SeriesCadence.WEEKLY, TimeRelation.PUBLISHED_IN_INTERVAL,
                ReleaseMode.MANUAL_GATE, NextIssueCreation.MANUAL, SeriesStatus.ACTIVE);
        Date intervalFrom = new Date(1_699_000_000_000L);
        Date stamp = new Date(1_700_000_000_000L);
        PublicationIssue i = issue(s, intervalFrom);

        // Published inside the window, so the criteria select it; CANCELLED, so it
        // has been withdrawn; and open past the cut-off, so nothing about the dates
        // says so. That combination is invisible in an exclusions panel -- the
        // message IS a member -- which is why it is the one warning a human has to
        // sign off rather than one they would notice unaided.
        cancelledButStillOpen(new Date(intervalFrom.getTime() + 3600_000L),
                new Date(stamp.getTime() + 86_400_000L));
        previewFor(i);
        em.flush();

        IssuePublishService.WarningsNotAcknowledgedException e =
                assertThrows(IssuePublishService.WarningsNotAcknowledgedException.class,
                        () -> publishService.publish(i.getId(),
                                new IssuePublishService.PublishRequest(false, Set.of(), null, stamp)));
        assertEquals("WARNING_NOT_ACKNOWLEDGED", e.code());
        assertEquals(List.of("CANCELLED_BUT_DATE_ALIVE"), e.codes(),
                "only an acknowledgeable warning may refuse a publish: a code with no control to "
                        + "clear it would refuse the same request forever");

        assertEquals(IssueStatus.OPEN, i.getStatus(), "the status flipped despite the refusal");
        assertEquals(0L, em.createQuery("SELECT COUNT(m) FROM IssueMember m WHERE m.issue = :i", Long.class)
                .setParameter("i", i).getSingleResult(), "member rows were frozen despite the refusal");
        assertTrue(auditService.forIssue(i).stream().noneMatch(a -> AuditAction.PUBLISHED == a.getAction()),
                "a PUBLISHED entry was written despite the refusal");

        // Acknowledging exactly those codes is what lets the same publish through.
        previewFor(i);
        publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(false, Set.copyOf(e.codes()), null,
                        new Date(1_700_000_000_000L)));
        assertEquals(IssueStatus.PUBLISHED, i.getStatus());
    }

    // ============================================================ step 10: the document

    /** 10b. Not regenerating promotes the newest preview to the official file. */
    @Test
    @Transactional
    public void notRegeneratingPromotesTheNewestPreview() throws Exception {
        PublicationSeries s = series(SeriesCadence.WEEKLY, TimeRelation.PUBLISHED_IN_INTERVAL,
                ReleaseMode.MANUAL_GATE, NextIssueCreation.MANUAL, SeriesStatus.ACTIVE);
        s.setReportId("some-report");
        PublicationIssue i = issue(s, new Date(1_699_000_000_000L));
        em.flush();

        previews.record(i, "da", "preview.pdf", "preview-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        previewFor(i);
        publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(false, IssuePublishService.PublishRequest.ALL_WARNINGS,
                        null, new Date(1_700_000_000_000L)));
        em.flush();

        PublicationIssueDesc da = i.getDescs().stream().filter(d -> "da".equals(d.getLang())).findFirst().orElseThrow();
        assertNotNull(da.getFilePath(), "the language has no document after publish");
        assertEquals(FileSource.GENERATED, da.getFileSource());
        assertTrue(da.getFilePath().startsWith(i.getRepoPath() + "/"), "the file is not under the issue's repo path");
        java.nio.file.Path official = paths.repoRoot().resolve(da.getFilePath());
        assertTrue(java.nio.file.Files.isRegularFile(official), "the official file does not exist: " + official);
        assertEquals("preview-bytes", java.nio.file.Files.readString(official),
                "the official file is not the promoted preview");
    }

    /** 10a/10c. A generated series that cannot produce a document does not publish. */
    @Test
    @Transactional
    public void aGeneratedSeriesThatCannotProduceADocumentIsRefused() {
        PublicationSeries s = series(SeriesCadence.WEEKLY, TimeRelation.PUBLISHED_IN_INTERVAL,
                ReleaseMode.MANUAL_GATE, NextIssueCreation.MANUAL, SeriesStatus.ACTIVE);
        s.setReportId("no-such-report");
        PublicationIssue i = issue(s, new Date(1_699_000_000_000L));
        em.flush();

        // 10a: the report does not exist, so the render fails and the publish with it.
        assertThrows(IssueRenderService.RenderFailedException.class,
                () -> publishService.publish(i.getId(),
                        new IssuePublishService.PublishRequest(true, IssuePublishService.PublishRequest.ALL_WARNINGS,
                                null, new Date(1_700_000_000_000L))));
        assertEquals(IssueStatus.OPEN, i.getStatus(), "a publish without a document flipped the status");

        // 10b without a preview to promote: equally refused, not silently skipped.
        assertThrows(IssueRenderService.RenderFailedException.class,
                () -> publishService.publish(i.getId(),
                        new IssuePublishService.PublishRequest(false, IssuePublishService.PublishRequest.ALL_WARNINGS,
                                null, new Date(1_700_000_000_000L))));
        assertEquals(IssueStatus.OPEN, i.getStatus());
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
