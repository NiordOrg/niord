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
import jakarta.transaction.Transactional;

import org.niord.core.publication.series.IssueResolutionService.IssueResolution;
import org.niord.core.publication.series.vo.IssueMemberVo;
import org.niord.core.publication.series.vo.IssueWorkbenchVo;
import org.niord.core.publication.series.vo.PublishChecklistVo;
import org.niord.core.publication.series.vo.SystemPublicationIssueVo;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * The issue screen, assembled once.
 *
 * The assembly lives in core rather than on the endpoint for the reason every
 * other rule in this package does: the web layer has no container tests, so a
 * decision taken there -- which parts a frozen issue carries, what a non-admin
 * sees, which instant the omissions answer for -- is a decision nothing can pin.
 *
 * ONE PERIOD for the whole screen -- one start and one instant. The members, the
 * rail and the omissions all read the same {@link IssueResolution}, so the panel
 * cannot answer for a different window than the list beside it. That is a change
 * from the six requests this replaces: the omissions probe used to ask at the
 * issue's nominal interval end, so on an open weekly issue whose period closes in
 * the future a message with a future publish date read as a member in one panel
 * and as an AFTER_CUTOFF omission in the other.
 *
 * WHICH instant is the caller's to choose, within one bound each way. An open
 * issue whose planned cut-off has passed has two honest answers -- what it holds
 * now, and what it held when its period closed -- and they differ by exactly the
 * messages published in between. Both are answered here at the SAME instant
 * throughout, so choosing the view moves every panel together; and the list that
 * names the difference between them is carried alongside, measured against the
 * cut-off THIS READ IS ABOUT: the instant the caller named where it named one,
 * the issue's stored plan where it did not.
 *
 * SO IS THE START, on an open issue, and for the same reason at one remove: an
 * admin editing the period is asking what the period they are typing would
 * contain, which is a question about a window that is not on disk yet. Naming it
 * moves the whole screen onto that window; naming nothing leaves the screen on
 * the issue's own. Nothing about the what-if is written down and nothing is
 * cached -- it is a read, and the next read without it answers for the stored
 * period again.
 */
@ApplicationScoped
public class IssueWorkbenchService {

    @Inject
    IssueResolutionService resolutions;

    @Inject
    IssueMemberListService memberList;

    @Inject
    IssueAuditService audit;

    @Inject
    PublishChecklistService checklist;

    @Inject
    IssuePreviewService previews;

    @Inject
    MemberResolutionService resolver;

    /**
     * Everything one issue screen renders.
     *
     * @param lang             the language the member rows are TITLED in; it
     *                         decides nothing else, and every other part here is
     *                         language-independent
     * @param mayReadChecklist whether the caller is an admin. The endpoint's gate
     *                         is the curator tier -- four of the five parts are
     *                         curator reads -- and the rail is narrowed here
     *                         rather than by a second annotation, because a
     *                         second gate would refuse the whole screen
     */
    @Transactional
    public IssueWorkbenchVo forIssue(PublicationIssue issue, String lang, boolean mayReadChecklist) {
        return forIssue(issue, lang, mayReadChecklist, null);
    }

    /**
     * The same screen, answered as of an instant the caller named.
     *
     * @param at the instant to answer for, or null for now. It applies to an OPEN
     *           issue only -- a frozen one's contents are what was printed, so
     *           there is no instant to choose between and one named here is
     *           ignored rather than refused. Otherwise it must fall between the
     *           issue's period start and now: earlier is a period the issue had
     *           not opened in, later is a member set that does not exist yet, and
     *           both would produce an authoritative-looking answer about a state
     *           of the world that never held
     */
    @Transactional
    public IssueWorkbenchVo forIssue(PublicationIssue issue, String lang, boolean mayReadChecklist,
                                     Date at) {
        return forIssue(issue, lang, mayReadChecklist, at, null);
    }

    /**
     * The same screen, answered over a period the caller named.
     *
     * ONE PERIOD FOR THE WHOLE SCREEN, which is what the pair of parameters is
     * for. An admin editing an open issue's period is asking what that period
     * would contain, and every number on the screen has to answer that one
     * question: the member list, its count, the rail's membership rows and the
     * omissions panel all come off the single resolve taken here. Answered from
     * the stored period instead, the panel beside the form would state a count
     * for the period on disk while the form showed another, and neither would say
     * which.
     *
     * @param from the period start to answer over, or null for the issue's own.
     *             It is a what-if and is written nowhere; it may fall earlier or
     *             later than the stored start, and must not fall after {@code at}
     *             -- a period that closes before it opens describes no window.
     *             Ignored on a frozen issue, for the same reason {@code at} is
     */
    @Transactional
    public IssueWorkbenchVo forIssue(PublicationIssue issue, String lang, boolean mayReadChecklist,
                                     Date at, Date from) {
        // Read once and carried, not re-read per use: the instant the screen is
        // answered at, the bound the caller's instant is checked against and the
        // instant the late list is computed at are the same "now", and three
        // separate clock reads make them three instants a few milliseconds apart.
        Date now = new Date();
        boolean frozen = IssueResolutionService.isFrozen(issue);
        // What a frozen issue holds is what it printed, so neither half of the
        // caller's window applies to it. Dropped rather than refused, and dropped
        // here so that nothing below has to ask again.
        Date namedFrom = frozen ? null : from;
        Date viewed;
        if (frozen || (at == null && namedFrom == null)) {
            viewed = now;
        } else {
            // The floor the instant is checked against is the start of the period
            // being ASKED about. Where that is a what-if, checking the stored
            // start instead would refuse a period moved wholly earlier -- a
            // perfectly ordinary correction -- and it would refuse it while the
            // form on the screen showed exactly that period.
            // With no instant named the read is as of now, and only the named
            // start has to be checked: an issue whose period has not opened yet
            // reads as of now like any other, holding nothing.
            viewed = validInstant(namedFrom != null ? namedFrom : issue.getIntervalFrom(),
                    at == null ? now : at, now, namedFrom != null);
        }

        IssueResolution resolved = resolutions.forIssue(issue, viewed, namedFrom);
        Date lateAfter = lateReference(issue, at, frozen);

        IssueWorkbenchVo vo = new IssueWorkbenchVo();
        vo.setIssue(issue.toVo(SystemPublicationIssueVo.class));
        // Echoed rather than left to the caller to assume: an instant that was
        // ignored -- which is what a frozen issue does with one -- would otherwise
        // be indistinguishable from one that was honoured.
        vo.setViewedAt(viewed.getTime());
        // The period start the answer was actually taken over, on the same terms:
        // the what-if where one was honoured, the stored start otherwise, and null
        // where the issue has no start at all.
        vo.setViewedFrom(resolved.from() == null ? null : resolved.from().getTime());
        // The cut-off the late list below was measured against, on the same terms
        // and for the same reason: the reader is being told how many messages fall
        // after a cut-off, and a number without the instant it was taken against
        // cannot be checked against the date on the form beside it.
        vo.setLateAfter(lateAfter == null ? null : lateAfter.getTime());
        List<IssueMemberVo> members = memberList.members(issue, lang, resolved);
        vo.setMembers(members);
        vo.setAfterPlannedCutoff(publishedAfterPlannedCutoff(issue, lang, resolved, members, now,
                lateAfter));
        vo.setAudit(audit.forIssue(issue).stream().map(IssueAuditEntry::toVo).toList());
        // Still read on a frozen issue: an imported one carries standing decisions
        // that were taken before it was ever published here.
        vo.setOverrides(memberList.standingDecisions(resolved));

        // Off the same store the preview endpoints read, so the rows the screen
        // shows are the generations the server actually still holds: they are
        // swept on a TTL, and a screen that kept its own memory of them would go
        // on offering a preview that is no longer there. Nothing for a frozen
        // issue -- there is no live member list to preview, and the release it
        // made is the archived document.
        // And only for a reader who may also render and open one: the preview
        // endpoints are admin-only, so a row handed to anybody else would offer a
        // download that is refused. Narrowed exactly as the checklist below is.
        vo.setPreviews((!resolved.frozen() && mayReadChecklist)
                ? previews.stored(issue) : List.of());

        if (!resolved.frozen() && mayReadChecklist) {
            // allowFuture is false: the rail here is the reading screen's, not the
            // dialog's. Waiving the future cut-off is a choice an admin makes in
            // the publish dialog, which asks for its own rail at the instant it is
            // offering, and a screen that waived it by default would show a check
            // as satisfied that nobody had made.
            vo.setChecklist(PublishChecklistVo.of(checklist.compute(issue, false, resolved)));
        }

        // A frozen issue takes no resolve at all, so this is also the "not on a
        // published issue" rule: what a published issue left out is a question
        // about the release it made, and the answer would be computed against
        // today's corpus rather than against the one it was printed from.
        if (resolved.resolution() != null) {
            // Through the resolver rather than off the VO directly: the sample's
            // short ids are filled there, in one query over the capped rows, and a
            // panel listing omissions by uid alone is unreadable to the editor who
            // has to decide whether each one belongs in the issue.
            vo.setOmissions(resolver.omissions(resolved.resolution().misses()));
        }
        return vo;
    }

    /**
     * The cut-off the late list is measured against, or null where there is none.
     *
     * AN OPEN ISSUE HAS TWO DATES, a period start and a planned cut-off, and "now"
     * as the one alternative to the plan when the plan has passed. So the cut-off
     * this question is asked about is the one the reader is looking at: the
     * instant they named where they named one -- the planned view sends the stored
     * plan, and an admin editing the period sends the cut-off as typed -- and the
     * issue's stored plan where no other cut-off is in play. Measured against a
     * third instant instead, the count would answer about a release nobody is
     * looking at, and the date printed above it would not be the date it was taken
     * against.
     *
     * Null on a frozen issue: its contents are what it printed, so there is no
     * cut-off still to be chosen and nothing to be late for. Null too where an
     * open issue has no planned cut-off at all and the caller named no instant --
     * "after" needs something to be after.
     */
    private static Date lateReference(PublicationIssue issue, Date at, boolean frozen) {
        if (frozen) {
            return null;
        }
        return at != null ? at : issue.getIntervalTo();
    }

    /**
     * The members published after that cut-off, as of NOW.
     *
     * What a release at this moment would carry beyond the cut-off in question,
     * named message by message -- and equally what a release stamped at that
     * cut-off would leave behind. One list, two readings, and it is the same list
     * either way, which is why it is always taken from the AS-OF-NOW membership
     * however the screen itself was answered: read at the cut-off, the messages
     * published after it are by definition not members, so a list built from that
     * view could only ever be empty and the screen would be offering a choice
     * between two instants without saying what turns on it.
     *
     * Built through the member list the screen already uses, so the rows are the
     * rows: same shape, same titles, same order. A second builder here would be a
     * second definition of what a member row is, and the two would drift the first
     * time a field was added to one of them.
     *
     * THE AS-OF-NOW RESOLUTION IS REUSED when that is what the screen was answered
     * from, which is the ordinary case -- the default view. A caller that named
     * another instant, or another period start, pays a second resolve, and it pays
     * for exactly this list: the window it asked about is not the issue's own, and
     * what that window holds says nothing about what this issue holds beyond the
     * cut-off.
     *
     * Empty in the three cases where the question does not arise: no cut-off to be
     * after at all, a cut-off at or ahead of now -- nothing can have been published
     * after an instant that has not happened -- and a frozen issue, which reaches
     * here with no reference for exactly that reason.
     */
    private List<IssueMemberVo> publishedAfterPlannedCutoff(PublicationIssue issue, String lang,
                                                            IssueResolution viewed,
                                                            List<IssueMemberVo> viewedMembers,
                                                            Date now, Date reference) {
        if (reference == null || !reference.before(now)) {
            return List.of();
        }

        // Reusable only where the screen was answered over the ISSUE'S OWN period
        // as of now: a read over a what-if period is a different member set, and
        // filtering it here would name as late whatever that period happened to
        // hold rather than what the issue holds.
        boolean isTheAsOfNowSet = sameInstant(viewed.at(), now)
                && sameInstant(viewed.from(), issue.getIntervalFrom());
        List<IssueMemberVo> live = isTheAsOfNowSet
                ? viewedMembers
                : memberList.members(issue, lang, resolutions.forIssue(issue, now));

        List<IssueMemberVo> late = new ArrayList<>();
        for (IssueMemberVo row : live) {
            // The live publish date, which is what the row carries on an open
            // issue -- the column is named for what it holds once the issue is
            // frozen, and there is nothing frozen here to hold.
            Date published = row.getFrozenPublishDateFrom();
            if (published != null && published.after(reference)) {
                late.add(row);
            }
        }
        return late;
    }

    /**
     * Whether two dates name the same instant, whatever their classes.
     *
     * NOT {@link Objects#equals}, and the difference decides whether the screen
     * takes a second resolve it does not need. The dates compared here arrive from
     * two places: a persistent entity hands back {@link java.sql.Timestamp}, and
     * the wire hands back plain {@link Date}. Timestamp.equals(Date) is false for
     * any Date that is not itself a Timestamp, while Date.equals(Timestamp) is
     * true when the milliseconds agree -- so an equality test over the pair gives
     * opposite answers depending on which side it is called on, and the one that
     * says "different" costs a full candidate narrowing, about a second on an
     * in-force series, on every read that happens to hold the pair that way round.
     */
    private static boolean sameInstant(Date a, Date b) {
        return a == null ? b == null : b != null && a.getTime() == b.getTime();
    }

    /**
     * The caller's instant, or a refusal naming the window it had to fall in.
     *
     * Both bounds are refusals rather than clamps. An instant before the period
     * opened describes a window that period does not have, and one in the future
     * describes a member set that does not exist yet -- and either, silently
     * clamped, would hand back a confident answer under a heading naming the
     * instant that was asked for. A lower bound is only checked where there is
     * one: an issue that says what stood at an instant carries no period start to
     * be before.
     *
     * A CALLER-NAMED START MUST FALL STRICTLY BEFORE the instant, which the
     * issue's own start need not. The window is (start, instant], so a start equal
     * to the instant is an empty period; the resolve refuses to build one and the
     * screen would answer 200 with a member list of zero -- which reads as "this
     * period is empty" rather than "the two dates on your form are the same one".
     * Only where the pair came from the caller: an issue that has arrived at that
     * state on disk still has to be readable.
     *
     * @param opens    the start of the period being asked about, which is the
     *                 what-if start where the caller named one and the issue's own
     *                 otherwise
     * @param named    whether that start came from the caller, which decides both
     *                 the strictness above and what the refusal has to say: the
     *                 reader is looking at a form holding both halves, and "your
     *                 period start is after the instant you are reading at" is
     *                 actionable where "this issue does not cover that window" is
     *                 not
     */
    private static Date validInstant(Date opens, Date at, Date now, boolean named) {
        boolean beforeStart = opens != null
                && (named ? !at.after(opens) : at.before(opens));
        if (at.after(now) || beforeStart) {
            throw new IssueLifecycleService.TransitionRefusedException("INVALID_INSTANT",
                    "cannot read this issue as of " + at.getTime() + ": the instant must fall "
                            + (named ? "after" : "at or after")
                            + " the start of the period asked about"
                            + (opens == null ? "" : " (" + opens.getTime()
                                    + (named ? ", the one named on the request" : "") + ")")
                            + " and at or before now (" + now.getTime() + "). Earlier is a window "
                            + "that period does not cover"
                            + (named ? ", the same instant is a period of no length," : "")
                            + " and later is a member set that does not exist yet.");
        }
        return at;
    }
}
