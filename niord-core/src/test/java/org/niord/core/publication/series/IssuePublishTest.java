package org.niord.core.publication.series;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.transaction.UserTransaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.criteria.MessageSeriesCriterionVo;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.vo.MessagePublication;

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
    EntityManager em;

    @Inject
    UserTransaction tx;

    // ------------------------------------------------------------------ fixtures

    private PublicationSeries series(SeriesCadence cadence, TimeRelation relation,
                                     ReleaseMode release, NextIssueCreation next, SeriesStatus status) {
        PublicationCategory c = new PublicationCategory();
        c.setCategoryId("cat-" + UUID.randomUUID().toString().substring(0, 8));
        c.setPriority(100);
        c.setPublish(true);
        em.persist(c);

        PublicationSeries s = new PublicationSeries();
        s.setSeriesId("s-" + UUID.randomUUID().toString().substring(0, 8));
        s.setStatus(status);
        s.setContentMode(ContentMode.GENERATED_FROM_QUERY);
        s.setCadence(cadence);
        s.setTimeRelation(relation);
        s.setAliveAtCutoff(relation == TimeRelation.IN_FORCE_AT_CUTOFF);
        s.setReleaseMode(release);
        s.setNextIssueCreation(next);
        s.setPublicAuthority(PublicAuthority.LEGACY);
        s.setMessagePublication(MessagePublication.NONE);
        s.setNumberingScheme(NumberingScheme.ISO_WEEK_YEAR);
        s.setCategory(c);
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
        issueId = issue(s, new Date(System.currentTimeMillis() - 3600_000L)).getId();
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
                            IssuePublishService.PublishRequest.manual(null));
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
                new IssuePublishService.PublishRequest(false, Set.of(), null, pastStamp));

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
                new IssuePublishService.PublishRequest(false, Set.of(), null, new Date(1_700_000_000_000L)));
        em.flush();
        em.clear();

        PublicationIssue after = em.find(PublicationIssue.class, i.getId());

        // Step 7: the interval the resolve ACTUALLY used.
        assertEquals(from, after.getSnapshotIntervalFrom(),
                "snapshotIntervalFrom does not record the bound the resolve used; that divergence is "
                        + "exactly why the column exists");
        assertNotNull(after.getSnapshotFrozenAt());
        assertEquals("PUBLISHED_IN_INTERVAL", after.getSnapshotTimeRelation());

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
                new IssuePublishService.PublishRequest(false, Set.of(), null, new Date(1_700_000_000_000L)));
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
        author.setUsername("curator-" + UUID.randomUUID().toString().substring(0, 8));
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
                new IssuePublishService.PublishRequest(false, Set.of(), null, new Date(1_700_000_000_000L)));
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
                new IssuePublishService.PublishRequest(false, Set.of(), null, new Date(1_700_000_000_000L)));
        em.flush();
        em.clear();

        PublicationIssue after = em.find(PublicationIssue.class, i.getId());
        assertNotNull(after.getPublishedAt());
        assertEquals(after.getCutoffStampedAt(), after.getPublishedAt(),
                "publishedAt must equal the stamp");
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
                new IssuePublishService.PublishRequest(false, Set.of(), null, recoveredStamp));
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
        PublicationIssue next = issue(s, new Date(1_700_000_000_000L));
        em.flush();

        publishService.publish(next.getId(),
                new IssuePublishService.PublishRequest(false, Set.of(), null, newStamp));
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
        PublicationIssue next = issue(s, new Date(1_700_000_000_000L));
        em.flush();

        publishService.publish(next.getId(),
                new IssuePublishService.PublishRequest(false, Set.of(), null, newStamp));
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
        PublicationIssue next = issue(s, new Date(1_700_000_000_000L));
        em.flush();

        publishService.publish(next.getId(),
                new IssuePublishService.PublishRequest(false, Set.of(), null, newStamp));
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
                new IssuePublishService.PublishRequest(false, Set.of(), null, stamp));
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
        var result = publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(false, Set.of(), null, stamp));
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
        assertNoSuccessor(series(SeriesCadence.WEEKLY, TimeRelation.PUBLISHED_IN_INTERVAL,
                ReleaseMode.MANUAL_GATE, NextIssueCreation.AUTO_ON_PUBLISH, SeriesStatus.DRAFT), "not ACTIVE");
        assertNoSuccessor(series(SeriesCadence.YEARLY, TimeRelation.IN_FORCE_AT_CUTOFF,
                ReleaseMode.MANUAL_GATE, NextIssueCreation.AUTO_ON_PUBLISH, SeriesStatus.ACTIVE),
                "does not tile");
    }

    private void assertNoSuccessor(PublicationSeries s, String why) {
        Date stamp = new Date(1_700_000_000_000L);
        PublicationIssue i = issue(s, new Date(stamp.getTime() - 7 * 24 * 3600_000L));
        em.flush();
        var result = publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(false, Set.of(), null, stamp));
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
                new IssuePublishService.PublishRequest(false, Set.of(), null, new Date(1_700_000_000_000L)));
        em.flush();
        em.clear();

        List<IssueAuditEntry> entries = em.createQuery(
                        "SELECT a FROM IssueAuditEntry a WHERE a.issue.id = :id AND a.action = 'PUBLISHED'",
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

    /** The audit vocabulary is closed: an unknown action is refused. */
    @Test
    @Transactional
    public void anUnknownAuditActionIsRefused() {
        PublicationSeries s = series(SeriesCadence.WEEKLY, TimeRelation.PUBLISHED_IN_INTERVAL,
                ReleaseMode.MANUAL_GATE, NextIssueCreation.MANUAL, SeriesStatus.ACTIVE);
        PublicationIssue i = issue(s, new Date());
        em.flush();

        assertThrows(IssueAuditService.UnknownAuditActionException.class,
                () -> auditService.created(i, null, "PUBLISHED_MAYBE"),
                "an action outside the vocabulary was accepted; the Historik panel would show it as unknown");

        // The count is a tripwire, not a rule: every action needs a translation
        // key, and one added without one renders in the Historik panel as its own
        // raw key. Bumping this number is the moment to add it.
        assertEquals(25, IssueAuditService.ACTIONS.size(), "the audit vocabulary changed size");
        assertTrue(IssueAuditService.ACTIONS.containsAll(
                        List.of("LINK_SET", "LINK_CLEARED", "INTERVAL_CHANGED", "NAME_CHANGED",
                                "CRITERIA_OVERRIDDEN")),
                "the vocabulary is specific by design: a Historik panel cannot render "
                        + "'something changed', so every mutation an admin can make has its own value");
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
                new IssuePublishService.PublishRequest(false, Set.of(), null, stamp));
        em.flush();

        IssuePublishService.AlreadyPublishedException e =
                assertThrows(IssuePublishService.AlreadyPublishedException.class,
                        () -> publishService.publish(i.getId(),
                                new IssuePublishService.PublishRequest(false, Set.of(), null, new Date())));
        assertEquals("ISSUE_ALREADY_PUBLISHED", e.code());
        assertEquals(stamp, e.stampedAt(), "the refusal did not carry the original stamp");
    }
}
