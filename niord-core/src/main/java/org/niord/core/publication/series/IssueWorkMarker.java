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

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TemporalType;
import jakarta.transaction.Synchronization;
import jakarta.transaction.Transactional;
import jakarta.transaction.TransactionSynchronizationRegistry;
import org.niord.core.publication.series.vo.IssueInFlightVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

/**
 * Who is already publishing this issue, and since when.
 *
 * A release or an amend renders the issue's documents INSIDE the request --
 * seconds on a weekly, tens of seconds on a compiled annual. The client that
 * pressed the button waits on its own request and knows perfectly well that the
 * work is running. Nobody else does. A refreshed page, a second tab and a second
 * admin all read the issue as idle and offer the same button again, and the
 * second press starts the whole thing over: it archives the documents the first
 * one is halfway through writing, against a member set that is being frozen
 * underneath it.
 *
 * So the fact is WRITTEN DOWN, in its own transaction, before the render begins:
 * committed work is the only kind another request can read. Two columns on the
 * issue row carry it, the workbench reports it to every reader, and the two
 * actions refuse with {@link #ISSUE_BUSY} while it stands.
 *
 * ONE STATEMENT CLAIMS IT. The test for "is anybody else doing this" and the
 * write that says "I am" are a single conditional UPDATE, so two presses that
 * arrive together cannot both find the issue idle: one row is updated, and the
 * other statement matches nothing. A read followed by a write would leave exactly
 * the window this class exists to close.
 *
 * THIS IS NOT A LOCK, and nothing here is allowed to behave like one. The publish
 * transaction's own pessimistic lock on the series and the issue is what makes
 * two concurrent releases produce one stamp; that guarantee is untouched by this
 * and does not depend on it. What the marker adds is the thing a lock cannot: a
 * readable answer for the screens that are not holding it.
 */
@ApplicationScoped
public class IssueWorkMarker {

    private static final Logger log = LoggerFactory.getLogger(IssueWorkMarker.class);

    /** The refusal a second release or amend gets while one is already running. */
    public static final String ISSUE_BUSY = "ISSUE_BUSY";

    /** The two actions long enough to be worth marking. */
    public static final String PUBLISH = "PUBLISH";

    public static final String AMEND = "AMEND";

    /**
     * How long a marker is believed, and why there is a ceiling at all.
     *
     * A marker outlives its request in exactly one case: the server died between
     * the write and the clear. Nothing is left to clear it then -- no finally
     * block ran and no transaction rolled back -- so without a ceiling that issue
     * would be unpublishable forever, by a render that stopped existing, and the
     * only way out would be a hand-written UPDATE against the live database.
     *
     * TEN MINUTES, which is comfortably longer than the slowest render this
     * system does (a compiled annual of some fifty source issues, in the tens of
     * seconds) and comfortably shorter than an admin's patience. Set nearer the
     * render it would start refusing work that is genuinely still running, which
     * is the one thing worse than not marking it at all.
     *
     * The boot clear below is the ordinary way a dead render's marker goes away;
     * this ceiling covers the case where the process is still up and only the
     * request died.
     */
    public static final long CEILING_MS = 10 * 60 * 1000L;

    @Inject
    EntityManager em;

    @Inject
    TransactionSynchronizationRegistry synchronizations;

    /**
     * Claim the issue for this request, or refuse because somebody else has it.
     *
     * REQUIRES_NEW, and that is the whole mechanism rather than a detail: a
     * marker written inside the publish transaction becomes readable when the
     * publish commits, which is the instant it stops being true. It has to be
     * committed BEFORE the render, alone, so that the readers it is for can see
     * it while the render runs.
     *
     * @param issueId the issue being worked on
     * @param action  {@link #PUBLISH} or {@link #AMEND}, as the refusal will name
     *                it to the next caller
     * @throws IssueLifecycleService.TransitionRefusedException coded
     *         {@link #ISSUE_BUSY} when work is already running on the issue and
     *         its marker is younger than {@link #CEILING_MS}
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void start(Integer issueId, String action) {
        if (issueId == null) {
            return;
        }
        Date now = new Date();

        // One statement: the condition and the claim cannot be separated, so two
        // starts that arrive together cannot both find the issue free. The stale
        // arm of the condition is what lets a marker from a dead render be taken
        // over rather than waited out -- applied HERE as well as in inFlightOf, so
        // that the two never disagree about which markers count.
        int claimed = em.createQuery(
                        "UPDATE PublicationIssue i SET i.workingAction = :action, i.workingSince = :now "
                                + "WHERE i.id = :id "
                                + "AND (i.workingAction IS NULL OR i.workingSince < :staleBefore)")
                .setParameter("action", action)
                .setParameter("now", now, TemporalType.TIMESTAMP)
                .setParameter("id", issueId)
                .setParameter("staleBefore", new Date(now.getTime() - CEILING_MS), TemporalType.TIMESTAMP)
                .executeUpdate();
        if (claimed > 0) {
            return;
        }

        // Nothing was claimed, and there are two reasons for that. Read which,
        // because the refusal has to name the action and the instant -- "this is
        // busy" with nothing else in it leaves an admin watching a button that
        // never comes back and no idea whose work they would be interrupting.
        List<Object[]> held = em.createQuery(
                        "SELECT i.workingAction, i.workingSince FROM PublicationIssue i WHERE i.id = :id",
                        Object[].class)
                .setParameter("id", issueId)
                .getResultList();
        if (held.isEmpty()) {
            // No such issue. Not this method's refusal to make: the caller resolved
            // the issue before getting here, so a row that has gone since is a race
            // with a deletion, and the action behind this is the one that knows
            // what to say about it.
            return;
        }

        String heldAction = (String) held.get(0)[0];
        Date heldSince = (Date) held.get(0)[1];
        throw new IssueLifecycleService.TransitionRefusedException(ISSUE_BUSY,
                "a " + heldAction + " of this issue has been running since " + heldSince
                        + "; it renders the documents in place, so a second one would archive and "
                        + "overwrite what the first is still writing");
    }

    /**
     * The work is over, however it ended.
     *
     * Unconditional: the marker is cleared whether the action succeeded or was
     * refused, because both mean nothing is running any more. A refusal that left
     * the marker standing would take the issue out of service for the whole
     * ceiling over a publish that never touched it.
     *
     * DEFERRED WHILE A TRANSACTION IS OPEN, and this is not an optimisation. The
     * publish holds a pessimistic write lock on the issue row for the length of
     * its transaction, and that transaction is still open when the action returns
     * to whoever is calling this -- so a clear in a new transaction here would
     * queue behind a lock held by the very thread that is waiting for it, and sit
     * there until the database's lock timeout expired. Registered on the
     * transaction instead, it runs the moment the lock is gone, on either
     * outcome, and before the response is written.
     */
    public void finish(Integer issueId) {
        if (issueId == null) {
            return;
        }
        if (synchronizations.getTransactionKey() == null) {
            clear(issueId);
            return;
        }
        synchronizations.registerInterposedSynchronization(new Synchronization() {
            @Override
            public void beforeCompletion() {
                // Nothing: the row is still locked here, which is the reason this
                // is registered at all.
            }

            @Override
            public void afterCompletion(int status) {
                clear(issueId);
            }
        });
    }

    /**
     * The clear itself, in a transaction of its own.
     *
     * Its own transaction for the same reason the claim has one, and because
     * after-completion runs with no transaction to join. A failure here is logged
     * rather than thrown: it happens after the action has already succeeded or
     * failed on its own terms, so raising it would replace that outcome with one
     * about bookkeeping -- and a marker left behind is answered by the ceiling.
     */
    private void clear(Integer issueId) {
        try {
            QuarkusTransaction.requiringNew().run(() -> em.createQuery(
                            "UPDATE PublicationIssue i SET i.workingAction = NULL, i.workingSince = NULL "
                                    + "WHERE i.id = :id")
                    .setParameter("id", issueId)
                    .executeUpdate());
        } catch (RuntimeException e) {
            log.error("Could not clear the work marker on issue {}; it will expire after {} ms",
                    issueId, CEILING_MS, e);
        }
    }

    /**
     * What the workbench reports, or null where nothing is running.
     *
     * The ceiling is applied HERE rather than by a sweep, so that a marker left by
     * a dead render stops being reported at the same instant it stops refusing a
     * new start. A sweeper would have the two disagree for as long as its period.
     */
    public IssueInFlightVo inFlightOf(PublicationIssue issue) {
        if (issue == null || issue.getWorkingAction() == null || issue.getWorkingSince() == null) {
            return null;
        }
        if (issue.getWorkingSince().getTime() < System.currentTimeMillis() - CEILING_MS) {
            return null;
        }
        IssueInFlightVo vo = new IssueInFlightVo();
        vo.setAction(issue.getWorkingAction());
        vo.setSince(issue.getWorkingSince().getTime());
        return vo;
    }

    /**
     * Every marker is wiped at boot.
     *
     * A marker describes a request in progress, and no request survives a
     * restart: whatever is still written down when this runs is by definition the
     * residue of a render that died with the previous process. Clearing it here
     * rather than waiting out the ceiling is what makes a restart the cure for a
     * stuck issue -- which is the first thing anybody tries.
     *
     * Logged with the count because a non-zero one is a fact about the LAST run:
     * the server went down in the middle of publishing something, and whoever is
     * reading the boot log is the person who needs to know which issue to check.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void clearMarkersAtBoot(@Observes StartupEvent ev) {
        int cleared = em.createQuery(
                        "UPDATE PublicationIssue i SET i.workingAction = NULL, i.workingSince = NULL "
                                + "WHERE i.workingAction IS NOT NULL")
                .executeUpdate();
        log.info("Cleared {} publication work marker(s) left by the previous run", cleared);
    }
}
