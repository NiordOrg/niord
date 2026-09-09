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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.niord.core.service.BaseService;
import org.niord.core.user.User;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * BOTH ENDS OF A PUBLICATION'S PUBLIC PERIOD, decided by hand.
 *
 * The period is one of the three independent facts about a publication that
 * comes out once, and the only one nothing else writes: there is no successor
 * whose release would cap it and no cadence to derive it from. So it is set
 * here, as one decision about one interval, rather than as two fields somebody
 * can move independently into a period that ends before it starts.
 *
 * WHY THIS IS NOT IN THE ENDPOINT. The rule and the trail belong together: the
 * validation says which periods are describable and the audit entry says which
 * one was chosen, and an admin reading the history months later is asking about
 * exactly the pair. Keeping them beside each other also puts them where a test
 * with a database can reach them -- the surface that calls this has none -- and
 * the four detail keys are a contract with a history panel in another
 * repository, so "the entry has the values it claims" has to be assertable.
 *
 * A STARTING INSTANT IN THE FUTURE IS ORDINARY, and is why the two ends are
 * written together. A publication can be prepared today, given the day it is to
 * go public, and released now; it then sits PUBLISHED and off the public site
 * until that day arrives, because the public listing asks whether the window
 * overlaps the instant being read at and not whether anybody has pressed
 * anything. Nothing has to run on the day.
 */
@ApplicationScoped
public class IssuePublicWindowService extends BaseService {

    /**
     * A period that cannot be read as a period.
     *
     * 400 rather than a state conflict: the two instants are IN the request, so
     * re-sending the same body fails identically and only different instants
     * succeed.
     */
    public static final String INVALID = "PUBLIC_WINDOW_INVALID";

    @Inject
    IssueAuditService audit;

    @Inject
    PublicationIssueService issueService;

    /**
     * Set the period, or refuse to describe one that cannot exist.
     *
     * NOT ITS OWN TRANSACTION, deliberately. It runs inside the caller's, which
     * is what makes the write and its audit entry one act -- and a refusal here
     * is about the request rather than about the state, so it must not mark a
     * caller's transaction for rollback on its way out of a validation that
     * changed nothing.
     *
     * A REAL CHANGE ALSO MOVES THE SERIES' REVISION. The period is stored on the
     * issue and the form that sets it is version-checked against the series, so
     * the counter the next write is compared with is the one that has to move;
     * see the note beside the increment below.
     *
     * @param from the instant the publication becomes public, or null for "not
     *             decided yet" -- which only an issue still being assembled may
     *             say
     * @param to   the instant it stops being current, or null for open-ended,
     *             which is the shape most of these publications have
     * @return whether anything actually moved. A save that carries the period it
     *         already has back is not a decision, and writing an audit entry for
     *         it would fill the history with lines nobody took
     */
    public boolean set(PublicationIssue issue, Date from, Date to, User actor) {
        if (issue == null) {
            throw new IssueLifecycleService.TransitionRefusedException("ISSUE_NOT_FOUND",
                    "there is no issue to set a public period on");
        }
        refuseUndescribablePeriod(issue.getStatus(), from, to);

        Date fromBefore = issue.getPublicFrom();
        Date toBefore = issue.getPublicTo();
        if (sameInstant(fromBefore, from) && sameInstant(toBefore, to)) {
            return false;
        }

        issue.setPublicFrom(from);
        issue.setPublicTo(to);
        // MANUAL, because somebody decided it. The publish chain leaves a window
        // it did not derive alone, which is what stops a later action quietly
        // re-opening or re-capping a decision an admin made.
        issue.setPublicWindowSource(PublicWindowSource.MANUAL);
        issueService.update(issue);

        // AND THE SERIES' REVISION MOVES WITH IT.
        //
        // The period is written on the ISSUE, but the surface that sets it is the
        // one-off form, where one revision covers both rows: the caller composes
        // every write against the SERIES' counter and the check is made against
        // it. Writing only the issue would leave that counter where it was, so two
        // admins who both loaded the publication at revision 7 would both pass the
        // check at revision 7 and the second one's period would silently replace
        // the first's -- on the one action that decides whether a document is on
        // the public site.
        //
        // Flushed first because the increment is a targeted UPDATE: a series
        // persisted earlier in this same transaction and not yet written matches no
        // row, and the failure names a conflict with a transaction that does not
        // exist. Only after a real change, so a save carrying the period back
        // unchanged neither writes history nor spends the caller's revision.
        em.flush();
        StaleVersionGuard.forceIncrement(em, issue.getSeries());

        // BOTH ENDS, BEFORE AND AFTER, and all four even when one of them did not
        // move. A history line reading "the period changed" answers nothing an
        // admin came to the panel for; they are there because a document is on or
        // off the public site when they did not expect it, and the question is
        // what the period was before somebody changed it.
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("fromBefore", millis(fromBefore));
        detail.put("fromAfter", millis(from));
        detail.put("toBefore", millis(toBefore));
        detail.put("toAfter", millis(to));
        audit.edited(issue, actor, AuditAction.VISIBILITY_WINDOW_CHANGED, detail);
        return true;
    }

    /**
     * The two periods that cannot be described, refused before anything is written.
     *
     * A RELEASED PUBLICATION HAS A START. The public listing selects on the window
     * overlapping the instant it is read at, so an issue that is PUBLISHED with no
     * starting instant is on no public site at all and nothing on the screen says
     * why -- it reads as released. Only an issue still being assembled may leave
     * the question open, and that is the one state where the answer is genuinely
     * "not decided yet".
     *
     * A PERIOD THAT ENDS BEFORE IT STARTS is not a shorter period, it is no
     * period: every read of it answers empty, and the publication is invisible for
     * a reason no screen can render. Refused rather than clamped, because clamping
     * would pick one of the two instants for the admin and neither choice is
     * theirs to guess.
     *
     * Static so the rule can be asserted directly and applied at an edge that has
     * no issue in hand yet.
     */
    public static void refuseUndescribablePeriod(IssueStatus status, Date from, Date to) {
        if (from == null && status != IssueStatus.OPEN) {
            throw new IssueLifecycleService.TransitionRefusedException(INVALID,
                    "a publication that has been released has to say when it becomes public; "
                            + "without a starting instant it is on no public site and nothing on the "
                            + "screen says why");
        }
        if (from != null && to != null && from.after(to)) {
            throw new IssueLifecycleService.TransitionRefusedException(INVALID,
                    "the public period would end at " + to + ", before it starts at " + from
                            + ". That is not a shorter period, it is no period: the publication "
                            + "would be readable at no instant at all.");
        }
    }

    private static Long millis(Date d) {
        return d == null ? null : d.getTime();
    }

    private static boolean sameInstant(Date a, Date b) {
        return Objects.equals(millis(a), millis(b));
    }
}
