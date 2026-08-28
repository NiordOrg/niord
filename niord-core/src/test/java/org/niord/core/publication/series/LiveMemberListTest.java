package org.niord.core.publication.series;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.message.Message;
import org.niord.core.message.MessageSeries;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.criteria.MessageSeriesCriterionVo;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.series.vo.IssueMemberVo;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.core.user.User;
import org.niord.model.message.MainType;
import org.niord.model.message.Status;
import org.niord.model.message.Type;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * I10 for an issue that has not been frozen yet.
 *
 * An OPEN issue has no member rows -- the freeze writes them -- so a list built
 * from the rows answered an empty array for every issue anybody is actually
 * working on. That is the worst available answer: "nothing is in this issue" and
 * "this issue has not been published yet" are indistinguishable on the wire, and
 * only one of them is true.
 *
 * So an open issue is resolved LIVE, exactly as the publish would resolve it,
 * and nothing is written.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class LiveMemberListTest {

    @Inject
    IssueMemberListService memberList;

    @Inject
    IssueCurationService curation;

    @Inject
    IssueLifecycleService lifecycle;

    @Inject
    EntityManager em;

    private static final long WEEK = 7L * 24 * 3600 * 1000;
    private static final Date OPENS = new Date(1_699_000_000_000L);

    // ------------------------------------------------------------------ fixtures

    private String seriesId;

    private PublicationSeries series() {
        PublicationCategory c = new PublicationCategory();
        c.setCategoryId("cat-" + UUID.randomUUID().toString().substring(0, 8));
        c.setPriority(100);
        em.persist(c);

        // Its own message series, so the corpus cannot decide the member count.
        seriesId = "ms-" + UUID.randomUUID().toString().substring(0, 8);
        MessageSeries ms = new MessageSeries();
        ms.setSeriesId(seriesId);
        ms.setMainType(MainType.NM);
        em.persist(ms);

        PublicationSeries s = new PublicationSeries();
        s.setSeriesId("s-" + UUID.randomUUID().toString().substring(0, 8));
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
        s.getLanguages().add("da");

        IssueCriteriaVo doc = new IssueCriteriaVo();
        MessageSeriesCriterionVo node = new MessageSeriesCriterionVo();
        node.setValues(new ArrayList<>(List.of(seriesId)));
        doc.getCriteria().add(node);
        s.setCriteria(doc);

        s.createDesc("da").setName("Test series");
        em.persist(s);
        return s;
    }

    private Message message(String shortId) {
        MessageSeries ms = em.createQuery(
                        "SELECT ms FROM MessageSeries ms WHERE ms.seriesId = :id", MessageSeries.class)
                .setParameter("id", seriesId).getSingleResult();

        Message m = new Message();
        m.setUid(UUID.randomUUID().toString());
        m.setMessageSeries(ms);
        m.setShortId(shortId);
        m.setMainType(MainType.NM);
        m.setType(Type.TEMPORARY_NOTICE);
        m.setStatus(Status.PUBLISHED);
        m.setPublishDateFrom(new Date(OPENS.getTime() + 3600_000L));
        em.persist(m);
        return m;
    }

    private User user() {
        User u = new User();
        u.setUsername("u-" + UUID.randomUUID().toString().substring(0, 8));
        em.persist(u);
        return u;
    }

    // -------------------------------------------------------------------- cases

    /**
     * An open issue answers with what the criteria select right now.
     *
     * The rows carry the LIVE values in the columns a published issue carries
     * frozen ones -- that is what the row is for while the issue is open -- and
     * they are in print order, so the list on screen is the list that would be
     * printed.
     */
    @Test
    @Transactional
    public void anOpenIssueResolvesItsMembersLive() {
        PublicationSeries s = series();
        PublicationIssue i = lifecycle.create(s, OPENS, IntervalBoundSource.STAMPED, user());
        Message m = message("NM-001");
        em.flush();

        List<IssueMemberVo> rows = memberList.members(i);

        assertEquals(1, rows.size(),
                "an open issue answered " + rows.size() + " members; an empty list here is "
                        + "indistinguishable from an issue that selects nothing");
        IssueMemberVo row = rows.get(0);
        assertEquals(m.getUid(), row.getMessageUid());
        assertEquals("NM-001", row.getFrozenShortId(), "the live row carries no short id");
        assertEquals(Status.PUBLISHED.name(), row.getFrozenStatus());
        assertEquals(MemberSource.CRITERIA.name(), row.getSource());
        assertEquals("IN_INTERVAL", row.getReasonCode());
        assertEquals(0, row.getSortIndex(), "sortIndex is dense and 0-based over the whole union");
        assertNull(row.getDrift(),
                "a live list has no frozen value for anything to have drifted from");
        assertNull(row.getCuration(), "nobody curated this row");

        // And nothing was written: the list is a probe, not a freeze.
        assertEquals(0L, em.createQuery("SELECT COUNT(x) FROM IssueMember x WHERE x.issue = :i", Long.class)
                .setParameter("i", i).getSingleResult(),
                "the live list froze member rows; it must write nothing at all");
    }

    /**
     * A curated row says who put it there, and an exclusion takes it out.
     *
     * Curation is only legal while an issue is OPEN, so the live list is the ONLY
     * list on which a curation decision can be seen taking effect at all.
     */
    @Test
    @Transactional
    public void curationIsVisibleOnTheLiveList() {
        PublicationSeries s = series();
        PublicationIssue i = lifecycle.create(s, OPENS, IntervalBoundSource.STAMPED, user());
        Message selected = message("NM-001");
        em.flush();

        curation.exclude(i, selected.getUid(), user(), "superseded before release");
        em.flush();
        assertTrue(memberList.members(i).isEmpty(), "an excluded message is still a member");

        // An INCLUDE of something the criteria do NOT select: a different message
        // series, which this issue's query has no way to reach.
        MessageSeries other = new MessageSeries();
        other.setSeriesId("ms-" + UUID.randomUUID().toString().substring(0, 8));
        other.setMainType(MainType.NM);
        em.persist(other);
        Message named = new Message();
        named.setUid(UUID.randomUUID().toString());
        named.setMessageSeries(other);
        named.setShortId("NM-999");
        named.setMainType(MainType.NM);
        named.setType(Type.TEMPORARY_NOTICE);
        named.setStatus(Status.PUBLISHED);
        named.setPublishDateFrom(new Date(OPENS.getTime() + 3600_000L));
        em.persist(named);
        em.flush();

        curation.include(i, named.getUid(), user(), "named by hand for this edition");
        em.flush();

        List<IssueMemberVo> rows = memberList.members(i);
        assertEquals(1, rows.size());
        assertEquals(named.getUid(), rows.get(0).getMessageUid());
        assertEquals(MemberSource.OVERRIDE_INCLUDE.name(), rows.get(0).getSource());
        assertEquals("MANUAL_INCLUDE", rows.get(0).getReasonCode());
        assertNotNull(rows.get(0).getCuration(), "a curated row does not say a human decided it");
        assertEquals("named by hand for this edition", rows.get(0).getCuration().getReason());
    }

    /**
     * O-4 is decided against the live resolution, because there is nothing else.
     *
     * Member rows are written by the freeze and curation is only legal while the
     * issue is OPEN, so the two conditions never held at once: the rule counted
     * rows, counted zero every time, and read as enforced while enforcing nothing.
     */
    @Test
    @Transactional
    public void includingAMessageTheCriteriaAlreadySelectIsRefused() {
        PublicationSeries s = series();
        PublicationIssue i = lifecycle.create(s, OPENS, IntervalBoundSource.STAMPED, user());
        Message selected = message("NM-001");
        em.flush();

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> curation.include(i, selected.getUid(), user(), "adding it again"));
        assertEquals("OVERRIDE_ALREADY_A_MEMBER", e.code());

        assertEquals(0L, em.createQuery(
                                "SELECT COUNT(o) FROM IssueOverride o WHERE o.issue = :i", Long.class)
                        .setParameter("i", i).getSingleResult(),
                "the refused override was written anyway");
    }
}
