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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
        m.setPublishDateFrom(new Date(OPENS.getTime() + 3600_000L));
        em.persist(m);
        return m;
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

    // The rail's WIRE SHAPE -- every CheckRow component reaching a property, and
    // the byte-identity with the hand-built map it replaced -- is pinned in
    // PublishChecklistVoTest. It needs no database, and a guard that only runs
    // where MySQL happens to be installed is a guard that stops running.

    // ------------------------------------------------------------------ helpers

    /** One rail row by code, so a test names the check it is asserting about. */
    private static PublishCheckRowVo rowOf(PublishChecklistService.Checklist rail, String code) {
        return PublishChecklistVo.of(rail).getRows().stream()
                .filter(r -> code.equals(r.getCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the rail has no " + code + " row at all"));
    }

    /** The member count the MEMBER_LIMIT row reports, read off its detail line. */
    private static int memberCountOf(PublishChecklistService.Checklist rail) {
        String detail = rowOf(rail, "MEMBER_LIMIT").getDetail();
        int of = detail.indexOf(" of ");
        return of < 0 ? 0 : Integer.parseInt(detail.substring(0, of));
    }
}
