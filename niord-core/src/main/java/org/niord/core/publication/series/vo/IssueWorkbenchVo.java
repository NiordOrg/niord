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

package org.niord.core.publication.series.vo;

import org.niord.model.IJsonSerializable;

import java.util.List;

/**
 * One issue screen, in one response.
 *
 * The screen used to be five requests taken in sequence -- the issue, its
 * members, its trail, its standing decisions, its rail -- plus a sixth probe for
 * the omissions panel. Sequential rather than parallel on purpose: this
 * deployment answers 500 under concurrent load on these routes, which is why
 * this is one endpoint and not a fan-out the client joins. Six requests at a
 * 0.13-0.18 s floor is a second of waiting before a single resolve has run, and
 * three of the six each took their own resolve of the same issue.
 *
 * Each part is the SAME shape its own endpoint returns, field for field. Those
 * endpoints stay -- they have other consumers -- and the day one of them starts
 * answering something this envelope does not is the day the screen and the API
 * disagree without saying so.
 *
 * Two parts are conditional, and both absences are facts rather than failures:
 * a frozen issue carries no rail and no omissions, because both describe a
 * release that has already happened; and a curator who is not an admin gets the
 * screen without the rail, where before the rail's 403 blanked the whole screen.
 */
public class IssueWorkbenchVo implements IJsonSerializable {

    /** The issue itself, as GET /editable-issue/{publicId} returns it. */
    private SystemPublicationIssueVo issue;

    /**
     * The instant every part of this response was answered for, epoch milliseconds.
     *
     * ECHOED RATHER THAN ASSUMED. An open issue can be read as of now or as of its
     * planned cut-off, and the two answers differ by exactly the messages
     * published in between -- so a screen that rendered a member list without
     * knowing which of the two it received would label one instant's answer with
     * the other's heading. It is also what a caller compares its own request
     * against: an instant it named that was not honoured (a frozen issue ignores
     * it) is visible here rather than silently substituted.
     *
     * The whole envelope shares it: the members, the rail and the omissions are
     * all resolved at this instant, which is what stops the count on one panel
     * disagreeing with the list on the next.
     */
    private long viewedAt;

    /** The member rows, live or frozen by status, titled in the requested language. */
    private List<IssueMemberVo> members;

    /**
     * The members published AFTER the issue's planned cut-off, as of now.
     *
     * The one part of this response that is deliberately NOT answered at
     * {@link #viewedAt}. It is the difference between the two instants an open
     * issue can be read at, so it has to be the same list in both views -- read as
     * of now it names what publishing now would add beyond the planned period;
     * read as of the planned cut-off it names what publishing then would leave
     * out. A list that moved with the view could not say either.
     *
     * The rows are built exactly as {@link #members} are -- same shape, same
     * titles, same order -- so the two lists can be rendered by one component. The
     * ones that also appear in `members` do so because the as-of-now member list
     * genuinely contains them; which of the two lists shows a row is the reader's
     * decision, not a second membership rule.
     *
     * ALWAYS PRESENT, empty where the question does not arise: an issue with no
     * planned cut-off, one whose planned cut-off has not passed, and a frozen
     * issue, whose contents are what was printed rather than a question about
     * today.
     */
    private List<IssueMemberVo> afterPlannedCutoff = List.of();

    /** The Historik panel. */
    private List<IssueAuditEntryVo> audit;

    /** Every curation decision that STANDS, include and exclude alike. */
    private List<IssueOverrideVo> overrides;

    /**
     * The previews the server still holds, newest generation per language.
     *
     * ALWAYS PRESENT, empty where there is nothing stored -- unlike the two
     * conditional parts below, which are absent. The screen seeds its preview
     * rows from this on every read, and an absent key would have to be read as
     * "unknown", which is the state that makes a refresh offer to generate a
     * preview again while the rail beside it reports the one on disk as current.
     * Empty for a frozen issue: what it printed is the archived document, and a
     * preview of it is a document nobody can produce or need.
     */
    private List<IssuePreviewVo> previews = List.of();

    /** Absent on a frozen issue, and for a curator who is not an admin. */
    private PublishChecklistVo checklist;

    /** Absent where nothing was resolved -- a frozen issue, or one with no query. */
    private IssueOmissionsVo omissions;

    public SystemPublicationIssueVo getIssue() {
        return issue;
    }

    public void setIssue(SystemPublicationIssueVo issue) {
        this.issue = issue;
    }

    public long getViewedAt() {
        return viewedAt;
    }

    public void setViewedAt(long viewedAt) {
        this.viewedAt = viewedAt;
    }

    public List<IssueMemberVo> getMembers() {
        return members;
    }

    public void setMembers(List<IssueMemberVo> members) {
        this.members = members;
    }

    public List<IssueMemberVo> getAfterPlannedCutoff() {
        return afterPlannedCutoff;
    }

    public void setAfterPlannedCutoff(List<IssueMemberVo> afterPlannedCutoff) {
        this.afterPlannedCutoff = afterPlannedCutoff;
    }

    public List<IssueAuditEntryVo> getAudit() {
        return audit;
    }

    public void setAudit(List<IssueAuditEntryVo> audit) {
        this.audit = audit;
    }

    public List<IssueOverrideVo> getOverrides() {
        return overrides;
    }

    public void setOverrides(List<IssueOverrideVo> overrides) {
        this.overrides = overrides;
    }

    public List<IssuePreviewVo> getPreviews() {
        return previews;
    }

    public void setPreviews(List<IssuePreviewVo> previews) {
        this.previews = previews;
    }

    public PublishChecklistVo getChecklist() {
        return checklist;
    }

    public void setChecklist(PublishChecklistVo checklist) {
        this.checklist = checklist;
    }

    public IssueOmissionsVo getOmissions() {
        return omissions;
    }

    public void setOmissions(IssueOmissionsVo omissions) {
        this.omissions = omissions;
    }
}
