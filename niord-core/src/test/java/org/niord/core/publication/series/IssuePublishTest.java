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
import org.niord.core.publication.series.resolve.IssueOrdering;
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

    /**
     * The stubbed renderer is one bean for the whole module, and the bytes it
     * produces are a static. A test that names its own bytes and does not put them
     * back leaves every later test in the JVM rendering them, which is a failure
     * that depends on the order the classes happen to run in.
     */
    @org.junit.jupiter.api.AfterEach
    public void restoreTheRenderer() {
        StubIssueRenderService.reset();
    }

    @Inject
    IssuePublishService publishService;

    @Inject
    IssueEditService edits;

    @Inject
    IssueAuditService auditService;

    @Inject
    IssuePreviewService previews;

    @Inject
    PublicationPathService paths;

    @Inject
    EntityManager em;

    // The successor is named by its public id, so it is looked up the way a client
    // would have to: by the one address an issue has outside the database.
    @Inject
    PublicationIssueService issues;

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
                            new IssuePublishService.PublishRequest(IssuePublishService.PublishRequest.ALL_WARNINGS, null, null));
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

        var result = publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(IssuePublishService.PublishRequest.ALL_WARNINGS, null, pastStamp));

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

        publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(IssuePublishService.PublishRequest.ALL_WARNINGS, null, new Date(1_700_000_000_000L)));
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

        publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(IssuePublishService.PublishRequest.ALL_WARNINGS, null, new Date(1_700_000_000_000L)));
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

        publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(IssuePublishService.PublishRequest.ALL_WARNINGS,
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

        publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(IssuePublishService.PublishRequest.ALL_WARNINGS, null, new Date(1_700_000_000_000L)));
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

        publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(IssuePublishService.PublishRequest.ALL_WARNINGS, null, new Date(1_700_000_000_000L)));
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

        publishService.publish(recovered.getId(),
                new IssuePublishService.PublishRequest(IssuePublishService.PublishRequest.ALL_WARNINGS, null, recoveredStamp));
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

        publishService.publish(next.getId(),
                new IssuePublishService.PublishRequest(IssuePublishService.PublishRequest.ALL_WARNINGS, null, newStamp));
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

        publishService.publish(next.getId(),
                new IssuePublishService.PublishRequest(IssuePublishService.PublishRequest.ALL_WARNINGS, null, newStamp));
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

        publishService.publish(next.getId(),
                new IssuePublishService.PublishRequest(IssuePublishService.PublishRequest.ALL_WARNINGS, null, newStamp));
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

        var result = publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(IssuePublishService.PublishRequest.ALL_WARNINGS, null, stamp));
        assertNotNull(result.successorId());

        em.flush();
        em.clear();
        PublicationIssue successor = issues.findByPublicId(result.successorId());

        assertEquals(all.getLanguages().size(), successor.getDescs().size(),
                "the successor carries " + successor.getDescs().size() + " desc row(s) for a series "
                        + "declaring " + all.getLanguages().size() + " language(s)");
        for (PublicationIssueDesc d : successor.getDescs()) {
            assertNotNull(d.getName(), "no name for " + d.getLang());
            assertFalse(d.getName().isBlank(),
                    "a blank name for " + d.getLang() + "; the issue is unfindable in every list");
        }
    }

    /**
     * Step 14. The successor is created only when all three clauses hold.
     *
     * TILING USED TO BE A FOURTH CLAUSE and is not one any more. It says what the
     * successor's period LOOKS like -- an in-force issue has one bound, so its
     * successor opens with none -- and never said whether anything was due. As a
     * clause it meant the two largest weekly publications in the estate published
     * without opening anything, and somebody had to create next week's issue by
     * hand every week. The shape an in-force successor is born with is asserted
     * in {@link InForceCadenceTest}.
     */
    @Test
    @Transactional
    public void aSuccessorIsCreatedOnlyWhenEveryClauseHolds() {
        Date stamp = new Date(1_700_000_000_000L);

        // Positive: all three.
        PublicationSeries all = series(SeriesCadence.WEEKLY, TimeRelation.PUBLISHED_IN_INTERVAL,
                ReleaseMode.MANUAL_GATE, NextIssueCreation.AUTO_ON_PUBLISH, SeriesStatus.ACTIVE);
        PublicationIssue i = issue(all, new Date(stamp.getTime() - 7 * 24 * 3600_000L));
        em.flush();
        var result = publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(IssuePublishService.PublishRequest.ALL_WARNINGS, null, stamp));
        assertNotNull(result.successorId(), "no successor was created when every clause held");

        em.flush();
        em.clear();
        PublicationIssue successor = issues.findByPublicId(result.successorId());
        assertEquals(stamp, successor.getIntervalFrom(),
                "the successor does not start at this issue's stamp; that chaining is what removes drift");
        assertEquals(IntervalBoundSource.STAMPED, successor.getIntervalFromSource(),
                "a tiling successor's start is a recorded instant, not a nominal one");

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
        var result = publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(IssuePublishService.PublishRequest.ALL_WARNINGS, null, stamp));
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

        var result = publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(Set.of(), null, new Date(1_700_000_000_000L)));
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
        em.flush();

        var result = publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(
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
        em.flush();

        var result = publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(Set.of(), null, stamp));
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
        em.flush();

        publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(
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
        assertEquals(33, AuditAction.values().length, "the audit vocabulary changed size");
        assertTrue(List.of(AuditAction.values()).containsAll(
                        List.of(AuditAction.LINK_SET, AuditAction.LINK_CLEARED,
                                AuditAction.INTERVAL_CHANGED, AuditAction.NAME_CHANGED,
                                AuditAction.CRITERIA_OVERRIDDEN, AuditAction.FILE_REPLACED_MANUALLY,
                                // The edition string, typed rather than derived: it
                                // is printed on the cover, expands into a file-name
                                // pattern, and is what tells two publications of one
                                // period apart.
                                AuditAction.EDITION_CHANGED,
                                // The public window's end, set or cleared by hand --
                                // distinct from a neighbour's publish capping it,
                                // and the only action that puts an expired
                                // publication back on the public site.
                                AuditAction.VISIBILITY_WINDOW_CHANGED,
                                // Moving a publication to another desk: it leaves one
                                // team's screens and appears on another's, and the
                                // question afterwards is always who and why.
                                AuditAction.OWNER_TRANSFERRED,
                                // The three per-edition overrides, each its own
                                // action because each is a separate surprise to
                                // whoever opens the history. The printed numbering
                                // changes the cover, the file name and the report
                                // heading at once while the derived numbers stand;
                                // the file name moves where the release will write;
                                // and the report decides the whole shape of the
                                // document.
                                AuditAction.NUMBERING_CHANGED,
                                AuditAction.FILE_NAME_CHANGED,
                                AuditAction.REPORT_CHANGED)),
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
        publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(IssuePublishService.PublishRequest.ALL_WARNINGS, null, stamp));
        em.flush();

        IssuePublishService.AlreadyPublishedException e =
                assertThrows(IssuePublishService.AlreadyPublishedException.class,
                        () -> publishService.publish(i.getId(),
                                new IssuePublishService.PublishRequest(IssuePublishService.PublishRequest.ALL_WARNINGS, null, new Date())));
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
        em.flush();

        IssuePublishService.WarningsNotAcknowledgedException e =
                assertThrows(IssuePublishService.WarningsNotAcknowledgedException.class,
                        () -> publishService.publish(i.getId(),
                                new IssuePublishService.PublishRequest(Set.of(), null, stamp)));
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
        publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(Set.copyOf(e.codes()), null,
                        new Date(1_700_000_000_000L)));
        assertEquals(IssueStatus.PUBLISHED, i.getStatus());
    }

    // ============================================================ step 10: the document

    /**
     * 10a. A release renders, and it renders even when a preview exists.
     *
     * The second half is the one worth having. A release used to be able to
     * promote the newest preview instead of rendering, and the bytes on the
     * public site were then whatever had been rendered at some earlier moment --
     * before a member was curated in or out, before an override was recorded.
     * The document and the frozen member rows of one issue could disagree with
     * each other, with nothing to say which was the publication. So a preview is
     * recorded here with bytes nothing else would produce, and the official file
     * must NOT be those bytes.
     */
    @Test
    @Transactional
    public void publishingRendersRatherThanShippingAPreview() throws Exception {
        PublicationSeries s = series(SeriesCadence.WEEKLY, TimeRelation.PUBLISHED_IN_INTERVAL,
                ReleaseMode.MANUAL_GATE, NextIssueCreation.MANUAL, SeriesStatus.ACTIVE);
        s.setReportId("some-report");
        PublicationIssue i = issue(s, new Date(1_699_000_000_000L));
        em.flush();

        previews.record(i, "da", "preview.pdf",
                "the bytes somebody looked at".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StubIssueRenderService.renders("the bytes the release rendered");

        publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(IssuePublishService.PublishRequest.ALL_WARNINGS,
                        null, new Date(1_700_000_000_000L)));
        em.flush();

        PublicationIssueDesc da = i.getDescs().stream().filter(d -> "da".equals(d.getLang())).findFirst().orElseThrow();
        assertNotNull(da.getFilePath(), "the language has no document after publish");
        assertEquals(FileSource.GENERATED, da.getFileSource());
        assertTrue(da.getFilePath().startsWith(i.getRepoPath() + "/"), "the file is not under the issue's repo path");
        java.nio.file.Path official = paths.repoRoot().resolve(da.getFilePath());
        assertTrue(java.nio.file.Files.isRegularFile(official), "the official file does not exist: " + official);
        assertEquals("the bytes the release rendered", java.nio.file.Files.readString(official),
                "the release shipped the preview instead of rendering the list it froze");
    }

    /** 10a/10c. A generated series that cannot produce a document does not publish. */
    @Test
    @Transactional
    public void aGeneratedSeriesThatCannotProduceADocumentIsRefused() {
        PublicationSeries s = series(SeriesCadence.WEEKLY, TimeRelation.PUBLISHED_IN_INTERVAL,
                ReleaseMode.MANUAL_GATE, NextIssueCreation.MANUAL, SeriesStatus.ACTIVE);
        s.setReportId(StubIssueRenderService.UNRENDERABLE);
        PublicationIssue i = issue(s, new Date(1_699_000_000_000L));
        em.flush();

        // The render fails, and the publish with it.
        assertThrows(IssueRenderService.RenderFailedException.class,
                () -> publishService.publish(i.getId(),
                        new IssuePublishService.PublishRequest(IssuePublishService.PublishRequest.ALL_WARNINGS,
                                null, new Date(1_700_000_000_000L))));
        assertEquals(IssueStatus.OPEN, i.getStatus(), "a publish without a document flipped the status");

        // And a preview does not rescue it: there is no path that ships bytes the
        // release did not produce, so a failing render is a failing release.
        previews.record(i, "da", "preview.pdf",
                "a preview".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThrows(IssueRenderService.RenderFailedException.class,
                () -> publishService.publish(i.getId(),
                        new IssuePublishService.PublishRequest(IssuePublishService.PublishRequest.ALL_WARNINGS,
                                null, new Date(1_700_000_000_000L))));
        assertEquals(IssueStatus.OPEN, i.getStatus());
    }

    // ========================================================== a compilation

    /** A weekly, two published weeks of it, and the annual that compiles them. */
    private record Compiled(PublicationSeries source, PublicationSeries annual,
                            PublicationIssue week1, PublicationIssue week2,
                            Message a, Message b, Message c) {
    }

    /**
     * The estate a compiled release is taken over.
     *
     * The source weeks are frozen BY HAND rather than published through the
     * transaction, because what is under test here is what the COMPILATION's
     * publish writes -- and producing the same rows through two full releases
     * first would make every assertion below depend on each of their ten steps.
     */
    private Compiled compiledEstate() {
        PublicationSeries source = series(SeriesCadence.WEEKLY, TimeRelation.PUBLISHED_IN_INTERVAL,
                ReleaseMode.MANUAL_GATE, NextIssueCreation.MANUAL, SeriesStatus.ACTIVE);

        PublicationSeries annual = series(SeriesCadence.YEARLY, TimeRelation.PUBLISHED_IN_INTERVAL,
                ReleaseMode.MANUAL_GATE, NextIssueCreation.MANUAL, SeriesStatus.ACTIVE);
        annual.setTimeRelation(TimeRelation.COMPILED_FROM_SOURCE);
        // A compilation runs no query and judges no liveness; S-24 refuses both.
        annual.setCriteria(null);
        annual.setAliveAtCutoff(null);
        annual.setSourceSeries(source);
        em.merge(annual);

        Message a = compiledMessage("NM-A");
        Message b = compiledMessage("NM-B");
        Message c = compiledMessage("NM-C");

        PublicationIssue week1 = frozenWeek(source, new Date(1_699_000_000_000L),
                new Date(1_699_100_000_000L), List.of(a, b));
        PublicationIssue week2 = frozenWeek(source, new Date(1_699_100_000_000L),
                new Date(1_699_200_000_000L), List.of(c));

        em.flush();
        return new Compiled(source, annual, week1, week2, a, b, c);
    }

    private Message compiledMessage(String shortId) {
        Message m = new Message();
        m.setUid(UUID.randomUUID().toString());
        m.setMessageSeries(messageSeries("dma-nm"));
        m.setShortId(shortId);
        m.setMainType(MainType.NM);
        m.setType(Type.TEMPORARY_NOTICE);
        m.setStatus(Status.PUBLISHED);
        m.setPublishDateFrom(new Date(1_699_000_000_000L));
        em.persist(m);
        return m;
    }

    /** A published source week with its member rows already frozen, in print order. */
    private PublicationIssue frozenWeek(PublicationSeries s, Date from, Date stamp,
                                        List<Message> members) {
        PublicationIssue i = issue(s, from);
        i.setStatus(IssueStatus.PUBLISHED);
        i.setCutoffStampedAt(stamp);
        i.setPublishedAt(stamp);
        i.setMemberCount(members.size());
        em.merge(i);
        int sortIndex = 0;
        for (Message m : members) {
            IssueMember row = new IssueMember();
            row.setIssue(i);
            row.setMessageUid(m.getUid());
            row.setMessage(m);
            row.setSortIndex(sortIndex++);
            row.setFrozenShortId(m.getShortId());
            row.setFrozenMainType(m.getMainType().name());
            row.setFrozenType(m.getType().name());
            row.setFrozenStatus(m.getStatus().name());
            row.setFrozenPublishDateFrom(m.getPublishDateFrom());
            row.setSource(MemberSource.CRITERIA);
            em.persist(row);
        }
        return i;
    }

    private List<IssueMember> frozenRowsOf(PublicationIssue issue) {
        return em.createQuery(
                        "SELECT m FROM IssueMember m WHERE m.issue = :i ORDER BY m.sortIndex",
                        IssueMember.class)
                .setParameter("i", issue).getResultList();
    }

    /**
     * Step 6 on a compilation: every row says which week printed it.
     *
     * The publicId is the whole record of the grouping. The document is drawn as
     * a section per source week and the screen as a heading per source week, and
     * neither can be reconstructed from the member set afterwards -- the source
     * issues inside a period GROW, so asking again next year would answer for a
     * different set of weeks than the one this release actually compiled.
     *
     * A MANUAL INCLUDE carries none, and that is the second half of the rule: it
     * came from no week, so inventing one would print it under a heading nobody
     * chose. It is what the final "added by hand" section exists for.
     */
    @Test
    @Transactional
    public void acompiledReleaseFreezesWhichSourceIssuePrintedEachRow() {
        Compiled e = compiledEstate();
        PublicationIssue annualIssue = issue(e.annual(), new Date(1_698_900_000_000L));

        Message byHand = compiledMessage("NM-BY-HAND");
        org.niord.core.user.User author = new org.niord.core.user.User();
        author.setUsername(TestIds.id("curator-"));
        em.persist(author);
        IssueOverride include = new IssueOverride();
        include.setIssue(annualIssue);
        include.setAuthor(author);
        include.setMessageUid(byHand.getUid());
        include.setKind(OverrideKind.INCLUDE);
        include.setReason("it belongs in the year although no week printed it");
        em.persist(include);
        em.flush();

        publishService.publish(annualIssue.getId(),
                new IssuePublishService.PublishRequest(IssuePublishService.PublishRequest.ALL_WARNINGS,
                        null, new Date(1_699_300_000_000L)));
        em.flush();

        List<IssueMember> rows = frozenRowsOf(annualIssue);
        assertEquals(List.of("NM-A", "NM-B", "NM-C", "NM-BY-HAND"),
                rows.stream().map(IssueMember::getFrozenShortId).toList(),
                "the frozen rows are not the two weeks in their own order with the manual "
                        + "include last");

        assertEquals(List.of(MemberSource.COMPILED, MemberSource.COMPILED, MemberSource.COMPILED,
                        MemberSource.OVERRIDE_INCLUDE),
                rows.stream().map(IssueMember::getSource).toList(),
                "a compiled row was frozen as a criteria row; nothing ran a query for it");

        assertEquals(List.of(e.week1().getPublicId(), e.week1().getPublicId(),
                        e.week2().getPublicId()),
                rows.subList(0, 3).stream().map(IssueMember::getSourceIssuePublicId).toList(),
                "the rows do not name the weeks that printed them");
        assertNull(rows.get(3).getSourceIssuePublicId(),
                "a message somebody added by hand was filed under a week that never printed it");
    }

    /**
     * I-20. Step 7 on a compilation records the operand it actually used.
     *
     * The source SERIES because that is the immutable name of what was compiled,
     * and the source ISSUES because the set of issues whose cut-off falls inside
     * a period GROWS: a weekly published next month whose stamp lands in last
     * year does not belong to a release already made, and without the list
     * nothing could ever say which weeks this document holds.
     *
     * The columns it leaves EMPTY are as much of the record: there was no
     * criteria document, no liveness rule and no resolved operand list, and a
     * value in any of them would describe a query that was never run.
     */
    @BindsRule({"I-20"})
    @Test
    @Transactional
    public void acompiledReleaseRecordsItsSourceSeriesAndEverySourceIssue() {
        Compiled e = compiledEstate();
        PublicationIssue annualIssue = issue(e.annual(), new Date(1_698_900_000_000L));
        em.flush();

        publishService.publish(annualIssue.getId(),
                new IssuePublishService.PublishRequest(IssuePublishService.PublishRequest.ALL_WARNINGS,
                        null, new Date(1_699_300_000_000L)));
        em.flush();

        assertEquals(MembershipProvenance.COMPILED, annualIssue.getMembershipProvenance(),
                "a compiled release recorded EXACT, which claims a query was run and reproduced");
        assertEquals(TimeRelation.COMPILED_FROM_SOURCE, annualIssue.getSnapshotTimeRelation());
        assertEquals(e.source().getSeriesId(), annualIssue.getSnapshotSourceSeriesId());
        assertEquals(e.week1().getPublicId() + "," + e.week2().getPublicId(),
                annualIssue.getSnapshotSourceIssueIds(),
                "the header does not name every source issue whose rows this release holds, in "
                        + "cut-off order");

        assertNull(annualIssue.getSnapshotAliveAtCutoff(),
                "the header states a liveness rule; none was applied, and each source week judged "
                        + "liveness at its own cut-off");
        assertNull(annualIssue.getCriteriaSnapshot(),
                "the header carries a criteria document a compilation never had");
        assertNull(annualIssue.getSnapshotSeriesIds());
        assertNull(annualIssue.getSnapshotMainTypes());
        assertNull(annualIssue.getSnapshotAreaIds());
        assertNull(annualIssue.getSnapshotCategoryIds());
        assertNull(annualIssue.getSnapshotChartNumbers());
    }

    /** And an ordinary weekly release is untouched by any of it. */
    @Test
    @Transactional
    public void anordinaryReleaseCarriesNoCompilationHeaderAtAll() {
        PublicationSeries s = series(SeriesCadence.WEEKLY, TimeRelation.PUBLISHED_IN_INTERVAL,
                ReleaseMode.MANUAL_GATE, NextIssueCreation.MANUAL, SeriesStatus.ACTIVE);
        PublicationIssue i = issue(s, new Date(1_699_000_000_000L));
        em.flush();

        publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(IssuePublishService.PublishRequest.ALL_WARNINGS,
                        null, new Date(1_700_000_000_000L)));
        em.flush();

        assertEquals(MembershipProvenance.EXACT, i.getMembershipProvenance());
        assertNull(i.getSnapshotSourceSeriesId(),
                "a query-backed release recorded a source series; the two new header columns are "
                        + "the compiled regime's alone");
        assertNull(i.getSnapshotSourceIssueIds());
        assertNotNull(i.getSnapshotAliveAtCutoff(),
                "the ordinary header stopped recording the liveness rule it resolved under");
        for (IssueMember m : frozenRowsOf(i)) {
            assertNull(m.getSourceIssuePublicId(),
                    "a criteria row was filed under a source issue");
        }
    }

    /**
     * A year released short of one of its weeks is refused until somebody says so.
     *
     * Releasing early is legitimate and recurring -- an annual put out in the
     * first days of January, before the last week of December is published -- so
     * this is an acknowledgement rather than a block. What it stops is doing it
     * without noticing: a week missing from a document of a thousand notices is
     * invisible, and the fifteenth rail row and this refusal are the same fact
     * said to the two readers that need it.
     */
    @Test
    @Transactional
    public void acompilationWhoseSourcePeriodIsUnfinishedIsRefusedUntilItIsAcknowledged() {
        Compiled e = compiledEstate();
        // A third week, still open, whose period closes inside the annual's.
        PublicationIssue openWeek = issue(e.source(), new Date(1_699_200_000_000L));
        openWeek.setIntervalTo(new Date(1_699_250_000_000L));
        em.merge(openWeek);

        PublicationIssue annualIssue = issue(e.annual(), new Date(1_698_900_000_000L));
        em.flush();

        IssuePublishService.WarningsNotAcknowledgedException refused =
                assertThrows(IssuePublishService.WarningsNotAcknowledgedException.class,
                        () -> publishService.publish(annualIssue.getId(),
                                new IssuePublishService.PublishRequest(Set.of(), null,
                                        new Date(1_699_300_000_000L))));
        assertTrue(refused.codes().contains(ResolutionWarningCode.SOURCE_ISSUES_INCOMPLETE.name()),
                "the release was refused for something other than the unfinished period: "
                        + refused.codes());
        assertEquals(IssueStatus.OPEN, annualIssue.getStatus(),
                "a refused publish flipped the status anyway");

        // Acknowledged, it goes out -- carrying the two weeks that ARE published.
        publishService.publish(annualIssue.getId(),
                new IssuePublishService.PublishRequest(
                        Set.of(ResolutionWarningCode.SOURCE_ISSUES_INCOMPLETE.name()), null,
                        new Date(1_699_300_000_000L)));
        em.flush();
        assertEquals(IssueStatus.PUBLISHED, annualIssue.getStatus());
        assertEquals(3, frozenRowsOf(annualIssue).size());
    }

    /**
     * An amend RE-DERIVES from the sources, at the original cut-off.
     *
     * The same rule as for any native issue, and it is what makes the regime
     * usable: a source week retired or amended after the annual went out changes
     * what the annual should hold, and the amend is how that correction reaches
     * it. The cut-off is NOT re-taken, so the window is the one the release was
     * decided over -- an amend must never silently re-decide which weeks are in
     * the year.
     */
    @Test
    @Transactional
    public void anamendOfACompilationReDerivesFromTheSources() {
        Compiled e = compiledEstate();
        PublicationIssue annualIssue = issue(e.annual(), new Date(1_698_900_000_000L));
        em.flush();

        publishService.publish(annualIssue.getId(),
                new IssuePublishService.PublishRequest(IssuePublishService.PublishRequest.ALL_WARNINGS,
                        null, new Date(1_699_300_000_000L)));
        em.flush();
        assertEquals(3, frozenRowsOf(annualIssue).size());

        // The second week is withdrawn: what went out for that period should not
        // stand, so the year must stop carrying what it printed.
        e.week2().setStatus(IssueStatus.RETIRED);
        em.merge(e.week2());
        em.flush();

        publishService.amend(annualIssue.getId(),
                new IssuePublishService.AmendRequest(IssuePublishService.PublishRequest.ALL_WARNINGS,
                        null, "the second week was withdrawn"));
        em.flush();

        List<IssueMember> rows = frozenRowsOf(annualIssue);
        assertEquals(List.of("NM-A", "NM-B"),
                rows.stream().map(IssueMember::getFrozenShortId).toList(),
                "the amend did not re-derive from the sources; the year still prints a week that "
                        + "was withdrawn");
        assertEquals(e.week1().getPublicId(), annualIssue.getSnapshotSourceIssueIds(),
                "the header still names the withdrawn week among the sources it holds");
        assertEquals(new Date(1_699_300_000_000L), annualIssue.getCutoffStampedAt(),
                "the amend moved the cut-off, which would re-decide which weeks are in the year");
    }

    /**
     * What reaches the RENDERER is the document's sections, not a flat list.
     *
     * The renderer has no way to work out where a section begins: the ordered
     * list it prints is uids and nothing else, and which week owned each of them
     * is a fact only the release knows. So the sections travel in the request,
     * and this is the assertion that they do -- in printed order, named as the
     * weeks are named, with what somebody added by hand in a section of its own
     * at the end, belonging to no week.
     *
     * The ids inside a section are a partition of the ordered list rather than a
     * copy of it: a section that carried its own messages could print one the
     * document does not contain, and nothing downstream would notice.
     */
    @Test
    @Transactional
    public void acompiledReleaseHandsTheRendererOneSectionPerSourceIssue() {
        Compiled e = compiledEstate();
        // Named apart, because a section is headed by the week's own name and a
        // fixture in which every week is called the same thing cannot tell whether
        // the heading came from the right one.
        e.week1().getDescs().get(0).setName("Week one");
        e.week2().getDescs().get(0).setName("Week two");
        em.merge(e.week1());
        em.merge(e.week2());

        PublicationIssue annualIssue = issue(e.annual(), new Date(1_698_900_000_000L));
        Message byHand = compiledMessage("NM-BY-HAND");
        org.niord.core.user.User author = new org.niord.core.user.User();
        author.setUsername(TestIds.id("curator-"));
        em.persist(author);
        IssueOverride include = new IssueOverride();
        include.setIssue(annualIssue);
        include.setAuthor(author);
        include.setMessageUid(byHand.getUid());
        include.setKind(OverrideKind.INCLUDE);
        include.setReason("it belongs in the year although no week printed it");
        em.persist(include);
        em.flush();

        StubIssueRenderService.reset();
        publishService.publish(annualIssue.getId(),
                new IssuePublishService.PublishRequest(IssuePublishService.PublishRequest.ALL_WARNINGS,
                        null, new Date(1_699_300_000_000L)));
        em.flush();

        IssueRenderService.RenderRequest request = StubIssueRenderService.lastRequest();
        assertNotNull(request, "the release rendered nothing at all");
        List<IssueRenderService.RenderGroup> groups = request.groups();
        assertNotNull(groups, "a compiled release handed the renderer a flat list");

        assertEquals(List.of(e.week1().getPublicId(), e.week2().getPublicId()),
                groups.subList(0, 2).stream().map(IssueRenderService.RenderGroup::publicId).toList(),
                "the sections are not the source weeks in cut-off order");
        assertEquals(List.of("Week one", "Week two"),
                groups.subList(0, 2).stream().map(IssueRenderService.RenderGroup::name).toList(),
                "the sections are not headed by the weeks that printed them");
        assertEquals(List.of(e.a().getUid(), e.b().getUid()), groups.get(0).messageIds(),
                "the first week's section is not its own rows in its own order");
        assertEquals(List.of(e.c().getUid()), groups.get(1).messageIds());

        IssueRenderService.RenderGroup manual = groups.get(groups.size() - 1);
        assertTrue(manual.manual(), "the last section is not the one added by hand");
        assertNull(manual.publicId(), "the manual section names a week that never printed it");
        assertEquals(List.of(byHand.getUid()), manual.messageIds());
        assertEquals(3, groups.size(), "the document grew a section nothing was filed under");

        // And the sections partition the list the renderer prints, exactly.
        assertEquals(request.orderedMessages().stream().map(m -> m.getId()).toList(),
                groups.stream().flatMap(g -> g.messageIds().stream()).toList(),
                "the sections and the printed list are two different documents");
    }

    /**
     * A SOURCE WEEK THAT FILED NOTHING IS STILL A SECTION.
     *
     * The sections are the record of what the year covered, not of what happened
     * to have rows. A year that quietly prints fifty-one of its fifty-two weeks
     * is a document claiming a week was never covered, and there is nothing in
     * the document for a reader to notice the claim by -- so the empty week keeps
     * its place, in the order it was compiled in, with no message ids under it.
     *
     * The list comes from the snapshot header rather than from the frozen rows,
     * because the rows are exactly what cannot answer for a week that filed
     * nothing. The workbench states the same rule over the same list.
     */
    @Test
    @Transactional
    public void aweekThatFiledNothingIsStillASectionOfTheReleasedDocument() {
        Compiled e = compiledEstate();
        // Between the two weeks that did print, so an empty section at the END
        // would not pass for the right answer.
        PublicationIssue silent = frozenWeek(e.source(), new Date(1_699_100_000_000L),
                new Date(1_699_150_000_000L), List.of());

        PublicationIssue annualIssue = issue(e.annual(), new Date(1_698_900_000_000L));
        em.flush();

        StubIssueRenderService.reset();
        publishService.publish(annualIssue.getId(),
                new IssuePublishService.PublishRequest(IssuePublishService.PublishRequest.ALL_WARNINGS,
                        null, new Date(1_699_300_000_000L)));
        em.flush();

        List<IssueRenderService.RenderGroup> groups = StubIssueRenderService.lastRequest().groups();
        assertEquals(List.of(e.week1().getPublicId(), silent.getPublicId(), e.week2().getPublicId()),
                groups.stream().map(IssueRenderService.RenderGroup::publicId).toList(),
                "the week that filed nothing is missing from the document's sections, which now "
                        + "claims the year never covered it");
        assertEquals(List.of(), groups.get(1).messageIds(),
                "the empty section was filled from somewhere");
        assertEquals(List.of(e.a().getUid(), e.b().getUid()), groups.get(0).messageIds());
        assertEquals(List.of(e.c().getUid()), groups.get(2).messageIds());

        // And the header the sections were built from says the same three weeks.
        assertEquals(String.join(",", e.week1().getPublicId(), silent.getPublicId(),
                        e.week2().getPublicId()),
                annualIssue.getSnapshotSourceIssueIds(),
                "the document's sections and the issue's own record name different weeks");
    }

    /**
     * And the PREVIEW draws the same document, off the survey.
     *
     * The preview has no snapshot to read -- nothing is frozen yet -- so it takes
     * the covered weeks from the survey, which is the same list the freeze will
     * write into the header. An admin who cannot see the empty week in the
     * preview cannot see it before the release either.
     */
    @Test
    @Transactional
    public void apreviewSectionsTheSameWeeksTheReleaseWill() {
        Compiled e = compiledEstate();
        PublicationIssue silent = frozenWeek(e.source(), new Date(1_699_100_000_000L),
                new Date(1_699_150_000_000L), List.of());

        PublicationIssue annualIssue = issue(e.annual(), new Date(1_698_900_000_000L));
        em.flush();

        StubIssueRenderService.reset();
        publishService.preview(annualIssue.getId());

        List<IssueRenderService.RenderGroup> groups = StubIssueRenderService.lastRequest().groups();
        assertNotNull(groups, "a compiled preview was drawn as a flat list");
        assertEquals(List.of(e.week1().getPublicId(), silent.getPublicId(), e.week2().getPublicId()),
                groups.stream().map(IssueRenderService.RenderGroup::publicId).toList(),
                "the preview and the release would section the same year differently");
        assertEquals(List.of(), groups.get(1).messageIds());
    }

    /**
     * A row whose source the covered list does not name still prints.
     *
     * Which weeks the document covers and which week owns each row are two
     * different reads, and a member that vanished because they disagreed would
     * be a notice missing from a published document with nothing to say it was
     * ever there. So such a row gets a section of its own, after the covered
     * ones and in arrival order -- and what came from no week at all stays last,
     * under the heading that says it was added by hand.
     */
    @Test
    public void arowWhoseSourceIsNotAmongTheCoveredWeeksGetsASectionAfterThem() {
        List<IssueOrdering.Orderable> ordered = List.of(
                orderable("uid-a"), orderable("uid-stray"), orderable("uid-b"), orderable("uid-hand"));

        Map<String, String> sourceByUid = new java.util.LinkedHashMap<>();
        sourceByUid.put("uid-a", "week-1");
        sourceByUid.put("uid-stray", "week-gone");
        sourceByUid.put("uid-b", "week-2");
        // uid-hand names no source at all: a manual include.

        List<IssueRenderService.RenderGroup> sections = IssuePublishService.sectionsOf(
                ordered, sourceByUid, List.of("week-1", "week-2", "week-3"));

        assertEquals(java.util.Arrays.asList("week-1", "week-2", "week-3", "week-gone", null),
                sections.stream().map(IssueRenderService.RenderGroup::publicId).toList(),
                "a covered week is out of order, or the row naming a week nobody covered was "
                        + "dropped from the print");
        assertEquals(List.of("uid-a"), sections.get(0).messageIds());
        assertEquals(List.of("uid-b"), sections.get(1).messageIds());
        assertEquals(List.of(), sections.get(2).messageIds(), "week 3 filed nothing and is empty");
        assertEquals(List.of("uid-stray"), sections.get(3).messageIds());

        IssueRenderService.RenderGroup manual = sections.get(sections.size() - 1);
        assertTrue(manual.manual(), "what came from no week is not the last section");
        assertEquals(List.of("uid-hand"), manual.messageIds());
    }

    /** A document with no grouping at all asks for no sections. */
    @Test
    public void sectionsOfAnUncompiledDocumentAreNone() {
        assertNull(IssuePublishService.sectionsOf(List.of(orderable("uid-a")), null, null));
    }

    private static IssueOrdering.Orderable orderable(String uid) {
        return new IssueOrdering.Orderable(uid, null, null, null, null, null, null, null, null);
    }

    /** And an ordinary weekly hands over no sections at all. */
    @Test
    @Transactional
    public void anordinaryReleaseHandsTheRendererNoGroups() {
        PublicationSeries s = series(SeriesCadence.WEEKLY, TimeRelation.PUBLISHED_IN_INTERVAL,
                ReleaseMode.MANUAL_GATE, NextIssueCreation.MANUAL, SeriesStatus.ACTIVE);
        PublicationIssue i = issue(s, new Date(1_699_000_000_000L));
        em.flush();

        StubIssueRenderService.reset();
        publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(IssuePublishService.PublishRequest.ALL_WARNINGS,
                        null, new Date(1_700_000_000_000L)));
        em.flush();

        IssueRenderService.RenderRequest request = StubIssueRenderService.lastRequest();
        assertNotNull(request);
        assertNull(request.groups(),
                "a query-backed release declared sections; a document with no sections must ask "
                        + "for none, or every template would have to know which regime it is in");
    }

}
