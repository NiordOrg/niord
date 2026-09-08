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
import org.niord.core.publication.series.vo.IssuePreviewVo;
import org.niord.core.publication.series.vo.IssueWorkbenchVo;
import org.niord.core.publication.series.vo.PublishChecklistVo;
import org.niord.core.publication.series.vo.SystemPublicationIssueVo;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * The issue screen, assembled once.
 *
 * The assembly lives in core rather than on the endpoint for the reason every
 * other rule in this package does: the web layer has no container tests, so a
 * decision taken there -- which parts a frozen issue carries, what a non-admin
 * sees, which instant the omissions answer for -- is a decision nothing can pin.
 *
 * ONE INSTANT for the whole screen. The members, the rail and the omissions all
 * read the same {@link IssueResolution}, so the panel cannot answer for a
 * different cut-off than the list beside it. That is a change from the six
 * requests this replaces: the omissions probe used to ask at the issue's nominal
 * interval end, so on an open weekly issue whose period closes in the future a
 * message with a future publish date read as a member in one panel and as an
 * AFTER_CUTOFF omission in the other.
 *
 * WHICH instant is the caller's to choose, within one bound each way. An open
 * issue whose planned cut-off has passed has two honest answers -- what it holds
 * now, and what it held when its period closed -- and they differ by exactly the
 * messages published in between. Both are answered here at the SAME instant
 * throughout, so choosing the view moves every panel together; and the one list
 * that names the difference between them is carried alongside, computed as of
 * now whichever view was asked for.
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
        // Read once and carried, not re-read per use: the instant the screen is
        // answered at, the bound the caller's instant is checked against and the
        // instant the late list is computed at are the same "now", and three
        // separate clock reads make them three instants a few milliseconds apart.
        Date now = new Date();
        Date viewed = at == null || IssueResolutionService.isFrozen(issue)
                ? now : validInstant(issue, at, now);

        IssueResolution resolved = resolutions.forIssue(issue, viewed);

        IssueWorkbenchVo vo = new IssueWorkbenchVo();
        vo.setIssue(issue.toVo(SystemPublicationIssueVo.class));
        // Echoed rather than left to the caller to assume: an instant that was
        // ignored -- which is what a frozen issue does with one -- would otherwise
        // be indistinguishable from one that was honoured.
        vo.setViewedAt(viewed.getTime());
        List<IssueMemberVo> members = memberList.members(issue, lang, resolved);
        vo.setMembers(members);
        vo.setAfterPlannedCutoff(publishedAfterPlannedCutoff(issue, lang, resolved, members, now));
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
        List<IssuePreviewVo> stored = (!resolved.frozen() && mayReadChecklist)
                ? previews.stored(issue) : List.of();
        vo.setPreviews(stored);

        if (!resolved.frozen() && mayReadChecklist) {
            // allowFuture is false: the rail here is the reading screen's, not the
            // dialog's. Waiving the future cut-off is a choice an admin makes in
            // the publish dialog, which asks for its own rail at the instant it is
            // offering, and a screen that waived it by default would show a check
            // as satisfied that nobody had made.
            // The PREVIEW_FRESH row is answered off the rows just read rather than
            // from a second pass over the store: the rail and the preview badges
            // beside it are then one answer by construction, and the screen reads
            // each language's directory once.
            vo.setChecklist(PublishChecklistVo.of(
                    checklist.compute(issue, false, previews.isStaleFor(issue, stored), resolved)));
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
     * The members published after the issue's planned cut-off, as of NOW.
     *
     * The difference between the two instants an open issue can be read at, named
     * message by message. It is what a release at this moment would carry beyond
     * the period the issue declares, and equally what a release stamped at that
     * period's close would leave behind -- one list, two readings, which is why it
     * is computed as of now whichever view was asked for. Answered per view it
     * would empty itself in the planned view, and the screen would then offer a
     * choice between two instants without saying what turns on it.
     *
     * Built through the member list the screen already uses, so the rows are the
     * rows: same shape, same titles, same order. A second builder here would be a
     * second definition of what a member row is, and the two would drift the first
     * time a field was added to one of them.
     *
     * THE AS-OF-NOW RESOLUTION IS REUSED when that is what the screen was answered
     * from, which is the ordinary case -- the default view. Only a caller asking
     * for another instant pays a second resolve, and it pays for exactly this
     * list.
     *
     * Empty in the three cases where the question does not arise: no planned
     * cut-off to be after, a planned cut-off still ahead of us -- nothing can have
     * been published after an instant that has not happened -- and a frozen issue,
     * whose contents are a record rather than a question about today.
     */
    private List<IssueMemberVo> publishedAfterPlannedCutoff(PublicationIssue issue, String lang,
                                                            IssueResolution viewed,
                                                            List<IssueMemberVo> viewedMembers,
                                                            Date now) {
        Date planned = issue.getIntervalTo();
        if (viewed.frozen() || planned == null || !planned.before(now)) {
            return List.of();
        }

        List<IssueMemberVo> live = viewed.at().getTime() == now.getTime()
                ? viewedMembers
                : memberList.members(issue, lang, resolutions.forIssue(issue, now));

        List<IssueMemberVo> late = new ArrayList<>();
        for (IssueMemberVo row : live) {
            // The live publish date, which is what the row carries on an open
            // issue -- the column is named for what it holds once the issue is
            // frozen, and there is nothing frozen here to hold.
            Date published = row.getFrozenPublishDateFrom();
            if (published != null && published.after(planned)) {
                late.add(row);
            }
        }
        return late;
    }

    /**
     * The caller's instant, or a refusal naming the window it had to fall in.
     *
     * Both bounds are refusals rather than clamps. An instant before the period
     * opened describes a window this issue does not have, and one in the future
     * describes a member set that does not exist yet -- and either, silently
     * clamped, would hand back a confident answer under a heading naming the
     * instant that was asked for. A lower bound is only checked where the issue
     * has one: an issue that says what stood at an instant carries no period start
     * to be before.
     */
    private static Date validInstant(PublicationIssue issue, Date at, Date now) {
        Date opens = issue.getIntervalFrom();
        if (at.after(now) || (opens != null && at.before(opens))) {
            throw new IssueLifecycleService.TransitionRefusedException("INVALID_INSTANT",
                    "cannot read this issue as of " + at.getTime() + ": the instant must fall at or "
                            + "after the start of its period"
                            + (opens == null ? "" : " (" + opens.getTime() + ")")
                            + " and at or before now (" + now.getTime() + "). Earlier is a window "
                            + "this issue does not cover, and later is a member set that does not "
                            + "exist yet.");
        }
        return at;
    }
}
