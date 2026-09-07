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
import org.niord.core.publication.series.vo.IssueOmissionsVo;
import org.niord.core.publication.series.vo.IssueWorkbenchVo;
import org.niord.core.publication.series.vo.PublishChecklistVo;
import org.niord.core.publication.series.vo.SystemPublicationIssueVo;

import java.util.Date;

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

        if (!resolved.frozen() && mayReadChecklist) {
            // allowFuture is false: the rail here is the reading screen's, not the
            // dialog's. Waiving the future cut-off is a choice an admin makes in
            // the publish dialog, which asks for its own rail at the instant it is
            // offering, and a screen that waived it by default would show a check
            // as satisfied that nobody had made.
            vo.setChecklist(PublishChecklistVo.of(
                    checklist.compute(issue, false, previews.isStaleFor(issue), resolved)));
        }

        // A frozen issue takes no resolve at all, so this is also the "not on a
        // published issue" rule: what a published issue left out is a question
        // about the release it made, and the answer would be computed against
        // today's corpus rather than against the one it was printed from.
        if (resolved.resolution() != null) {
            vo.setOmissions(IssueOmissionsVo.of(resolved.resolution().misses()));
        }
        return vo;
    }
}
