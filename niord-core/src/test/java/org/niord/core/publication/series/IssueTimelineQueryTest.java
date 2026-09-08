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
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.domain.Domain;
import org.niord.core.message.Message;
import org.niord.core.message.MessageSeries;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.TestIds;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.criteria.MessageSeriesCriterionVo;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.series.vo.IssueTimelineRowVo;
import org.niord.core.publication.series.vo.IssueTimelineVo;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The strip's QUERY, which nothing pinned before.
 *
 * Every other assertion about the timeline runs over the static builder with a
 * list the caller supplied, so `recent()` -- the method the dashboard actually
 * calls, and the one that decides how much of the archive is read -- had never
 * been exercised in either module. That is precisely where the bound lives: it
 * reads the newest `periods + 2` issues instead of all five hundred, and an
 * off-by-one there drops cells from the dashboard with nothing failing.
 *
 * The differential in IssueTimelineTest proves the WINDOW is wide enough over
 * fixtures. This proves the QUERY delivers that window: the ordering, the
 * two-query descs prime, and the live-count overlay landing on the open row.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class IssueTimelineQueryTest {

    @Inject
    IssueListService issueList;

    @Inject
    PublicationSeriesService seriesService;

    @Inject
    IssueLifecycleService lifecycle;

    @Inject
    EntityManager em;

    private static final long WEEK = 7L * 24 * 3600_000L;
    private static final Date FIRST = new Date(1_699_000_000_000L);

    /**
     * A limit high enough that the read is not the thing under test.
     *
     * The real route asks for fifty. The shared test desk owns more than that,
     * because this database is long-lived and never truncated, so an assertion
     * about WHICH series the rule selects has to be made against an uncapped read
     * or it is really an assertion about the slice.
     */
    private static final int UNCAPPED = 10_000;

    private String messageSeriesId;

    // ------------------------------------------------------------------ fixtures

    private PublicationSeries series(SeriesCadence cadence, SeriesStatus status, Domain domain) {
        PublicationCategory c = new PublicationCategory();
        c.setCategoryId(TestIds.category());
        c.setPriority(100);
        em.persist(c);

        messageSeriesId = TestIds.id("ms-");
        MessageSeries ms = new MessageSeries();
        ms.setSeriesId(messageSeriesId);
        ms.setMainType(MainType.NM);
        em.persist(ms);

        PublicationSeries s = new PublicationSeries();
        s.setSeriesId(TestIds.series());
        s.setStatus(status);
        s.setContentMode(ContentMode.GENERATED_FROM_QUERY);
        s.setReportId("some-report");
        s.setCadence(cadence);
        s.setTimeRelation(TimeRelation.PUBLISHED_IN_INTERVAL);
        s.setAliveAtCutoff(false);
        s.setReleaseMode(ReleaseMode.MANUAL_GATE);
        s.setNextIssueCreation(NextIssueCreation.MANUAL);
        s.setPublicAuthority(PublicAuthority.LEGACY);
        s.setMessagePublication(MessagePublication.NONE);
        s.setNumberingScheme(NumberingScheme.ISO_WEEK_YEAR);
        s.setCategory(c);
        s.setDomain(domain);
        s.getLanguages().add("da");

        IssueCriteriaVo doc = new IssueCriteriaVo();
        MessageSeriesCriterionVo node = new MessageSeriesCriterionVo();
        node.setValues(new ArrayList<>(List.of(messageSeriesId)));
        doc.getCriteria().add(node);
        s.setCriteria(doc);

        s.createDesc("da").setName("Test series " + s.getSeriesId());
        em.persist(s);
        return s;
    }

    /** A released weekly issue, chained off the one before it. */
    private PublicationIssue released(PublicationSeries series, int week) {
        PublicationIssue i = new PublicationIssue();
        i.setSeries(series);
        i.setPublicId(TestIds.id("iss-"));
        i.setRepoPath("publications/" + i.getPublicId());
        i.setStatus(IssueStatus.PUBLISHED);
        i.setIntervalFrom(new Date(FIRST.getTime() + (week - 1) * WEEK));
        i.setIntervalTo(new Date(FIRST.getTime() + week * WEEK));
        i.setIntervalToSource(IntervalBoundSource.STAMPED);
        i.setCutoffStampedAt(new Date(FIRST.getTime() + week * WEEK));
        i.setMemberCount(3);
        i.createDesc("da").setName("Uge " + week);
        em.persist(i);
        return i;
    }

    private User user() {
        User u = new User();
        u.setUsername(TestIds.user());
        em.persist(u);
        return u;
    }

    // -------------------------------------------------------------------- cases

    /**
     * The bounded read draws exactly the strip the whole archive draws.
     *
     * Thirty issues with a hole in the middle, so the window has to reach past its
     * own cells to find the pair that produces the missing periods. Compared
     * against the same builder run over the FULL archive -- the thing the query
     * used to hand it -- rather than against a hand-written expectation, because
     * what has to hold is that nothing changed.
     */
    @Test
    @Transactional
    public void theBoundedStripIsTheStripOverTheWholeArchive() {
        PublicationSeries s = series(SeriesCadence.WEEKLY, SeriesStatus.ACTIVE, TestOwnerDomain.of(em));
        for (int week = 1; week <= 30; week++) {
            // A three-week hole in the middle: the archive is not tidy, and the
            // synthesizer has to tile it from the pair either side.
            if (week >= 14 && week <= 16) {
                continue;
            }
            released(s, week);
        }
        em.flush();
        em.clear();

        PublicationSeries reloaded = seriesService.findBySeriesId(s.getSeriesId());
        Date now = new Date(FIRST.getTime() + 31 * WEEK);

        IssueTimelineVo bounded = issueList.recent(reloaded, 8, now, "da");
        IssueTimelineVo whole = IssueListService.buildRecent(reloaded,
                em.createQuery("SELECT DISTINCT i FROM PublicationIssue i LEFT JOIN FETCH i.descs "
                                + "WHERE i.series = :s "
                                + "ORDER BY COALESCE(i.cutoffStampedAt, i.intervalTo) DESC, i.publicId DESC",
                        PublicationIssue.class).setParameter("s", reloaded).getResultList(),
                8, now, "da");

        assertEquals(whole.getRows().size(), bounded.getRows().size());
        for (int r = 0; r < whole.getRows().size(); r++) {
            IssueTimelineRowVo a = whole.getRows().get(r);
            IssueTimelineRowVo b = bounded.getRows().get(r);
            assertEquals(a.getPublicId(), b.getPublicId(), "cell " + r + " is a different issue");
            assertEquals(a.getComputedStatus(), b.getComputedStatus(), "cell " + r + " has another status");
            assertEquals(a.getIntervalTo(), b.getIntervalTo(), "cell " + r + " closes elsewhere");
            // The label is the reason the descs are fetched at all; a bounded read
            // that skipped the second query would answer a derived name here.
            assertEquals(a.getLabel(), b.getLabel(), "cell " + r + " is named differently");
        }
        assertTrue(bounded.getRows().stream()
                        .anyMatch(r -> "MISSING".equals(r.getComputedStatus())),
                "the fixture has a three-week hole and the bounded strip found none of it");
    }

    /**
     * A withdrawn week is a cell of its own AND a period that is missing again.
     *
     * Through the QUERY, because that is where the status has to survive: the
     * synthesizer is told which rows still cover their periods, and a bounded read
     * that dropped the marker would draw a tidy strip over a week nobody covers.
     * The retired cell stays because retiring withdraws a document from the
     * workflow rather than unmaking it, and the MISSING cell beside it is where the
     * replacement gets created.
     */
    @Test
    @Transactional
    public void theStripShowsARetiredWeekAndTheGapItLeaves() {
        PublicationSeries s = series(SeriesCadence.WEEKLY, SeriesStatus.ACTIVE, TestOwnerDomain.of(em));
        for (int week = 1; week <= 6; week++) {
            released(s, week);
        }
        PublicationIssue withdrawn = released(s, 7);
        released(s, 8);
        em.flush();

        lifecycle.retire(withdrawn, user(), "the week that went out named the wrong charts");
        em.flush();
        em.clear();

        PublicationSeries reloaded = seriesService.findBySeriesId(s.getSeriesId());
        IssueTimelineVo strip = issueList.recent(reloaded, 8,
                new Date(FIRST.getTime() + 8 * WEEK + 3600_000L), "da");

        IssueTimelineRowVo cell = strip.getRows().stream()
                .filter(r -> withdrawn.getPublicId().equals(r.getPublicId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the retired issue lost its cell on the strip"));
        assertEquals("RETIRED", cell.getComputedStatus());

        List<IssueTimelineRowVo> missing = strip.getRows().stream()
                .filter(r -> "MISSING".equals(r.getComputedStatus()))
                .toList();
        assertEquals(1, missing.size(),
                "the withdrawn week is still drawn as covered, so the strip and the list offer no "
                        + "way to create its replacement");
        assertEquals(new Date(FIRST.getTime() + 7 * WEEK), missing.get(0).getIntervalTo(),
                "the missing cell must cover exactly the withdrawn week's period");
    }

    /**
     * The live count still lands on the OPEN row.
     *
     * The overlay resolves every OPEN issue in the list it is handed and writes the
     * count onto the matching cell. Bounding the list can only reduce how many it
     * resolves -- but only because a series' open issue sorts NEWEST, which is a
     * property of the coalesce ordering rather than of the status. If it ever
     * stopped being true, the one issue anybody is working on would be the one
     * cell with a stale count.
     */
    @Test
    @Transactional
    public void theLiveCountLandsOnTheOpenRowUnderTheBound() {
        PublicationSeries s = series(SeriesCadence.WEEKLY, SeriesStatus.ACTIVE, TestOwnerDomain.of(em));
        for (int week = 1; week <= 30; week++) {
            released(s, week);
        }
        PublicationIssue open = lifecycle.create(s,
                new Date(FIRST.getTime() + 30 * WEEK), IntervalBoundSource.STAMPED, user());
        // A stale column, as an import leaves it.
        open.setMemberCount(9);

        MessageSeries ms = em.createQuery(
                        "SELECT ms FROM MessageSeries ms WHERE ms.seriesId = :id", MessageSeries.class)
                .setParameter("id", messageSeriesId).getSingleResult();
        for (int n = 0; n < 2; n++) {
            Message m = new Message();
            m.setUid(UUID.randomUUID().toString());
            m.setMessageSeries(ms);
            m.setShortId("NM-" + n);
            m.setMainType(MainType.NM);
            m.setType(Type.TEMPORARY_NOTICE);
            m.setStatus(Status.PUBLISHED);
            m.setPublishDateFrom(new Date(FIRST.getTime() + 30 * WEEK + 3600_000L));
            em.persist(m);
        }
        em.flush();

        IssueTimelineVo strip = issueList.recent(s, 8, new Date(FIRST.getTime() + 31 * WEEK), "da");
        IssueTimelineRowVo cell = strip.getRows().stream()
                .filter(r -> open.getPublicId().equals(r.getPublicId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "the open issue has no cell; the bound dropped the row being worked on"));
        assertEquals(Integer.valueOf(2), cell.getMemberCount(),
                "the open cell reported its stored column rather than what the issue contains today");
    }

    /**
     * The descs prime RAN, which no assertion about a label can show.
     *
     * The strip names its cells from the issue descs, and the bounded read fetches
     * them in a second query by id because Hibernate applies a limit IN MEMORY on
     * a query carrying a collection fetch -- the joined form looks bounded and
     * still selects the whole archive. But a test that merely reads the label back
     * runs inside a transaction, where a lazy collection loads on touch and
     * succeeds: deleting the prime would leave every label assertion green while
     * the dashboard read one select per cell, which is the cost this was written
     * to remove.
     *
     * So the collection is asked whether it is initialised, on the rows the query
     * returned, after a clear -- and the guard below shows the check can fail, by
     * finding the same issues lazy when they are read without the prime.
     */
    @Test
    @Transactional
    public void theBoundedReadPrimesTheDescsOfTheRowsItKeeps() {
        PublicationSeries s = series(SeriesCadence.WEEKLY, SeriesStatus.ACTIVE, TestOwnerDomain.of(em));
        for (int week = 1; week <= 6; week++) {
            released(s, week);
        }
        em.flush();
        em.clear();

        PublicationSeries reloaded = seriesService.findBySeriesId(s.getSeriesId());
        List<PublicationIssue> primed = issueList.newestIssuesOf(reloaded, 4);
        assertEquals(4, primed.size(), "the bound did not take; there is nothing to prove about");
        for (PublicationIssue issue : primed) {
            assertTrue(Hibernate.isInitialized(issue.getDescs()),
                    "issue " + issue.getPublicId() + " came back with lazy descs, so the strip reads "
                            + "its name in a select of its own -- one per cell, per series, per "
                            + "dashboard");
        }

        // The sensitivity guard: the same rows, read WITHOUT the prime, are lazy.
        // Without this the assertion above would also pass if the persistence
        // context happened to be handing back instances somebody else hydrated.
        em.clear();
        List<PublicationIssue> unprimed = em.createQuery(
                        "SELECT i FROM PublicationIssue i WHERE i.series = :s ORDER BY i.publicId DESC",
                        PublicationIssue.class)
                .setParameter("s", reloaded)
                .setMaxResults(4)
                .getResultList();
        assertFalse(Hibernate.isInitialized(unprimed.get(0).getDescs()),
                "an unprimed read came back initialised too, so the check above is not measuring the "
                        + "prime -- the descs are eager, or the context is being reused");
    }

    /**
     * A short archive is answered in full, not padded or truncated.
     *
     * The limit is a maximum. A series with three issues has three real cells, and
     * the setMaxResults must not become a floor the query pads up to.
     */
    @Test
    @Transactional
    public void aSeriesWithFewerIssuesThanTheWindowAnswersAllOfThem() {
        PublicationSeries s = series(SeriesCadence.NONE, SeriesStatus.ACTIVE, TestOwnerDomain.of(em));
        for (int week = 1; week <= 3; week++) {
            released(s, week);
        }
        em.flush();

        IssueTimelineVo strip = issueList.recent(s, 8, new Date(FIRST.getTime() + 4 * WEEK), "da");
        assertEquals(3, strip.getRows().size());
        assertFalse(strip.getGapDetection().isEnabled(),
                "a cadence-less series synthesized cells for periods it does not have");
        for (IssueTimelineRowVo row : strip.getRows()) {
            assertNotNull(row.getLabel(), "a cell lost its name; the descs prime did not run");
        }
    }

    // ---------------------------------------------------- the desk's series set

    /**
     * The strip's series are the desk's cards, split by whether they have a calendar.
     *
     * The dashboard groups its cards by exactly this rule and then asks the server
     * for the strips. Two implementations of one rule leave cards with no strip and
     * strips with no card -- a per-desk failure nothing reports, because both
     * halves look internally consistent.
     */
    @Test
    @Transactional
    public void theTimelineSeriesAreTheDesksCadencedPublications() {
        Domain desk = TestOwnerDomain.of(em);
        PublicationSeries weekly = series(SeriesCadence.WEEKLY, SeriesStatus.ACTIVE, desk);
        PublicationSeries yearly = series(SeriesCadence.YEARLY, SeriesStatus.ACTIVE, desk);
        PublicationSeries unscheduled = series(SeriesCadence.NONE, SeriesStatus.ACTIVE, desk);

        // A DRAFT nobody imported is being assembled and belongs on the dashboard.
        PublicationSeries draft = series(SeriesCadence.WEEKLY, SeriesStatus.DRAFT, desk);

        // An imported DRAFT is legacy history that was never activated here; the
        // dashboard deliberately does not show it as a live publication.
        PublicationSeries imported = series(SeriesCadence.WEEKLY, SeriesStatus.DRAFT, desk);
        imported.setImportSource("legacy");

        // A retired series has stopped, and a one-off is not a series with periods.
        PublicationSeries retired = series(SeriesCadence.WEEKLY, SeriesStatus.RETIRED, desk);
        PublicationSeries oneOff = series(SeriesCadence.WEEKLY, SeriesStatus.ACTIVE, desk);
        oneOff.setKind(SeriesKind.ONE_OFF);

        // Another desk's publication. A series is listed in its owner domain and
        // nowhere else -- that is the whole point of having an owner -- and a
        // scoped read that reached it would put a strip on a dashboard with no card
        // to hang it on.
        PublicationSeries elsewhere = series(SeriesCadence.WEEKLY, SeriesStatus.ACTIVE,
                TestOwnerDomain.of(em, "test-other-desk"));
        em.flush();

        // Read UNCAPPED. This suite shares a long-lived database that is never
        // truncated between runs, so the shared test desk owns far more than the
        // fifty a real request asks for -- and a containment assertion against a
        // capped read would be asserting against the slice rather than the rule.
        List<String> cadenced = seriesService
                .findTimelineSeries(desk.getDomainId(), true, UNCAPPED).stream()
                .map(PublicationSeries::getSeriesId).toList();
        assertTrue(cadenced.contains(weekly.getSeriesId()));
        assertTrue(cadenced.contains(yearly.getSeriesId()));
        assertTrue(cadenced.contains(draft.getSeriesId()),
                "a draft nobody imported is being assembled and belongs on the dashboard");
        assertFalse(cadenced.contains(unscheduled.getSeriesId()),
                "a series with no calendar has no periods and belongs to the other read");
        assertFalse(cadenced.contains(imported.getSeriesId()),
                "an imported draft is legacy history, not a live publication");
        assertFalse(cadenced.contains(retired.getSeriesId()));
        assertFalse(cadenced.contains(oneOff.getSeriesId()));
        assertFalse(cadenced.contains(elsewhere.getSeriesId()),
                "another desk's publication was listed for a desk that does not own it");

        // Weeklies before annuals: the dashboard is scanned for what is due next,
        // and the once-a-year publication must not head the queue.
        assertTrue(cadenced.indexOf(weekly.getSeriesId()) < cadenced.indexOf(yearly.getSeriesId()),
                "the strips are not ordered by how often the series comes out");

        List<String> unscheduledIds = seriesService
                .findTimelineSeries(desk.getDomainId(), false, UNCAPPED).stream()
                .map(PublicationSeries::getSeriesId).toList();
        assertTrue(unscheduledIds.contains(unscheduled.getSeriesId()));
        assertFalse(unscheduledIds.contains(weekly.getSeriesId()),
                "the two reads overlap; a series would then get two strips");

        assertTrue(seriesService.findTimelineSeries("no-such-desk", true, UNCAPPED).isEmpty(),
                "a desk that owns nothing must answer an empty list rather than the estate");
        assertTrue(seriesService.findTimelineSeries("  ", true, UNCAPPED).isEmpty(),
                "a blank domain answered with something; this read never scans the estate");

        // Unnarrowed: the whole desk, in one read. The dashboard draws both groups
        // of card off one domain, so asking each half separately pays the round
        // trip twice for one question.
        List<String> both = seriesService
                .findTimelineSeries(desk.getDomainId(), null, UNCAPPED).stream()
                .map(PublicationSeries::getSeriesId).toList();
        assertTrue(both.containsAll(cadenced),
                "the unnarrowed read dropped series the scheduled read returns");
        assertTrue(both.containsAll(unscheduledIds),
                "the unnarrowed read dropped series the calendar-less read returns");
        assertEquals(cadenced.size() + unscheduledIds.size(), both.size(),
                "the unnarrowed read is not exactly the two halves; either a series is listed "
                        + "twice -- two strips for one card -- or the two rules disagree about one");

        // The cadence rank spans BOTH kinds, so the calendar-less series come last
        // in one list rather than being a second list appended by the caller.
        assertTrue(both.indexOf(weekly.getSeriesId()) < both.indexOf(yearly.getSeriesId()),
                "the unnarrowed read is not ordered by how often the series comes out");
        assertTrue(both.indexOf(yearly.getSeriesId()) < both.indexOf(unscheduled.getSeriesId()),
                "a series with no calendar sorted ahead of a scheduled one; the dashboard reads "
                        + "the scheduled cards first");

        assertFalse(both.contains(imported.getSeriesId()),
                "the unnarrowed read reaches rows neither narrowed read does");
        assertFalse(both.contains(retired.getSeriesId()));
        assertFalse(both.contains(oneOff.getSeriesId()));
        assertFalse(both.contains(elsewhere.getSeriesId()),
                "another desk's publication was listed for a desk that does not own it");

        assertTrue(seriesService.findTimelineSeries("no-such-desk", null, UNCAPPED).isEmpty(),
                "an unnarrowed read of a desk that owns nothing answered with something");
    }

    /**
     * One answer, one cap -- not one cap per kind.
     *
     * The bound exists so a single request cannot become an enumeration of a
     * desk's whole inventory. Applying it to each half of an unnarrowed read
     * instead would quietly double what one request returns, and the caller
     * receiving twice the documented maximum has no way to tell.
     */
    @Test
    @Transactional
    public void theSliceAppliesToTheUnionOfBothKinds() {
        Domain desk = TestOwnerDomain.of(em);
        series(SeriesCadence.WEEKLY, SeriesStatus.ACTIVE, desk);
        series(SeriesCadence.YEARLY, SeriesStatus.ACTIVE, desk);
        series(SeriesCadence.NONE, SeriesStatus.ACTIVE, desk);
        series(SeriesCadence.NONE, SeriesStatus.ACTIVE, desk);
        em.flush();

        List<String> all = seriesService
                .findTimelineSeries(desk.getDomainId(), null, UNCAPPED).stream()
                .map(PublicationSeries::getSeriesId).toList();
        assertTrue(all.size() > 2, "the fixture left nothing for the cap to truncate");

        List<PublicationSeries> capped =
                seriesService.findTimelineSeries(desk.getDomainId(), null, 2);
        assertEquals(2, capped.size(),
                "the cap was applied per kind rather than to the answer; one request then returns "
                        + "twice the documented maximum");
        assertEquals(all.subList(0, 2),
                capped.stream().map(PublicationSeries::getSeriesId).toList(),
                "the cap kept different series than the head of the reading order");
        assertFalse(capped.get(0).getDescs().isEmpty(),
                "the capped series lost its descs; the naming patterns are then read one query "
                        + "per series");
    }

    /**
     * The cap is a SLICE, and it keeps the series read first.
     *
     * Refusing because a desk owns more publications than one request answers for
     * would blank its whole dashboard. Truncating keeps the strips it can have, and
     * the ones it keeps are the ones at the top of the reading order.
     */
    @Test
    @Transactional
    public void theSeriesCapTruncatesRatherThanRefuses() {
        Domain desk = TestOwnerDomain.of(em);
        series(SeriesCadence.WEEKLY, SeriesStatus.ACTIVE, desk);
        series(SeriesCadence.YEARLY, SeriesStatus.ACTIVE, desk);
        series(SeriesCadence.YEARLY, SeriesStatus.ACTIVE, desk);
        em.flush();

        // Asserted against the uncapped read rather than against a named series:
        // this suite shares a long-lived database with every other one, so what the
        // desk owns is not knowable from the fixture -- but the cap keeping the
        // HEAD of the same order is, and that is the whole claim.
        List<PublicationSeries> all = seriesService.findTimelineSeries(desk.getDomainId(), true, 50);
        assertTrue(all.size() > 1, "the fixture left nothing for the cap to truncate");

        List<PublicationSeries> capped = seriesService.findTimelineSeries(desk.getDomainId(), true, 1);
        assertEquals(1, capped.size(), "the cap refused rather than truncating; a desk owning more "
                + "publications than one request answers for would get no dashboard at all");
        assertEquals(all.get(0).getSeriesId(), capped.get(0).getSeriesId(),
                "the cap kept a different series than the head of the reading order");
        assertFalse(capped.get(0).getDescs().isEmpty(),
                "the capped series lost its descs; the naming patterns are then read one query per series");
    }
}
