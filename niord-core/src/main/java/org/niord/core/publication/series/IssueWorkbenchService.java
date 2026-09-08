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
import org.niord.core.publication.series.vo.IssuePreviewVo;
import org.niord.core.publication.series.vo.IssueWorkbenchVo;
import org.niord.core.publication.series.vo.PublishChecklistVo;
import org.niord.core.publication.series.vo.SystemPublicationIssueVo;

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
        IssueResolution resolved = resolutions.forIssue(issue, new Date());

        IssueWorkbenchVo vo = new IssueWorkbenchVo();
        vo.setIssue(issue.toVo(SystemPublicationIssueVo.class));
        vo.setMembers(memberList.members(issue, lang, resolved));
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
}
