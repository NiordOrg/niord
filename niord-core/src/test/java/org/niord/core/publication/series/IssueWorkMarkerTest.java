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
import jakarta.persistence.LockModeType;
import jakarta.persistence.TemporalType;
import jakarta.transaction.UserTransaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.message.MessageSeries;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.TestIds;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.criteria.MessageSeriesCriterionVo;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.series.vo.IssueInFlightVo;
import org.niord.core.publication.series.vo.IssueWorkbenchVo;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.model.message.MainType;

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
 * The marker that says somebody is already publishing this.
 *
 * Every case here is about a SECOND reader -- a refreshed page, another tab,
 * another admin -- because the first one never needed telling: it is holding the
 * request. So the fixtures commit before they mark, and the assertions are made
 * from outside the transaction that wrote them. A test that marked and read
 * inside one transaction would pass while the feature did nothing at all.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class IssueWorkMarkerTest {

    @Inject
    IssueWorkMarker marker;

    @Inject
    IssueWorkbenchService workbench;

    @Inject
    EntityManager em;

    @Inject
    UserTransaction tx;

    // ------------------------------------------------------------------ fixtures

    /** An issue that is committed and readable by anybody, which is the premise. */
    private Integer committedIssue() throws Exception {
        tx.begin();
        PublicationCategory c = new PublicationCategory();
        c.setCategoryId(TestIds.category());
        c.setPriority(100);
        em.persist(c);

        String messageSeriesId = TestIds.id("ms-");
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

        PublicationIssue i = new PublicationIssue();
        i.setSeries(s);
        i.setPublicId(UUID.randomUUID().toString());
        i.setRepoPath("publications/" + i.getPublicId());
        i.setStatus(IssueStatus.OPEN);
        i.setIntervalFrom(new Date(System.currentTimeMillis() - 7 * 24 * 3600_000L));
        i.setIntervalFromSource(IntervalBoundSource.STAMPED);
        i.createDesc("da").setName("Test issue");
        em.persist(i);
        tx.commit();
        return i.getId();
    }

    /** The issue as another request would read it: its own transaction, no cache. */
    private PublicationIssue reread(Integer issueId) throws Exception {
        tx.begin();
        PublicationIssue issue = em.find(PublicationIssue.class, issueId);
        tx.commit();
        return issue;
    }

    /**
     * Move a standing marker back in time, which is the only way to produce the
     * one state nothing can produce on purpose: a render that died with the
     * server and left its marker behind.
     */
    private void backdateMarker(Integer issueId, long by) throws Exception {
        tx.begin();
        em.createQuery("UPDATE PublicationIssue i SET i.workingSince = :then WHERE i.id = :id")
                .setParameter("then", new Date(System.currentTimeMillis() - by), TemporalType.TIMESTAMP)
                .setParameter("id", issueId)
                .executeUpdate();
        tx.commit();
    }

    // -------------------------------------------------------------------- cases

    /**
     * The first start marks the issue and the second is refused.
     *
     * The refusal is the whole guard: the second press arrives while the first
     * render is running, and a publish that went ahead would archive and
     * overwrite the documents the first one is in the middle of writing.
     */
    @Test
    public void aSecondStartIsRefusedWhileTheFirstIsRunning() throws Exception {
        Integer issueId = committedIssue();

        marker.start(issueId, IssueWorkMarker.PUBLISH);

        PublicationIssue marked = reread(issueId);
        assertEquals(IssueWorkMarker.PUBLISH, marked.getWorkingAction(),
                "the marker was not committed, so no other request can see it -- which is the only "
                        + "reason it exists");
        assertNotNull(marked.getWorkingSince(), "a marker with no instant cannot be aged out");

        IssueLifecycleService.TransitionRefusedException refused =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> marker.start(issueId, IssueWorkMarker.AMEND));
        assertEquals(IssueWorkMarker.ISSUE_BUSY, refused.code());
        assertTrue(refused.getMessage().contains(IssueWorkMarker.PUBLISH),
                "the refusal has to name the action already running; a bare 'busy' leaves an admin "
                        + "guessing whose work they would be interrupting");

        assertEquals(IssueWorkMarker.PUBLISH, reread(issueId).getWorkingAction(),
                "the refused start overwrote the marker it was refused by");
    }

    /** Finish clears it, and the next action can start. */
    @Test
    public void finishClearsTheMarkerAndTheNextStartSucceeds() throws Exception {
        Integer issueId = committedIssue();

        marker.start(issueId, IssueWorkMarker.PUBLISH);
        marker.finish(issueId);

        PublicationIssue cleared = reread(issueId);
        assertNull(cleared.getWorkingAction(), "the marker outlived the work it described");
        assertNull(cleared.getWorkingSince());
        assertNull(marker.inFlightOf(cleared));

        marker.start(issueId, IssueWorkMarker.AMEND);
        assertEquals(IssueWorkMarker.AMEND, reread(issueId).getWorkingAction());
    }

    /**
     * A marker older than the ceiling is not believed, by either reader of it.
     *
     * It is the residue of a render that died with the server: nothing ran to
     * clear it. Without the ceiling that issue is unpublishable forever, by work
     * that stopped existing, and the only way out is a hand-written UPDATE
     * against the live database.
     */
    @Test
    public void aMarkerPastTheCeilingIsNeitherReportedNorRefusedAgainst() throws Exception {
        Integer issueId = committedIssue();

        marker.start(issueId, IssueWorkMarker.PUBLISH);
        backdateMarker(issueId, IssueWorkMarker.CEILING_MS + 60_000L);

        PublicationIssue stale = reread(issueId);
        assertNotNull(stale.getWorkingAction(), "the fixture cleared the marker instead of ageing it");
        assertNull(marker.inFlightOf(stale),
                "a marker past the ceiling was still reported as work in flight, so the screen shows a "
                        + "spinner for a render that is not running");

        marker.start(issueId, IssueWorkMarker.AMEND);
        assertEquals(IssueWorkMarker.AMEND, reread(issueId).getWorkingAction(),
                "the stale marker refused a new start, which locks the issue out of service until "
                        + "somebody edits the database by hand");
    }

    /**
     * The workbench carries it while it stands, and carries nothing when it does
     * not.
     *
     * Both halves matter equally. A screen that never hears about the work offers
     * the button twice; a screen that hears about work that has finished shows a
     * spinner nothing will ever end.
     */
    @Test
    public void theWorkbenchReportsTheMarkerAndOnlyWhileItStands() throws Exception {
        Integer issueId = committedIssue();

        tx.begin();
        IssueWorkbenchVo idle = workbench.forIssue(em.find(PublicationIssue.class, issueId), "da", false);
        tx.commit();
        assertNull(idle.getInFlight(), "an idle issue reported work in flight");

        marker.start(issueId, IssueWorkMarker.AMEND);

        tx.begin();
        IssueWorkbenchVo busy = workbench.forIssue(em.find(PublicationIssue.class, issueId), "da", false);
        tx.commit();
        IssueInFlightVo inFlight = busy.getInFlight();
        assertNotNull(inFlight, "the workbench did not report the amend that is running on this issue");
        assertEquals(IssueWorkMarker.AMEND, inFlight.getAction());
        assertNotNull(inFlight.getSince(), "without the instant a client cannot say how long to wait");
        assertTrue(inFlight.getSince() <= System.currentTimeMillis(),
                "the work began in the future");

        marker.finish(issueId);

        tx.begin();
        IssueWorkbenchVo after = workbench.forIssue(em.find(PublicationIssue.class, issueId), "da", false);
        tx.commit();
        assertNull(after.getInFlight(), "the workbench still reported work that had finished");
    }

    /**
     * A restart clears every marker, which is what makes a restart the cure.
     *
     * Whatever is still written down at boot is by definition the residue of a
     * render that died with the previous process -- no request survives a
     * restart. The observer is called directly here; through the container it
     * would already have run before this test's fixture existed.
     */
    @Test
    public void bootClearsEveryMarkerLeftByThePreviousRun() throws Exception {
        Integer issueId = committedIssue();
        marker.start(issueId, IssueWorkMarker.PUBLISH);

        marker.clearMarkersAtBoot(null);

        assertNull(reread(issueId).getWorkingAction(),
                "a marker survived the boot clear, so an issue whose render died with the server stays "
                        + "out of service for the whole ceiling");
    }

    /**
     * Finishing does not queue behind the lock the work itself is holding.
     *
     * The publish takes a pessimistic write lock on the issue row and holds it
     * for its whole transaction -- and that transaction is still open when the
     * action returns to the code that clears the marker. A clear that opened its
     * own transaction THERE would wait for a lock held by the very thread that is
     * waiting for it, and sit until the database's lock timeout expired: fifty
     * seconds added to every release, and a failure raised from a finally block
     * over a publish that had already succeeded.
     *
     * So the clear is deferred to the end of that transaction. What is asserted
     * is both halves of that: the call returns at once, and the marker is
     * actually gone once the lock is.
     */
    @Test
    public void finishDoesNotWaitForTheTransactionThatHoldsTheIssueLocked() throws Exception {
        Integer issueId = committedIssue();
        marker.start(issueId, IssueWorkMarker.PUBLISH);

        tx.begin();
        // Exactly what the publish holds, for exactly as long.
        em.find(PublicationIssue.class, issueId, LockModeType.PESSIMISTIC_WRITE);

        long before = System.currentTimeMillis();
        marker.finish(issueId);
        long elapsed = System.currentTimeMillis() - before;

        tx.commit();

        assertTrue(elapsed < 5_000L,
                "clearing the marker took " + elapsed + " ms while the issue row was locked by the work "
                        + "itself; it queued behind that lock instead of waiting for the transaction to "
                        + "end, which on a real release is the database's lock timeout");
        assertNull(reread(issueId).getWorkingAction(),
                "the deferred clear never ran, so the issue stays marked until the ceiling expires");
    }
}
