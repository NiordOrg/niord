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
import org.hibernate.Session;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.message.Message;
import org.niord.core.message.MessageHistory;
import org.niord.core.message.MessageSeries;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.TestIds;
import org.niord.core.publication.series.IssueResolutionService.IssueResolution;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.criteria.MessageSeriesCriterionVo;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.series.vo.IssueMemberVo;
import org.niord.core.publication.series.vo.IssueOmissionsVo;
import org.niord.core.publication.series.vo.IssueOverrideVo;
import org.niord.core.publication.series.vo.IssuePreviewVo;
import org.niord.core.publication.series.vo.IssueWorkbenchVo;
import org.niord.core.publication.series.vo.PublishCheckRowVo;
import org.niord.core.publication.series.vo.PublishChecklistVo;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.core.user.User;
import org.niord.model.message.MainType;
import org.niord.model.message.Status;
import org.niord.model.message.Type;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The issue screen answered once, and the parts still agreeing.
 *
 * The screen used to make six requests, three of which each took their own
 * resolve of the same issue over the same interval -- about a second each on an
 * in-force series, whose candidate set is the series' whole published history.
 * They now share one resolution.
 *
 * What is asserted here is therefore not "the workbench returns something". It is
 * that sharing changed no answer: the rail computed from a shared resolution is
 * the rail computed from its own, the member list is the member list, and the
 * standing decisions come back in the order the endpoint has always sent them.
 * A performance change that quietly moved one of those would be invisible on
 * every screen until somebody compared two of them.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class IssueWorkbenchTest {

    @Inject
    IssueWorkbenchService workbench;

    @Inject
    IssueResolutionService resolutions;

    @Inject
    IssueMemberListService memberList;

    @Inject
    PublishChecklistService checklist;

    @Inject
    IssueCurationService curation;

    @Inject
    IssueLifecycleService lifecycle;

    @Inject
    MemberResolutionService resolver;

    @Inject
    IssuePreviewService previews;

    @Inject
    EntityManager em;

    private static final Date OPENS = new Date(1_699_000_000_000L);

    private static final long HOUR = 3600_000L;

    private static final long DAY = 24 * HOUR;

    // ------------------------------------------------------------------ fixtures

    private String messageSeriesId;

    private PublicationSeries series() {
        PublicationCategory c = new PublicationCategory();
        c.setCategoryId(TestIds.category());
        c.setPriority(100);
        em.persist(c);

        // Its own message series, so the rest of the corpus cannot decide what this
        // issue contains -- or how much it left out.
        messageSeriesId = TestIds.id("ms-");
        MessageSeries ms = new MessageSeries();
        ms.setSeriesId(messageSeriesId);
        ms.setMainType(MainType.NM);
        em.persist(ms);

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
        node.setValues(new ArrayList<>(List.of(messageSeriesId)));
        doc.getCriteria().add(node);
        s.setCriteria(doc);

        s.createDesc("da").setName("Test series");
        em.persist(s);
        return s;
    }

    private Message message(String shortId, Status status) {
        return message(shortId, status, new Date(OPENS.getTime() + HOUR));
    }

    /** The same message, published at an instant the case cares about. */
    private Message message(String shortId, Status status, Date publishedAt) {
        MessageSeries ms = em.createQuery(
                        "SELECT ms FROM MessageSeries ms WHERE ms.seriesId = :id", MessageSeries.class)
                .setParameter("id", messageSeriesId).getSingleResult();

        Message m = new Message();
        m.setUid(UUID.randomUUID().toString());
        m.setMessageSeries(ms);
        m.setShortId(shortId);
        m.setMainType(MainType.NM);
        m.setType(Type.TEMPORARY_NOTICE);
        m.setStatus(status);
        m.setPublishDateFrom(publishedAt);
        em.persist(m);
        return m;
    }

    /** One history row, which is where the instant of a withdrawal is read from. */
    private void history(Message m, Status status, Date at) {
        MessageHistory h = new MessageHistory();
        h.setMessage(m);
        h.setStatus(status);
        h.setCreated(at);
        h.setVersion(m.getHistory().size() + 1);
        em.persist(h);
        m.getHistory().add(h);
    }

    private User user() {
        User u = new User();
        u.setUsername(TestIds.user());
        em.persist(u);
        return u;
    }

    // -------------------------------------------------------------------- cases

    /**
     * The three readers of one resolution agree, which is the point of sharing it.
     *
     * NOT that the two forms of compute() agree -- one delegates to the other, so
     * that comparison cannot fail and guards nothing. What sharing actually buys
     * is that the rail's member count, the length of the list beside it and the
     * omissions panel under it are all the SAME resolve: before this they were
     * three, taken milliseconds apart, and the rail could say 214 members beside a
     * list of 213 with nothing to say which one the publish would honour.
     *
     * At ONE instant on purpose. Half of what is compared is an answer about a
     * point in time, so two reads a few milliseconds apart are not the same
     * question.
     */
    @Test
    @Transactional
    public void theThreeReadersOfOneResolutionAgree() {
        PublicationSeries s = series();
        PublicationIssue i = lifecycle.create(s, OPENS, IntervalBoundSource.STAMPED, user());
        message("NM-001", Status.PUBLISHED);
        message("NM-002", Status.PUBLISHED);
        // A candidate the query selects and the predicate then drops, so the
        // omissions half of the comparison is not asserted over an empty list.
        message("NM-D01", Status.DRAFT);
        em.flush();

        IssueResolution pre = resolutions.forIssue(i, new Date());
        PublishChecklistService.Checklist rail = checklist.compute(i, false, false, pre);

        assertEquals(2, rail.resolution().members().size(),
                "the shared resolution did not reach the rail's member count");
        assertEquals(rail.resolution().members().size(), memberList.members(i, "da", pre).size(),
                "the rail counted a different number of members than the list beside it, off the same "
                        + "resolution -- which is the disagreement sharing one resolve exists to close");

        IssueOmissionsVo omissions = IssueOmissionsVo.of(pre.resolution().misses());
        assertEquals(pre.resolution().misses().size(), omissions.getMissCount(),
                "the omissions panel reported a different total than the resolution it was built from");
        assertTrue(omissions.getMissCount() > 0,
                "the fixture dropped a candidate and the resolution recorded no miss; the agreement "
                        + "above would then hold over nothing");
        assertSame(pre.resolution(), rail.resolution(),
                "the rail took its own resolve instead of the one it was handed");
    }

    /**
     * Listing the standing decisions takes NO resolve.
     *
     * The decisions are rows and answering them needs the override query alone.
     * Reading them off a shared resolution would be tidy and would make
     * GET /overrides pay a full candidate narrowing -- about a second on an
     * in-force series -- for a list nobody resolved anything for, in a change
     * whose whole purpose is fewer resolves per screen.
     *
     * Counted rather than described, and with the resolving read measured beside
     * it so the counter is shown to be sensitive: a statistics handle that
     * recorded nothing would otherwise let this pass vacuously.
     */
    @Test
    @Transactional
    public void listingTheStandingDecisionsTakesNoResolve() {
        PublicationSeries s = series();
        PublicationIssue i = lifecycle.create(s, OPENS, IntervalBoundSource.STAMPED, user());
        Message dropped = message("NM-002", Status.PUBLISHED);
        message("NM-001", Status.PUBLISHED);
        em.flush();
        curation.exclude(i, dropped.getUid(), user(), "held over to the next edition");
        em.flush();

        Statistics stats = em.unwrap(Session.class).getSessionFactory().getStatistics();
        boolean wasEnabled = stats.isStatisticsEnabled();
        stats.setStatisticsEnabled(true);
        try {
            long before = stats.getQueryExecutionCount();
            List<IssueOverrideVo> decisions = memberList.standingDecisions(i);
            long listing = stats.getQueryExecutionCount() - before;

            long mid = stats.getQueryExecutionCount();
            resolutions.forIssue(i, new Date());
            long resolving = stats.getQueryExecutionCount() - mid;

            assertEquals(1, decisions.size(), "the fixture's exclusion is not being listed at all");
            assertEquals(1L, listing,
                    "listing the standing decisions ran " + listing + " queries. It is ONE read of the "
                            + "override rows; anything more means it is resolving the issue's membership "
                            + "to render a list that shows none of it");
            assertTrue(resolving > listing,
                    "the resolving read cost no more than the listing one (" + resolving + " vs "
                            + listing + "), so the counter is not measuring what this test claims");
        } finally {
            stats.setStatisticsEnabled(wasEnabled);
        }
    }

    /**
     * The standalone rail does not resolve a frozen issue either.
     *
     * compute(issue, cutoff, ...) reaches the same short-circuit the screen does,
     * so asking a PUBLISHED issue for its rail no longer runs a live resolve over
     * the series' whole history to describe a release that already happened. The
     * consequence is a CHANGED ANSWER and is pinned here rather than left to be
     * discovered: MEMBERS_RESOLVED reports that the resolver did not run. It is a
     * WARN and canPublish was already false through ISSUE_OPEN, and no shipped
     * caller asks a frozen issue for a rail -- but the endpoint's answer moved.
     */
    @Test
    @Transactional
    public void theStandaloneRailDoesNotResolveAFrozenIssue() {
        PublicationSeries s = series();
        PublicationIssue i = lifecycle.create(s, OPENS, IntervalBoundSource.STAMPED, user());
        message("NM-001", Status.PUBLISHED);
        em.flush();

        i.setStatus(IssueStatus.PUBLISHED);
        i.setCutoffStampedAt(new Date());
        em.merge(i);
        em.flush();

        PublishChecklistService.Checklist rail = checklist.compute(i, new Date(), false, false);
        assertNull(rail.resolution(),
                "the standalone rail resolved a published issue live; the archived document is the "
                        + "record, and this pays a full narrowing on every published issue anybody opens");
        assertFalse(rowOf(rail, "MEMBERS_RESOLVED").isPassed(),
                "the rail claimed the resolver ran on a frozen issue");
        assertEquals(0, memberCountOf(rail),
                "MEMBER_LIMIT counted members on an issue that was never resolved");
        assertFalse(rail.canPublish(),
                "a published issue was offered a release; ISSUE_OPEN blocks it either way");
    }

    /**
     * A curated issue with NO criteria document reports its includes on the rail.
     *
     * The corner where the rail and the member list used to disagree. A
     * query-backed series whose effective document is null -- the annexes, where
     * the contents are named by hand because no query can select one live message
     * and not the other -- fell through to the curated branch in the member list
     * and to nothing at all on the rail, which then reported MEMBERS_RESOLVED as a
     * warning beside a list showing the member. One resolve means one answer, and
     * this is the answer.
     */
    @Test
    @Transactional
    public void aQueryBackedSeriesWithNoDocumentReportsItsIncludesOnBothFormsOfTheRail() {
        PublicationSeries s = series();
        // GENERATED_FROM_QUERY with a timeRelation and NO document: the shape the
        // annex series are in, and the one both readers had to guess about.
        s.setCriteria(null);
        em.merge(s);
        PublicationIssue i = lifecycle.create(s, OPENS, IntervalBoundSource.STAMPED, user());
        Message named = message("NM-001", Status.PUBLISHED);
        em.flush();
        curation.include(i, named.getUid(), user(), "the annex names its contents by hand");
        em.flush();

        Date at = new Date();
        PublishChecklistService.Checklist alone = checklist.compute(i, at, false, false);
        PublishChecklistService.Checklist shared =
                checklist.compute(i, false, false, resolutions.forIssue(i, at));

        for (PublishChecklistService.Checklist rail : List.of(alone, shared)) {
            assertNotNull(rail.resolution(),
                    "a curated issue with no criteria document resolved to nothing; its INCLUDE is "
                            + "then an audited decision the publish would discard");
            assertEquals(1, rail.resolution().members().size(),
                    "the named member did not reach the rail");
            assertEquals(1, memberCountOf(rail),
                    "MEMBER_LIMIT counted a different member set than the resolution beside it");
            assertTrue(rowOf(rail, "MEMBERS_RESOLVED").isPassed(),
                    "the rail warned that the resolver did not run, on an issue whose contents "
                            + "somebody named");
        }
        assertEquals(1, memberList.members(i, "da").size(),
                "the member list and the rail disagree again about a hand-curated issue");
    }

    /**
     * A frozen issue takes NO resolve, and the screen says so by omission.
     *
     * Its member rows are what was printed and the archived document is the proof.
     * Resolving it live would cost the full narrowing on every published issue
     * anybody opens, to produce an authoritative-looking answer about a document
     * nobody published -- and the rail and the omissions would both be describing
     * a release that has already happened.
     */
    @Test
    @Transactional
    public void aFrozenIssueIsNotResolvedAndCarriesNoRailAndNoOmissions() {
        PublicationSeries s = series();
        PublicationIssue i = lifecycle.create(s, OPENS, IntervalBoundSource.STAMPED, user());
        message("NM-001", Status.PUBLISHED);
        em.flush();

        i.setStatus(IssueStatus.PUBLISHED);
        i.setCutoffStampedAt(new Date());
        em.merge(i);
        em.flush();

        IssueResolution pre = resolutions.forIssue(i, new Date());
        assertTrue(pre.frozen(), "a PUBLISHED issue did not read as frozen");
        assertNull(pre.resolution(),
                "a frozen issue was resolved live. That is the cost this exists to avoid, and the "
                        + "answer describes a document nobody published");

        IssueWorkbenchVo vo = workbench.forIssue(i, "da", true);
        assertNull(vo.getChecklist(), "a published issue was offered a release rail");
        assertNull(vo.getOmissions(),
                "a published issue reported what today's corpus would leave out of it");
        assertNotNull(vo.getIssue());
        assertNotNull(vo.getAudit());
        assertNotNull(vo.getOverrides(),
                "the standing decisions are dropped on a frozen issue; an imported one carries them");
    }

    /**
     * A curator gets the screen; only an admin gets the rail.
     *
     * The endpoint's gate is the curator tier because four of the five parts are
     * curator reads. Narrowing the rail with a second annotation would refuse the
     * whole response, which is what happened before: the checklist's 403 had no
     * branch of its own on the client and blanked the entire screen for a curator
     * who is not an admin.
     */
    @Test
    @Transactional
    public void theRailIsTheOnlyPartAnAdminSeesAndACuratorDoesNot() {
        PublicationSeries s = series();
        PublicationIssue i = lifecycle.create(s, OPENS, IntervalBoundSource.STAMPED, user());
        message("NM-001", Status.PUBLISHED);
        em.flush();

        IssueWorkbenchVo asCurator = workbench.forIssue(i, "da", false);
        assertNull(asCurator.getChecklist(), "a curator who is not an admin was handed the release rail");
        assertEquals(1, asCurator.getMembers().size(), "a curator was refused the member list");
        assertNotNull(asCurator.getAudit());
        assertNotNull(asCurator.getOmissions(),
                "a curator was refused the panel saying what the criteria left out");

        IssueWorkbenchVo asAdmin = workbench.forIssue(i, "da", true);
        assertNotNull(asAdmin.getChecklist(), "an admin was not given the release rail");
        assertEquals(PublishChecklistService.CODES.size(), asAdmin.getChecklist().getRows().size(),
                "the rail shipped a subset of its rows; a client renders what it receives and reads a "
                        + "missing row as a check that does not exist");
    }

    /**
     * Every part of the workbench is the part its own endpoint returns.
     *
     * The five endpoints stay -- other consumers read them -- so the day one of
     * them answers something this envelope does not is the day the screen and the
     * API disagree with nothing to say which is right.
     */
    @Test
    @Transactional
    public void eachPartMatchesTheEndpointItComposes() {
        PublicationSeries s = series();
        PublicationIssue i = lifecycle.create(s, OPENS, IntervalBoundSource.STAMPED, user());
        Message included = message("NM-001", Status.PUBLISHED);
        Message dropped = message("NM-002", Status.PUBLISHED);
        em.flush();
        curation.exclude(i, dropped.getUid(), user(), "held over to the next edition");
        em.flush();

        IssueWorkbenchVo vo = workbench.forIssue(i, "da", true);

        List<String> composed = vo.getMembers().stream().map(IssueMemberVo::getMessageUid).toList();
        List<String> direct = memberList.members(i, "da").stream()
                .map(IssueMemberVo::getMessageUid).toList();
        assertEquals(direct, composed, "the workbench's member list is not the member endpoint's");
        assertEquals(List.of(included.getUid()), composed);

        List<String> composedDecisions = vo.getOverrides().stream()
                .map(IssueOverrideVo::getMessageUid).toList();
        List<String> directDecisions = memberList.standingDecisions(i).stream()
                .map(IssueOverrideVo::getMessageUid).toList();
        assertEquals(directDecisions, composedDecisions,
                "the standing decisions came back in a different order than the endpoint sends them; "
                        + "the shared read must keep the id ordering the list has always had");
        assertEquals(1, composedDecisions.size());
        assertNotNull(vo.getOverrides().get(0).getAuthor(),
                "the standing decision lost its author, which is half of a why-line");
    }

    /**
     * Every omission row carries the short id an editor recognises, and the whole
     * sample costs ONE query.
     *
     * The rows are a work list: somebody reads them and decides whether each
     * message belongs in the issue after all. A uid is a UUID, and a panel that
     * lists nothing else is one an editor cannot act on -- they would have to look
     * every row up somewhere else to find out what it is. The short id is display
     * text and nothing more, which is why the resolution does not carry it: it is
     * not unique, nothing prevents reuse, and a membership decision keyed on it
     * could treat two different messages as one. So it is filled in afterwards,
     * over the CAPPED sample and in one query -- a wide document drops thousands
     * of candidates, and a lookup per row would put a thousand queries on a path
     * the criteria editor fires while somebody is still typing.
     *
     * The query is COUNTED rather than described, with the resolving read measured
     * beside it so the counter is shown to be sensitive to what it claims to
     * measure.
     */
    @Test
    @Transactional
    public void everyOmissionRowCarriesItsShortIdAndTheFillIsOneQuery() {
        PublicationSeries s = series();
        PublicationIssue i = lifecycle.create(s, OPENS, IntervalBoundSource.STAMPED, user());
        message("NM-001", Status.PUBLISHED);
        // Two candidates the query selects and the predicate then drops: one that
        // has been given its number, and one that has not. The second is why the
        // field is nullable -- a draft has no short id at all, and a fill that
        // assumed one would either omit the row or invent an id for it.
        Message numbered = message("NM-815-26", Status.DRAFT);
        Message unnumbered = message(null, Status.DRAFT);
        em.flush();

        IssueResolution resolved = resolutions.forIssue(i, new Date());
        assertEquals(2, resolved.resolution().misses().size(),
                "the fixture did not drop the two candidates this test is about");

        Statistics stats = em.unwrap(Session.class).getSessionFactory().getStatistics();
        boolean wasEnabled = stats.isStatisticsEnabled();
        stats.setStatisticsEnabled(true);
        IssueOmissionsVo omissions;
        try {
            long before = stats.getQueryExecutionCount();
            omissions = resolver.omissions(resolved.resolution().misses());
            long filling = stats.getQueryExecutionCount() - before;

            assertEquals(1L, filling,
                    "filling the omission rows' short ids ran " + filling + " queries. It is ONE read "
                            + "over the capped uids; anything more is a lookup per row, and the sample "
                            + "is fifty rows on a path that fires while somebody is typing");
        } finally {
            stats.setStatisticsEnabled(wasEnabled);
        }

        assertEquals(2, omissions.getMissCount());
        assertEquals("NM-815-26", messageIdOf(omissions, numbered.getUid()),
                "the omission row for a numbered message came back without the id an editor reads");
        assertNull(messageIdOf(omissions, unnumbered.getUid()),
                "a message with no short id was given one; the uid is the identity and the short id "
                        + "is display text that may genuinely be absent");

        // And the screen serves the same rows, so the panel is not a second shape.
        IssueWorkbenchVo vo = workbench.forIssue(i, "da", true);
        assertEquals("NM-815-26", messageIdOf(vo.getOmissions(), numbered.getUid()),
                "the workbench's omissions panel serves rows with no short id on them");
    }

    /** The short id on the row for one uid, or null. */
    private static String messageIdOf(IssueOmissionsVo omissions, String uid) {
        return omissions.getMisses().stream()
                .filter(m -> uid.equals(m.messageUid()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no omission row for " + uid))
                .messageId();
    }

    /**
     * The omissions are a SAMPLE, and the count beside them is not.
     *
     * Every candidate the predicate rejected produces one of these, so a wide
     * document over a live corpus yields thousands. Fifty is enough to see the
     * shape of what is being dropped; a panel that reported fifty as the answer
     * would understate an over-narrow criteria document by orders of magnitude.
     */
    @Test
    @Transactional
    public void theOmissionSampleIsCappedAndTheCountIsNot() {
        PublicationSeries s = series();
        PublicationIssue i = lifecycle.create(s, OPENS, IntervalBoundSource.STAMPED, user());
        message("NM-001", Status.PUBLISHED);
        // Candidates the query selects and the predicate then drops: status is a
        // resolver invariant and is deliberately not narrowed in SQL.
        int dropped = IssueOmissionsVo.PROBE_SAMPLE + 5;
        for (int n = 0; n < dropped; n++) {
            message("NM-D" + n, Status.DRAFT);
        }
        em.flush();

        IssueWorkbenchVo vo = workbench.forIssue(i, "da", true);
        assertNotNull(vo.getOmissions());
        assertEquals(dropped, vo.getOmissions().getMissCount(),
                "missCount reported the sample size rather than how much was left out");
        assertEquals(IssueOmissionsVo.PROBE_SAMPLE, vo.getOmissions().getMisses().size(),
                "the omissions panel shipped every miss; it is a sample, not a page");
        assertEquals(1, vo.getMembers().size(), "the dropped candidates became members");
    }

    /**
     * An issue nobody has previewed reports an empty list, not an absent one.
     *
     * The screen seeds its preview rows from every read of this envelope, so the
     * two states have to be told apart on the wire: "none stored" is an answer
     * and it is what lets the screen stop offering to open something that is not
     * there. A null here would be read as "unknown" by a client that has no
     * separate way to ask.
     */
    @Test
    @Transactional
    public void anOpenIssueWithNothingPreviewedReportsAnEmptyList() {
        PublicationSeries s = series();
        PublicationIssue i = lifecycle.create(s, OPENS, IntervalBoundSource.STAMPED, user());
        message("NM-001", Status.PUBLISHED);
        em.flush();

        IssueWorkbenchVo vo = workbench.forIssue(i, "da", true);
        assertNotNull(vo.getPreviews(), "the previews list is absent rather than empty");
        assertTrue(vo.getPreviews().isEmpty(),
                "an issue nobody has previewed reported " + vo.getPreviews().size() + " previews");
    }

    /**
     * A reader who may not render or open a preview is not handed one to open.
     *
     * The preview endpoints answer only to an admin, so a row on the workbench of
     * anybody else would be an offer the server refuses a moment later. Narrowed
     * the way the checklist is, and off the same flag.
     */
    @Test
    @Transactional
    public void aStoredPreviewIsNotReportedToAReaderWhoMayNotOpenIt() throws Exception {
        PublicationSeries s = series();
        PublicationIssue i = lifecycle.create(s, OPENS, IntervalBoundSource.STAMPED, user());
        message("NM-001", Status.PUBLISHED);
        em.flush();

        previews.record(i, "da", "test.pdf", "bytes".getBytes(StandardCharsets.UTF_8));

        IssueWorkbenchVo asCurator = workbench.forIssue(i, "da", false);
        assertNotNull(asCurator.getPreviews(), "the previews list is absent rather than empty");
        assertTrue(asCurator.getPreviews().isEmpty(),
                "a reader without the admin role was handed " + asCurator.getPreviews().size()
                        + " preview rows whose download the server refuses");

        IssueWorkbenchVo asAdmin = workbench.forIssue(i, "da", true);
        assertEquals(1, asAdmin.getPreviews().size(), "the admin no longer sees the stored preview");
    }

    /**
     * A stored preview reaches the screen, and moving the member set makes it say
     * so.
     *
     * The row is what the screen offers to open, and its staleness is the same
     * question the rail answers -- computed here off the issue's stamp rather
     * than remembered from the moment the preview was generated. A curation
     * moves that stamp, and the row has to follow: a screen still badging the
     * preview as current after somebody has excluded a message would have an
     * admin release against a document they read before the change.
     */
    @Test
    @Transactional
    public void aStoredPreviewIsReportedAndGoesStaleWhenTheMemberSetMoves() throws Exception {
        PublicationSeries s = series();
        PublicationIssue i = lifecycle.create(s, OPENS, IntervalBoundSource.STAMPED, user());
        message("NM-001", Status.PUBLISHED);
        Message dropped = message("NM-002", Status.PUBLISHED);
        em.flush();

        previews.record(i, "da", "test.pdf", "bytes".getBytes(StandardCharsets.UTF_8));

        IssueWorkbenchVo before = workbench.forIssue(i, "da", true);
        List<IssuePreviewVo> fresh = before.getPreviews();
        assertEquals(1, fresh.size(),
                "one language has a stored preview and the screen reported " + fresh.size() + " rows");
        assertEquals("da", fresh.get(0).lang());
        assertTrue(fresh.get(0).renderedAt() > 0,
                "the preview row came back without the instant it was rendered at");
        assertFalse(fresh.get(0).stale(),
                "a preview generated after the last change to the issue was reported stale");
        assertTrue(previewFreshRowOf(before).isPassed(),
                "the rail warned that the preview is stale beside a row reporting it current; the "
                        + "badge and the rail answer the same question and must not disagree");

        // The stamp and the generation are both milliseconds, so a curation taken
        // inside the same one would be indistinguishable from no change at all.
        Thread.sleep(5);
        curation.exclude(i, dropped.getUid(), user(), "held over to the next edition");
        em.flush();

        IssueWorkbenchVo moved = workbench.forIssue(i, "da", true);
        List<IssuePreviewVo> after = moved.getPreviews();
        assertEquals(1, after.size(), "the stored preview vanished when the member set moved");
        assertTrue(after.get(0).stale(),
                "the member set moved after the preview was rendered and the row still claims to be "
                        + "current");
        assertFalse(previewFreshRowOf(moved).isPassed(),
                "the row went stale and the rail beside it still reports the preview as current");
    }

    /** The PREVIEW_FRESH row off a workbench response, so the badge and the rail are compared. */
    private static PublishCheckRowVo previewFreshRowOf(IssueWorkbenchVo vo) {
        return vo.getChecklist().getRows().stream()
                .filter(r -> "PREVIEW_FRESH".equals(r.getCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the rail has no PREVIEW_FRESH row at all"));
    }

    /**
     * A frozen issue carries no preview rows, stored ones included.
     *
     * A preview is a preview of a live member list, and a published issue has
     * none: what it printed is the archived document. The generations rendered
     * while it was open outlive the release by up to the sweep's TTL, so this is
     * not hypothetical -- an issue published an hour ago still has files on disk,
     * and offering them beside the released document invites a reader to compare
     * an official notice set against a draft of it.
     */
    @Test
    @Transactional
    public void aFrozenIssueCarriesNoPreviewRowsEvenWithOneStored() {
        PublicationSeries s = series();
        PublicationIssue i = lifecycle.create(s, OPENS, IntervalBoundSource.STAMPED, user());
        message("NM-001", Status.PUBLISHED);
        em.flush();

        previews.record(i, "da", "test.pdf", "bytes".getBytes(StandardCharsets.UTF_8));
        assertEquals(1, workbench.forIssue(i, "da", true).getPreviews().size(),
                "the fixture stored no preview, so freezing it below would prove nothing");

        i.setStatus(IssueStatus.PUBLISHED);
        i.setCutoffStampedAt(new Date());
        em.merge(i);
        em.flush();

        IssueWorkbenchVo vo = workbench.forIssue(i, "da", true);
        assertNotNull(vo.getPreviews(), "the previews list is absent rather than empty");
        assertTrue(vo.getPreviews().isEmpty(),
                "a published issue offered the previews rendered while it was still open");
    }

    // ------------------------------------------------------- the view instant

    /**
     * The whole screen moves to the instant it was asked for, and the list of
     * what separates the two instants reads the same in both.
     *
     * An open issue whose planned cut-off has passed has two honest answers, and
     * they differ by exactly the messages published in between. Read as of now it
     * holds them; read as of the planned cut-off it does not -- and the rail
     * beside the list has to be the rail for THAT instant, or the screen offers a
     * choice between two views while only one of the panels moves.
     *
     * `afterPlannedCutoff` is what makes the choice legible, and here it does not
     * move -- not because it is pinned to the stored plan whatever is asked, but
     * because both views are ABOUT the same cut-off: the planned view names the
     * stored plan as its instant, and the default view has no other cut-off in
     * play and falls back to that same plan. So the rows are the same rows, read
     * once as "what publishing now would add" and once as "what publishing then
     * would leave out". It is `lateAfter` that says which cut-off was measured
     * against, and it agrees across the two.
     */
    @Test
    @Transactional
    public void theViewInstantMovesTheScreenAndBothViewsMeasureAgainstTheSameCutoff() {
        PublicationSeries s = series();
        Date planned = new Date(OPENS.getTime() + 7 * DAY);
        PublicationIssue i = lifecycle.create(s, OPENS, IntervalBoundSource.STAMPED, planned, user());
        message("NM-001", Status.PUBLISHED);
        message("NM-002", Status.PUBLISHED);
        Message late = message("NM-003", Status.PUBLISHED, new Date(OPENS.getTime() + 14 * DAY));
        em.flush();

        IssueWorkbenchVo asOfNow = workbench.forIssue(i, "da", true);
        assertEquals(3, asOfNow.getMembers().size(),
                "the default view is as of now, and now the period has three messages in it");
        assertEquals(List.of(late.getUid()), uidsOf(asOfNow.getAfterPlannedCutoff()),
                "the message published after the planned cut-off is not named as such, so the screen "
                        + "cannot tell an admin what publishing now would add beyond the period");
        assertNotNull(asOfNow.getLateAfter(),
                "the default read named no cut-off for the late list beside it, so the count on the "
                        + "screen has no date the reader can check it against");
        assertEquals(planned.getTime(), asOfNow.getLateAfter().longValue(),
                "with no other cut-off in play the late list is measured against the stored plan");
        assertTrue(Math.abs(asOfNow.getViewedAt() - System.currentTimeMillis()) < 60_000,
                "the default read reported a viewed instant that is not now: " + asOfNow.getViewedAt());

        IssueWorkbenchVo asOfPlanned = workbench.forIssue(i, "da", true, planned);
        assertEquals(planned.getTime(), asOfPlanned.getViewedAt(),
                "the instant the caller asked for was not the one the screen answered at");
        assertEquals(2, asOfPlanned.getMembers().size(),
                "read as of the planned cut-off the issue still held the message published a week "
                        + "after it");
        assertFalse(uidsOf(asOfPlanned.getMembers()).contains(late.getUid()));
        assertEquals(List.of(late.getUid()), uidsOf(asOfPlanned.getAfterPlannedCutoff()),
                "the late list emptied itself in the planned view. Read AT the planned cut-off "
                        + "nothing published after it is a member, so a list answered from that view "
                        + "could only ever be empty -- it is taken from the as-of-now membership in "
                        + "both views, and it is the DIFFERENCE between them");
        assertEquals(asOfNow.getLateAfter(), asOfPlanned.getLateAfter(),
                "the two views measured the late list against different cut-offs, and the planned "
                        + "view names exactly the stored plan the default view falls back to");

        // The rail moved with the list, which is the half a client cannot check.
        // Both places it states a count: the envelope number the publish dialog
        // prints as its headline, and the MEMBER_LIMIT row an admin reads beside
        // the list -- compared against each other inside the helper.
        assertEquals(2, memberCountOf(asOfPlanned.getChecklist()),
                "the rail counted the as-of-now members beside a list showing the planned ones");
        assertEquals(3, memberCountOf(asOfNow.getChecklist()));

        String stamp = ZonedDateTime.ofInstant(planned.toInstant(), s.cutoffZone())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        assertTrue(rowOf(asOfPlanned.getChecklist(), "CUTOFF_NOT_FUTURE").getDetail().contains(stamp),
                "the rail's cut-off row still names now rather than the instant the screen is "
                        + "answering for: "
                        + rowOf(asOfPlanned.getChecklist(), "CUTOFF_NOT_FUTURE").getDetail());
        assertFalse(rowOf(asOfNow.getChecklist(), "CUTOFF_NOT_FUTURE").getDetail().contains(stamp),
                "the default read named the planned cut-off; it answers for now");

        // The omissions panel is answered off the same resolution, so it moves
        // with the rest rather than describing a third instant.
        assertNotNull(asOfPlanned.getOmissions(),
                "the planned view carries no omissions panel; it is an open issue and was resolved");
    }

    /**
     * The late list is measured against the cut-off THE READ IS ABOUT, and three
     * reads of one issue give three different answers.
     *
     * An open issue has two dates -- a period start and a planned cut-off -- with
     * "now" as the one alternative to the plan once the plan has passed. So the
     * question "what was published after the cut-off" has to be asked about the
     * cut-off in front of the reader: the stored plan when they are reading it,
     * the instant they named when they moved it, and now when now is what they
     * chose. Measured against a third instant instead, the count would answer
     * about a release nobody is looking at while sitting under the date they are.
     *
     * The three reads here are the three the screen makes, over one fixture whose
     * members are published on day 1, day 5 and day 9 of a period planned to close
     * on day 4. The counts have to differ, and each has to match the date reported
     * beside it -- which is what `lateAfter` is for: a bare number with no instant
     * attached cannot be checked against the form it stands next to.
     */
    @Test
    @Transactional
    public void theLateListIsMeasuredAgainstTheCutoffTheReadIsAbout() {
        PublicationSeries s = series();
        Date planned = new Date(OPENS.getTime() + 4 * DAY);
        PublicationIssue i = lifecycle.create(s, OPENS, IntervalBoundSource.STAMPED, planned, user());
        Message day1 = message("NM-001", Status.PUBLISHED, new Date(OPENS.getTime() + DAY));
        Message day5 = message("NM-005", Status.PUBLISHED, new Date(OPENS.getTime() + 5 * DAY));
        Message day9 = message("NM-009", Status.PUBLISHED, new Date(OPENS.getTime() + 9 * DAY));
        em.flush();

        // The plain read: no other cut-off is in play, so the plan on disk is the
        // one the question is about.
        IssueWorkbenchVo asStored = workbench.forIssue(i, "da", true);
        assertEquals(3, asStored.getMembers().size(),
                "the period is open and all three messages fall in it as of now; the counts below "
                        + "are filters over this list and would prove nothing if it were short");
        assertNotNull(asStored.getLateAfter(), "the plain read named no cut-off for its late list");
        assertEquals(planned.getTime(), asStored.getLateAfter().longValue(),
                "a read that named no instant measured the late list against something other than "
                        + "the stored plan");
        List<String> lateAsStored = uidsOf(asStored.getAfterPlannedCutoff());
        assertEquals(2, lateAsStored.size(),
                "the plan closes on day 4 and two messages were published after it; the screen "
                        + "reported " + lateAsStored.size());
        assertTrue(lateAsStored.contains(day5.getUid()) && lateAsStored.contains(day9.getUid()),
                "a message published after the stored plan is missing from the list that names them");
        assertFalse(lateAsStored.contains(day1.getUid()),
                "a message published BEFORE the plan closed was named as late");

        // The plan moved to day 7 -- read in the planned view of a plan that says
        // day 7, or typed into the form on the way to saving it. One act, and the
        // numbers follow the date either way.
        Date day7 = new Date(OPENS.getTime() + 7 * DAY);
        IssueWorkbenchVo atDay7 = workbench.forIssue(i, "da", true, day7);
        assertNotNull(atDay7.getLateAfter(), "the read at a named instant named no cut-off");
        assertEquals(day7.getTime(), atDay7.getLateAfter().longValue(),
                "the screen went on measuring against the stored plan while the reader was looking "
                        + "at another cut-off, so the count and the date above it describe two "
                        + "different releases");
        assertEquals(List.of(day9.getUid()), uidsOf(atDay7.getAfterPlannedCutoff()),
                "measured against day 7 exactly one message is late; the message published on day 5 "
                        + "is inside that period and belongs in the release, not after it");
        assertEquals(2, atDay7.getMembers().size(),
                "the members moved to the named instant: day 1 and day 5 fall in (start, day 7]");
        assertFalse(uidsOf(atDay7.getMembers()).contains(day9.getUid()),
                "the message the same read names as late is also being counted as a member of the "
                        + "period it falls after");

        // And now, which is the other option the publish dialog offers: everything
        // is inside the period, so nothing is after it.
        Date nowish = new Date(System.currentTimeMillis() - HOUR);
        IssueWorkbenchVo atNow = workbench.forIssue(i, "da", true, nowish);
        assertNotNull(atNow.getLateAfter(), "the read as of now named no cut-off");
        assertEquals(nowish.getTime(), atNow.getLateAfter().longValue(),
                "the read as of now measured its late list against some other instant");
        assertTrue(atNow.getAfterPlannedCutoff().isEmpty(),
                "publishing now carries every message published so far, so nothing can fall after "
                        + "it -- and the screen named " + atNow.getAfterPlannedCutoff().size());
        assertEquals(3, atNow.getMembers().size(),
                "the as-of-now view lost members, so the emptiness above says nothing");
    }

    /**
     * A planned cut-off still ahead of us names nothing as late.
     *
     * Nothing can have been published after an instant that has not happened, so
     * the list is empty as a matter of fact rather than as a default -- and the
     * screen shows no choice of view, because there is nothing for the two views
     * to differ about.
     */
    @Test
    @Transactional
    public void aPlannedCutoffStillAheadOfUsNamesNothingAsLate() {
        PublicationSeries s = series();
        Date planned = new Date(System.currentTimeMillis() + 7 * DAY);
        PublicationIssue i = lifecycle.create(s, OPENS, IntervalBoundSource.STAMPED, planned, user());
        message("NM-001", Status.PUBLISHED);
        em.flush();

        IssueWorkbenchVo vo = workbench.forIssue(i, "da", true);
        assertEquals(1, vo.getMembers().size(),
                "the fixture resolved no members, so the emptiness below would prove nothing");
        assertNotNull(vo.getAfterPlannedCutoff(), "the late list is absent rather than empty");
        assertTrue(vo.getAfterPlannedCutoff().isEmpty(),
                "an issue whose period has not closed reported " + vo.getAfterPlannedCutoff().size()
                        + " messages published after it closed");
        assertNotNull(vo.getLateAfter(),
                "the cut-off the empty list was measured against went unreported. The issue HAS a "
                        + "planned cut-off; the list is empty because that cut-off has not arrived, "
                        + "which is a different statement from having none");
        assertEquals(planned.getTime(), vo.getLateAfter().longValue(),
                "the screen reported some other cut-off than the plan it fell back to");
    }

    /**
     * A frozen issue ignores the instant, and names nothing as late.
     *
     * What a published issue contains is what it printed. There is no second
     * instant to read it at, so one named here is neither honoured nor refused --
     * it is answered at now, and `viewedAt` says so rather than echoing back a
     * choice that decided nothing.
     */
    @Test
    @Transactional
    public void aFrozenIssueIgnoresTheInstantAndNamesNothingAsLate() {
        PublicationSeries s = series();
        Date planned = new Date(OPENS.getTime() + 7 * DAY);
        PublicationIssue i = lifecycle.create(s, OPENS, IntervalBoundSource.STAMPED, planned, user());
        message("NM-001", Status.PUBLISHED);
        message("NM-003", Status.PUBLISHED, new Date(OPENS.getTime() + 14 * DAY));
        em.flush();

        i.setStatus(IssueStatus.PUBLISHED);
        i.setCutoffStampedAt(new Date());
        em.merge(i);
        em.flush();

        IssueWorkbenchVo vo = workbench.forIssue(i, "da", true, planned);
        assertTrue(Math.abs(vo.getViewedAt() - System.currentTimeMillis()) < 60_000,
                "a published issue was answered as of an instant the caller chose; its contents are "
                        + "a record, and reading them at another instant describes a document nobody "
                        + "published");
        assertTrue(vo.getAfterPlannedCutoff().isEmpty(),
                "a published issue reported what today's corpus would add to it beyond its period");
        assertNull(vo.getLateAfter(),
                "a published issue named a cut-off for a late list it cannot have. Its contents are "
                        + "what it printed, so there is no cut-off left to choose between and nothing "
                        + "to be late for -- and a date here would head an empty list with a question "
                        + "that does not arise");
    }

    /**
     * An instant outside the window the issue can be read at is refused.
     *
     * Both bounds, and both as refusals rather than clamps. Before the period
     * opened is a window this issue does not cover; after now is a member set that
     * does not exist yet. Silently clamped, either would hand back a confident
     * answer under a heading naming the instant that was asked for -- which is the
     * one outcome a view instant must not produce.
     */
    @Test
    @Transactional
    public void anInstantOutsideTheIssuesWindowIsRefused() {
        PublicationSeries s = series();
        Date planned = new Date(OPENS.getTime() + 7 * DAY);
        PublicationIssue i = lifecycle.create(s, OPENS, IntervalBoundSource.STAMPED, planned, user());
        message("NM-001", Status.PUBLISHED);
        em.flush();

        IssueLifecycleService.TransitionRefusedException tooEarly = assertThrows(
                IssueLifecycleService.TransitionRefusedException.class,
                () -> workbench.forIssue(i, "da", true, new Date(OPENS.getTime() - DAY)),
                "an instant before the period opened was answered rather than refused");
        assertEquals("INVALID_INSTANT", tooEarly.code());

        IssueLifecycleService.TransitionRefusedException inTheFuture = assertThrows(
                IssueLifecycleService.TransitionRefusedException.class,
                () -> workbench.forIssue(i, "da", true,
                        new Date(System.currentTimeMillis() + DAY)),
                "an instant in the future was answered rather than refused");
        assertEquals("INVALID_INSTANT", inTheFuture.code());
    }

    /**
     * A message cancelled between the planned cut-off and now is a member at the
     * one instant and not at the other.
     *
     * The case that makes the choice of view a decision rather than a preference.
     * A cancel leaves an editor-set validity end alone, so the date alone still
     * says "alive"; the withdrawal instant is what decides, and it falls between
     * the two instants the screen can be read at. So the message belongs to the
     * issue as its period closed -- and the rail says so, with the one warning an
     * admin has to acknowledge -- and does not belong to it today.
     */
    @Test
    @Transactional
    public void amessageCancelledAfterThePlannedCutoffIsAMemberAtItAndNotNow() {
        PublicationSeries s = series();
        // The regime where liveness decides at all: a series that carries its
        // contents forward would keep a withdrawn message in either view.
        s.setAliveAtCutoff(true);
        em.merge(s);

        Date planned = new Date(OPENS.getTime() + 7 * DAY);
        PublicationIssue i = lifecycle.create(s, OPENS, IntervalBoundSource.STAMPED, planned, user());
        message("NM-001", Status.PUBLISHED);
        Message withdrawn = message("NM-002", Status.CANCELLED);
        history(withdrawn, Status.PUBLISHED, new Date(OPENS.getTime() + HOUR));
        history(withdrawn, Status.CANCELLED, new Date(planned.getTime() + 7 * DAY));
        em.flush();

        IssueWorkbenchVo asOfPlanned = workbench.forIssue(i, "da", true, planned);
        assertTrue(uidsOf(asOfPlanned.getMembers()).contains(withdrawn.getUid()),
                "a cancel a week after the period closed reached back into the issue that closed "
                        + "before it");
        assertFalse(rowOf(asOfPlanned.getChecklist(), "CANCELLED_MEMBERS_ALIVE_AT_CUTOFF").isPassed(),
                "the issue holds a withdrawn member as of its planned cut-off and the rail does not "
                        + "say so -- an exclusions panel is structurally blind to this class, because "
                        + "those messages ARE members");

        IssueWorkbenchVo asOfNow = workbench.forIssue(i, "da", true);
        assertFalse(uidsOf(asOfNow.getMembers()).contains(withdrawn.getUid()),
                "the message was withdrawn before now, so today's answer must not carry it");
        assertEquals(1, asOfNow.getMembers().size(),
                "the as-of-now list came back empty, so the absence above says nothing about the "
                        + "withdrawal -- it would hold for any reason the resolve produced nothing");
        assertTrue(rowOf(asOfNow.getChecklist(), "CANCELLED_MEMBERS_ALIVE_AT_CUTOFF").isPassed(),
                "the rail warns about a withdrawn member the as-of-now list does not contain");
    }

    /**
     * The default view names the late messages without resolving the issue twice.
     *
     * The late list is the as-of-now membership, and the default view IS as of now
     * -- so it is a filter over the rows the screen already built. Resolving again
     * for it would double the cost of every issue screen anybody opens, and on an
     * in-force series a resolve is about a second, which is the whole reason this
     * envelope exists.
     *
     * COUNTED rather than described, because the reuse is one equality test in the
     * middle of a method and a refactor that always re-resolves would pass every
     * other case here. Measured against the read that legitimately does resolve
     * twice -- a view instant that is not now has no as-of-now member list to
     * filter -- so the counter is shown to be sensitive to exactly the work this
     * pins, rather than against an absolute number that moves whenever an
     * unrelated query is added to the screen.
     */
    @Test
    @Transactional
    public void theDefaultViewNamesTheLateMessagesWithoutASecondResolve() {
        PublicationSeries s = series();
        Date planned = new Date(OPENS.getTime() + 7 * DAY);
        PublicationIssue i = lifecycle.create(s, OPENS, IntervalBoundSource.STAMPED, planned, user());
        message("NM-001", Status.PUBLISHED);
        message("NM-002", Status.PUBLISHED);
        message("NM-003", Status.PUBLISHED, new Date(OPENS.getTime() + 14 * DAY));
        em.flush();

        Statistics stats = em.unwrap(Session.class).getSessionFactory().getStatistics();
        boolean wasEnabled = stats.isStatisticsEnabled();
        stats.setStatisticsEnabled(true);
        try {
            long before = stats.getQueryExecutionCount();
            IssueWorkbenchVo asOfNow = workbench.forIssue(i, "da", true);
            long defaultRead = stats.getQueryExecutionCount() - before;

            long mid = stats.getQueryExecutionCount();
            IssueWorkbenchVo asOfPlanned = workbench.forIssue(i, "da", true, planned);
            long chosenRead = stats.getQueryExecutionCount() - mid;

            assertEquals(1, asOfNow.getAfterPlannedCutoff().size(),
                    "the default read named nothing as late, so it had nothing to build the list from "
                            + "and the counts below would prove nothing");
            assertEquals(1, asOfPlanned.getAfterPlannedCutoff().size(),
                    "the read at another instant lost the late list, which is the list it pays a "
                            + "second resolve for");
            assertTrue(chosenRead > defaultRead,
                    "the default view ran " + defaultRead + " queries and the read at a chosen instant "
                            + "ran " + chosenRead + ". The default view answers as of now and the late "
                            + "list is as of now, so it is a filter over rows already in hand; equal "
                            + "counts mean it resolved the issue a second time to produce them");
        } finally {
            stats.setStatisticsEnabled(wasEnabled);
        }
    }

    /**
     * A named period start that is the issue's own still skips the second resolve,
     * whatever class the date arrives as.
     *
     * The reuse above turns on "was this answered over the issue's own period as
     * of now", and half of that test compares two dates that come from different
     * places: a persistent entity hands back {@link Timestamp}, the wire hands
     * back plain {@link Date}. Timestamp.equals(Date) is false for any Date that
     * is not itself a Timestamp, while Date.equals(Timestamp) is true when the
     * milliseconds agree -- so an equality test over the pair answers differently
     * depending on which side it is called on, and the answer that says
     * "different" buys a full candidate narrowing nobody asked for. About a second
     * on an in-force series, on a screen whose whole purpose is not to pay that
     * twice.
     *
     * Named here as the class the ENTITY side produces, against a stored start
     * that is a plain Date -- which is the direction that fails. Counted against
     * the plain read beside it, and against a read at another instant that
     * legitimately does resolve twice, so the counter is shown to be sensitive to
     * exactly the work this pins.
     */
    @Test
    @Transactional
    public void aNamedStartOfAnotherDateClassStillSkipsTheSecondResolve() {
        PublicationSeries s = series();
        Date planned = new Date(OPENS.getTime() + 7 * DAY);
        PublicationIssue i = lifecycle.create(s, OPENS, IntervalBoundSource.STAMPED, planned, user());
        message("NM-001", Status.PUBLISHED);
        message("NM-003", Status.PUBLISHED, new Date(OPENS.getTime() + 14 * DAY));
        em.flush();

        // Warmed, because the counts below are compared to each other and the
        // first read of an issue loads rows every later one finds already in hand.
        workbench.forIssue(i, "da", true);

        Statistics stats = em.unwrap(Session.class).getSessionFactory().getStatistics();
        boolean wasEnabled = stats.isStatisticsEnabled();
        stats.setStatisticsEnabled(true);
        try {
            long before = stats.getQueryExecutionCount();
            IssueWorkbenchVo plain = workbench.forIssue(i, "da", true);
            long plainRead = stats.getQueryExecutionCount() - before;

            long mid = stats.getQueryExecutionCount();
            IssueWorkbenchVo named = workbench.forIssue(i, "da", true, null,
                    new Timestamp(OPENS.getTime()));
            long namedRead = stats.getQueryExecutionCount() - mid;

            long third = stats.getQueryExecutionCount();
            workbench.forIssue(i, "da", true, planned);
            long anotherInstant = stats.getQueryExecutionCount() - third;

            assertEquals(1, plain.getAfterPlannedCutoff().size(),
                    "the plain read named nothing as late, so it built no list and the counts below "
                            + "would prove nothing");
            assertNotNull(named.getViewedFrom(), "the named start was not reported at all");
            assertEquals(OPENS.getTime(), named.getViewedFrom().longValue(),
                    "the start named on the request was not the one the screen answered over");
            assertEquals(uidsOf(plain.getAfterPlannedCutoff()),
                    uidsOf(named.getAfterPlannedCutoff()),
                    "naming the start the issue already has changed which messages read as late");
            assertEquals(plainRead, namedRead,
                    "the plain read ran " + plainRead + " queries and the read naming the SAME start "
                            + "ran " + namedRead + ". They answer over one window, so the second has "
                            + "the as-of-now member list in hand exactly as the first does; a higher "
                            + "count means the two dates compared unequal because one is a Timestamp "
                            + "and the other a Date");
            assertTrue(anotherInstant > plainRead,
                    "a read at another instant cost no more than the plain one (" + anotherInstant
                            + " vs " + plainRead + "), so the counter is not measuring the resolve "
                            + "this test claims");
        } finally {
            stats.setStatisticsEnabled(wasEnabled);
        }
    }

    /**
     * A period start the caller named moves the members, and leaves the rows about
     * the issue's own period alone.
     *
     * The edit case. An admin retyping an open issue's period is asking what THAT
     * period would contain, and the answer has to come from the period being typed
     * -- otherwise the count beside the form answers for the period on disk while
     * the form shows another, and the screen states two periods at once without
     * saying which number belongs to which.
     *
     * The half that must NOT move is the chain. INTERVAL_CHAINED asks whether this
     * issue opens exactly where its predecessor closed, which is a fact about what
     * is saved; answered off an unsaved period it would report a broken chain that
     * nobody has and that saving would not create. It is asserted here against a
     * predecessor stamped exactly at the stored start, so the row genuinely CAN
     * fail: read off the what-if start it would.
     */
    @Test
    @Transactional
    public void aNamedPeriodStartMovesTheMembersAndLeavesTheChainRowsAlone() {
        PublicationSeries s = series();
        PublicationIssue previous = lifecycle.create(s, new Date(OPENS.getTime() - 7 * DAY),
                IntervalBoundSource.STAMPED, OPENS, user());
        previous.setStatus(IssueStatus.PUBLISHED);
        previous.setCutoffStampedAt(OPENS);
        em.merge(previous);
        em.flush();

        Date planned = new Date(OPENS.getTime() + 14 * DAY);
        PublicationIssue i = lifecycle.create(s, OPENS, IntervalBoundSource.STAMPED, planned, user());
        message("NM-001", Status.PUBLISHED, new Date(OPENS.getTime() + DAY));
        Message inside = message("NM-005", Status.PUBLISHED, new Date(OPENS.getTime() + 5 * DAY));
        message("NM-009", Status.PUBLISHED, new Date(OPENS.getTime() + 9 * DAY));
        em.flush();

        Date from = new Date(OPENS.getTime() + 4 * DAY);
        Date at = new Date(OPENS.getTime() + 7 * DAY);
        IssueWorkbenchVo vo = workbench.forIssue(i, "da", true, at, from);

        assertEquals(List.of(inside.getUid()), uidsOf(vo.getMembers()),
                "the named period runs from day 4 to day 7 and holds exactly the message published "
                        + "on day 5; the screen answered over some other window");
        assertEquals(at.getTime(), vo.getViewedAt(),
                "the instant the caller asked for was not the one the screen answered at");
        assertNotNull(vo.getViewedFrom(), "the screen did not say which period start it answered over");
        assertEquals(from.getTime(), vo.getViewedFrom().longValue(),
                "the start the caller named was not the one the screen answered over, and the "
                        + "member list beside it is therefore labelled with the wrong period");

        assertEquals(1, memberCountOf(vo.getChecklist()),
                "the rail counted the stored period's members beside a list showing the named "
                        + "period's -- the disagreement the one-resolve rule exists to prevent");
        assertNotNull(vo.getOmissions(),
                "the named period carries no omissions panel; it is an open issue and was resolved");

        assertTrue(rowOf(vo.getChecklist(), "INTERVAL_CHAINED").isPassed(),
                "the chain row answered off the period named on the request rather than off the one "
                        + "the issue has. This issue opens exactly where its predecessor closed; a "
                        + "what-if start is not a break in the chain, and reporting one would send an "
                        + "admin to 'correct' an interval that is right: "
                        + rowOf(vo.getChecklist(), "INTERVAL_CHAINED").getDetail());
        assertTrue(rowOf(vo.getChecklist(), "INTERVAL_PRESENT").isPassed(),
                "the row about the stored interval's presence failed on an issue that has one");
    }

    /**
     * With no period start named, the screen answers over the issue's own -- and
     * says so.
     *
     * The default read is the same read, and `viewedFrom` is what makes the two
     * distinguishable on the wire: a client that could not tell a what-if answer
     * from the issue's own would label one with the other's heading, which is the
     * single failure a view parameter must not produce.
     */
    @Test
    @Transactional
    public void withNoPeriodStartNamedTheScreenAnswersOverTheStoredOne() {
        PublicationSeries s = series();
        Date planned = new Date(OPENS.getTime() + 14 * DAY);
        PublicationIssue i = lifecycle.create(s, OPENS, IntervalBoundSource.STAMPED, planned, user());
        message("NM-001", Status.PUBLISHED, new Date(OPENS.getTime() + DAY));
        message("NM-005", Status.PUBLISHED, new Date(OPENS.getTime() + 5 * DAY));
        message("NM-009", Status.PUBLISHED, new Date(OPENS.getTime() + 9 * DAY));
        em.flush();

        Date at = new Date(OPENS.getTime() + 7 * DAY);
        IssueWorkbenchVo implied = workbench.forIssue(i, "da", true, at);
        IssueWorkbenchVo named = workbench.forIssue(i, "da", true, at, OPENS);

        assertEquals(2, implied.getMembers().size(),
                "the issue's own period runs from its start to day 7 and holds the messages published "
                        + "on day 1 and day 5");
        assertEquals(uidsOf(named.getMembers()), uidsOf(implied.getMembers()),
                "naming the start the issue already has changed which messages came back");
        assertNotNull(implied.getViewedFrom());
        assertEquals(OPENS.getTime(), implied.getViewedFrom().longValue(),
                "a read that named no start reported answering over some other one");
        assertEquals(implied.getViewedFrom(), named.getViewedFrom());
        assertEquals(memberCountOf(named.getChecklist()), memberCountOf(implied.getChecklist()));
    }

    /**
     * A period start that does not fall BEFORE the instant being read at is
     * refused -- after it, and equal to it.
     *
     * A window that closes before it opens describes nothing, and one that closes
     * exactly where it opens describes no length. There is no honest answer to
     * give for either: the window is (start, instant], so both resolve to an empty
     * member list, which reads as "this period is empty" rather than "the two
     * dates you typed cannot make a period". The equal case is the one an admin
     * reaches by hand -- typing the same date into both halves of the form -- and
     * a 200 with a zero beside it is the answer least likely to be recognised as a
     * mistake. Refused at all three ways of naming the pair: after with an
     * instant, equal to one, and with no instant at all, where it is now.
     */
    @Test
    @Transactional
    public void aPeriodStartNotBeforeTheInstantIsRefused() {
        PublicationSeries s = series();
        Date planned = new Date(OPENS.getTime() + 14 * DAY);
        PublicationIssue i = lifecycle.create(s, OPENS, IntervalBoundSource.STAMPED, planned, user());
        message("NM-001", Status.PUBLISHED);
        em.flush();

        Date at = new Date(OPENS.getTime() + 7 * DAY);
        IssueLifecycleService.TransitionRefusedException inverted = assertThrows(
                IssueLifecycleService.TransitionRefusedException.class,
                () -> workbench.forIssue(i, "da", true, at, new Date(at.getTime() + DAY)),
                "a period whose start falls after the instant it is read at was answered rather "
                        + "than refused");
        assertEquals("INVALID_INSTANT", inverted.code());

        IssueLifecycleService.TransitionRefusedException empty = assertThrows(
                IssueLifecycleService.TransitionRefusedException.class,
                () -> workbench.forIssue(i, "da", true, at, new Date(at.getTime())),
                "a period start equal to the instant it is read at was answered rather than refused. "
                        + "The window is half-open, so the two name a period of no length -- and the "
                        + "answer is a 200 carrying zero members, which reads as an empty period");
        assertEquals("INVALID_INSTANT", empty.code());

        IssueLifecycleService.TransitionRefusedException ahead = assertThrows(
                IssueLifecycleService.TransitionRefusedException.class,
                () -> workbench.forIssue(i, "da", true, null,
                        new Date(System.currentTimeMillis() + DAY)),
                "a period starting in the future was answered as of now, which is before it began");
        assertEquals("INVALID_INSTANT", ahead.code());
    }

    /**
     * A frozen issue ignores a named period start, exactly as it ignores an instant.
     *
     * What a published issue holds is what it printed. There is no other period to
     * read it over, so one named here is neither honoured nor refused -- and the
     * one echoed back is the issue's own, so nobody is told a published issue
     * covered a period it did not. The start named here is one that would be
     * REFUSED on an open issue, which is what makes "ignored" the assertion rather
     * than "happened not to matter".
     */
    @Test
    @Transactional
    public void aFrozenIssueIgnoresANamedPeriodStart() {
        PublicationSeries s = series();
        Date planned = new Date(OPENS.getTime() + 14 * DAY);
        PublicationIssue i = lifecycle.create(s, OPENS, IntervalBoundSource.STAMPED, planned, user());
        message("NM-001", Status.PUBLISHED, new Date(OPENS.getTime() + DAY));
        em.flush();

        i.setStatus(IssueStatus.PUBLISHED);
        i.setCutoffStampedAt(planned);
        em.merge(i);
        em.flush();

        IssueWorkbenchVo vo = workbench.forIssue(i, "da", true, planned,
                new Date(planned.getTime() + DAY));

        assertNotNull(vo.getViewedFrom(),
                "a published issue reported no period start at all; it has one, and it is the one "
                        + "it printed from");
        assertEquals(OPENS.getTime(), vo.getViewedFrom().longValue(),
                "a published issue was answered over a period the caller named; its contents are a "
                        + "record, and labelling them with another period describes a document nobody "
                        + "published");
        assertTrue(Math.abs(vo.getViewedAt() - System.currentTimeMillis()) < 60_000,
                "a published issue was answered as of an instant the caller chose");
    }

    // The rail's WIRE SHAPE -- every CheckRow component reaching a property, and
    // the byte-identity with the hand-built map it replaced -- is pinned in
    // PublishChecklistVoTest. It needs no database, and a guard that only runs
    // where MySQL happens to be installed is a guard that stops running.

    // ------------------------------------------------------------------ helpers

    /** The member uids of a list, in the order it came back. */
    private static List<String> uidsOf(List<IssueMemberVo> rows) {
        return rows.stream().map(IssueMemberVo::getMessageUid).toList();
    }

    /** One rail row by code, so a test names the check it is asserting about. */
    private static PublishCheckRowVo rowOf(PublishChecklistVo rail, String code) {
        return rail.getRows().stream()
                .filter(r -> code.equals(r.getCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the rail has no " + code + " row at all"));
    }

    /** The same, off a rail that has not been rendered yet. */
    private static PublishCheckRowVo rowOf(PublishChecklistService.Checklist rail, String code) {
        return rowOf(PublishChecklistVo.of(rail), code);
    }

    /**
     * The member count the rail reports -- and the two places it is reported.
     *
     * The envelope's number is what the publish dialog prints as its headline; the
     * MEMBER_LIMIT row's detail line is what an admin reads on the rail beside it.
     * They are one answer, so they are compared here rather than in one more case:
     * a screen whose headline and whose rail state different counts for the same
     * release is a disagreement with nothing to say which is right.
     *
     * The row's detail is prose ("214 of 1000"), and an inapplicable row carries a
     * sentence with no number in it at all -- which is the zero the envelope
     * reports for an issue that raises no membership question.
     */
    private static int memberCountOf(PublishChecklistVo rail) {
        String detail = rowOf(rail, "MEMBER_LIMIT").getDetail();
        int of = detail.indexOf(" of ");
        int onTheRow = of < 0 ? 0 : Integer.parseInt(detail.substring(0, of));
        assertEquals(onTheRow, rail.getMemberCount(),
                "the rail's envelope counts " + rail.getMemberCount() + " members and its MEMBER_LIMIT "
                        + "row says \"" + detail + "\". The dialog prints the first and the admin reads "
                        + "the second, on one screen, about one release");
        return rail.getMemberCount();
    }

    private static int memberCountOf(PublishChecklistService.Checklist rail) {
        return memberCountOf(PublishChecklistVo.of(rail));
    }
}
