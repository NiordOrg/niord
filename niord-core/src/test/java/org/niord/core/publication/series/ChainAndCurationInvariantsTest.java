package org.niord.core.publication.series;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.criteria.MessageSeriesCriterionVo;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.core.message.Message;
import org.niord.model.message.MainType;
import org.niord.model.message.Status;
import org.niord.model.message.Type;
import org.niord.core.user.User;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B1.7b. The chain invariants, and the author on a curation decision.
 *
 * I-7 and I-9 were left pending on B2.3b and O-2 on B2.9. All three tasks
 * completed, so the behaviour exists -- what was missing is anything holding it
 * to the rule. These assert the stored STATE rather than an API refusal, because
 * that is what the invariants are about: I-7 and I-9 describe what must be true
 * of a series' issues after publishing, not what create() must reject.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class ChainAndCurationInvariantsTest {

    @Inject
    IssueLifecycleService lifecycle;

    @Inject
    IssuePublishService publishService;

    @Inject
    IssueCurationService curation;

    @Inject
    EntityManager em;

    private User user() {
        User u = new User();
        u.setUsername("u-" + UUID.randomUUID().toString().substring(0, 8));
        em.persist(u);
        return u;
    }

    private PublicationSeries series() {
        PublicationCategory c = new PublicationCategory();
        c.setCategoryId("cat-" + UUID.randomUUID().toString().substring(0, 8));
        c.setPriority(100);
        em.persist(c);

        PublicationSeries s = new PublicationSeries();
        s.setSeriesId("s-" + UUID.randomUUID().toString().substring(0, 8));
        s.setStatus(SeriesStatus.ACTIVE);
        s.setContentMode(ContentMode.GENERATED_FROM_QUERY);
        s.setCadence(SeriesCadence.WEEKLY);
        s.setTimeRelation(TimeRelation.PUBLISHED_IN_INTERVAL);
        s.setAliveAtCutoff(false);
        s.setReleaseMode(ReleaseMode.MANUAL_GATE);
        s.setNextIssueCreation(NextIssueCreation.MANUAL);
        s.setPublicAuthority(PublicAuthority.LEGACY);
        s.setMessagePublication(MessagePublication.NONE);
        s.setNumberingScheme(NumberingScheme.ISO_WEEK_YEAR);
        // No report: these tests are about the chain and the curation, and a series with a report now renders a document at publish.
        s.setCategory(c);
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

    private PublicationIssue publishAt(PublicationSeries s, Date intervalFrom, Date stamp) {
        PublicationIssue i = lifecycle.create(s, intervalFrom, IntervalBoundSource.STAMPED, user());
        em.flush();
        publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(false, IssuePublishService.PublishRequest.ALL_WARNINGS, null, stamp));
        em.flush();
        return em.find(PublicationIssue.class, i.getId());
    }

    /**
     * I-9: within a series, cutoffStampedAt is strictly increasing in publish order.
     *
     * Breaking it breaks the chain arithmetic that everything else rests on --
     * an issue whose stamp is at or before its predecessor's would make the
     * predecessor's public window close before it opened.
     */
    @BindsRule({"I-9"})
    @Test
    @Transactional
    public void theStampsOfASeriesAreStrictlyIncreasing() {
        PublicationSeries s = series();

        Date firstStamp = new Date(1_700_000_000_000L);
        Date secondStamp = new Date(1_700_600_000_000L);

        PublicationIssue first = publishAt(s, new Date(1_699_000_000_000L), firstStamp);
        PublicationIssue second = publishAt(s, firstStamp, secondStamp);

        List<PublicationIssue> chain = em.createQuery(
                        "SELECT i FROM PublicationIssue i WHERE i.series = :s "
                                + "AND i.cutoffStampedAt IS NOT NULL ORDER BY i.cutoffStampedAt",
                        PublicationIssue.class)
                .setParameter("s", s).getResultList();

        assertEquals(2, chain.size());
        for (int n = 1; n < chain.size(); n++) {
            assertTrue(chain.get(n).getCutoffStampedAt().after(chain.get(n - 1).getCutoffStampedAt()),
                    "stamps must strictly increase, or a predecessor's window closes before it opens");
        }
        assertEquals(firstStamp, first.getCutoffStampedAt());
        assertEquals(secondStamp, second.getCutoffStampedAt());
    }

    /**
     * I-7: a PUBLISHED_IN_INTERVAL issue's intervalFrom is its predecessor's
     * stamped cut-off.
     *
     * Asserted over the stored chain rather than as a refusal at create(): the
     * rule allows a deviation that an INTERVAL_CHANGED audit explains, so what
     * has to hold is the state, not the call.
     */
    @BindsRule({"I-7"})
    @Test
    @Transactional
    public void anIssueChainsOffItsPredecessorsStampedCutoff() {
        PublicationSeries s = series();

        Date firstStamp = new Date(1_700_000_000_000L);
        publishAt(s, new Date(1_699_000_000_000L), firstStamp);

        PublicationIssue second = publishAt(s, firstStamp, new Date(1_700_600_000_000L));

        assertEquals(firstStamp, second.getIntervalFrom(),
                "the successor opens exactly where its predecessor's stamped cut-off closed; a gap or "
                        + "an overlap here is a window that serves nothing or serves twice");
        assertEquals(IntervalBoundSource.STAMPED, second.getIntervalFromSource(),
                "and the bound records that it came from a stamp rather than from a nominal date");
    }

    /**
     * O-2: the author is recorded on every override, regardless of who may write.
     *
     * "Regardless of who may write" is the point: the permission question and the
     * attribution question are separate, and an override with no author cannot be
     * reviewed later no matter how legitimate the writer was.
     */
    @BindsRule({"O-2"})
    @Test
    @Transactional
    public void everyOverrideRecordsItsAuthor() {
        PublicationSeries s = series();
        PublicationIssue i = lifecycle.create(s, new Date(1_699_000_000_000L),
                IntervalBoundSource.STAMPED, user());
        em.flush();

        // Real messages, because O-6 now refuses an override naming nothing --
        // which is the guard working, and is why this test had to change with it.
        User curator = user();
        curation.include(i, message().getUid(), curator, "belongs in this week");
        curation.exclude(i, message().getUid(), curator, "withdrawn before release");
        em.flush();

        List<IssueOverride> overrides = curation.forIssue(i);
        assertEquals(2, overrides.size());
        for (IssueOverride o : overrides) {
            assertNotNull(o.getAuthor(), "an override with no author is unreviewable");
            assertEquals(curator.getUsername(), o.getAuthor().getUsername());
            assertNotNull(o.getReason());
        }
    }
    /**
     * O-6: an override naming a message that does not exist is a hard error.
     *
     * Never silent. An override that can never apply would sit in the audit trail
     * looking like a decision, and because the annex report takes its heading from
     * the first member, the visible result of a bad uid is an untitled PDF rather
     * than a complaint.
     */
    @BindsRule({"O-6"})
    @Test
    @Transactional
    public void anOverrideNamingNoMessageIsRefused() {
        PublicationSeries s = series();
        PublicationIssue i = lifecycle.create(s, new Date(1_699_000_000_000L),
                IntervalBoundSource.STAMPED, user());
        em.flush();

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> curation.include(i, "uid-that-does-not-exist", user(), "a good reason"));
        assertEquals("OVERRIDE_MESSAGE_NOT_FOUND", e.code());
    }

    /**
     * O-4: including a message the criteria already select is refused.
     *
     * The override would claim somebody decided to add a message that was already
     * there -- and if the criteria later narrow, that stale INCLUDE quietly keeps
     * a message the query no longer selects.
     */
    @BindsRule({"O-4"})
    @Test
    @Transactional
    public void includingAnExistingMemberIsRefused() {
        PublicationSeries s = series();
        PublicationIssue i = lifecycle.create(s, new Date(1_699_000_000_000L),
                IntervalBoundSource.STAMPED, user());
        em.flush();

        Message m = message();
        IssueMember member = new IssueMember();
        member.setIssue(i);
        member.setMessageUid(m.getUid());
        member.setSortIndex(0);
        member.setSource(MemberSource.CRITERIA);
        // The frozen caption columns are NOT NULL: they exist so a retired issue
        // can still be read without joining to a message that may have moved.
        member.setFrozenShortId("NM-001-26");
        member.setFrozenMainType("NM");
        member.setFrozenType("TEMPORARY_NOTICE");
        member.setFrozenStatus("PUBLISHED");
        em.persist(member);
        em.flush();

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> curation.include(i, m.getUid(), user(), "already selected"));
        assertEquals("OVERRIDE_ALREADY_A_MEMBER", e.code());

        // An EXCLUDE of the same uid is legal -- that is the whole point of a
        // curated removal, and O-4 is about INCLUDE alone.
        assertNotNull(curation.exclude(i, m.getUid(), user(), "withdrawn before release"));
    }

    /**
     * A message to point overrides at, WELL FORMED.
     *
     * mainType, type and status are set because they are NOT NULL on the frozen
     * member row this message will eventually become -- and because this database
     * is shared: IssueInvariantsTest picks its fixtures with
     * "SELECT m.uid FROM Message m ORDER BY m.id", so a malformed row left here
     * fails a different suite, nowhere near its cause.
     */
    private Message message() {
        Message m = new Message();
        m.setUid(UUID.randomUUID().toString());
        m.setMainType(MainType.NM);
        m.setType(Type.TEMPORARY_NOTICE);
        m.setStatus(Status.PUBLISHED);
        em.persist(m);
        em.flush();
        return m;
    }
}
