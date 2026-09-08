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

    /** The member rows, live or frozen by status, titled in the requested language. */
    private List<IssueMemberVo> members;

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

    public List<IssueMemberVo> getMembers() {
        return members;
    }

    public void setMembers(List<IssueMemberVo> members) {
        this.members = members;
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
