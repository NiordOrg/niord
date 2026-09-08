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

import com.fasterxml.jackson.annotation.JsonInclude;
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

    /**
     * The period START every part of this response was answered over, epoch
     * milliseconds -- null where the issue has no start at all.
     *
     * The other half of the window {@link #viewedAt} closes, and echoed for the
     * same reason: a caller may name a start of its own -- the period an admin is
     * editing, before it is saved -- and a screen that could not tell which start
     * was honoured would label a what-if answer as the issue's own, or the issue's
     * own as the what-if. Absent from the request, this is the issue's stored
     * period start; named, it is the one that was named; on a frozen issue, always
     * the stored one, because a published issue's contents are what it printed.
     *
     * NULL IS A REAL ANSWER, not a missing one: an in-force issue has no lower
     * bound -- its contents are what stood at the cut-off, however long ago they
     * were published -- so the key is always serialised and carries null there.
     * Reporting 0 instead would state a period beginning in 1970.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private Long viewedFrom;

    /** The member rows, live or frozen by status, titled in the requested language. */
    private List<IssueMemberVo> members;

    /**
     * The cut-off {@link #afterPlannedCutoff} was measured against, epoch
     * milliseconds -- null where there is none.
     *
     * AN OPEN ISSUE HAS TWO DATES: a period start, and a planned cut-off with
     * "now" as its one alternative once the plan has passed. This is the cut-off
     * half of the read, as the read itself saw it -- the instant the caller named
     * where it named one, the issue's stored plan where no other cut-off was in
     * play. It is reported for the same reason the count beside it is: the screen
     * prints "N published after <date>", and a number whose date the caller had to
     * infer is one nobody can check against the form it is standing next to. A
     * cut-off edited on the screen moves this, because it moves what the question
     * is about.
     *
     * NULL IS A REAL ANSWER, not a missing one: an open issue with no planned
     * cut-off has nothing to be after, and a frozen issue has no cut-off left to
     * choose. So the key is always serialised and carries null there -- reporting
     * 0 instead would name an instant in 1970 and the list beside it would be
     * unreadable.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private Long lateAfter;

    /**
     * The members published AFTER {@link #lateAfter}, as of now.
     *
     * The one part of this response that is deliberately NOT answered at
     * {@link #viewedAt}, and it cannot be: read AT a cut-off, the messages
     * published after it are not members, so a list built from that view could
     * only ever be empty. Taken as of now it says both of the things the reader
     * needs at once -- what publishing now would add beyond that cut-off, and what
     * publishing at that cut-off would leave behind.
     *
     * The rows are built exactly as {@link #members} are -- same shape, same
     * titles, same order -- so the two lists can be rendered by one component. The
     * ones that also appear in `members` do so because the as-of-now member list
     * genuinely contains them; which of the two lists shows a row is the reader's
     * decision, not a second membership rule.
     *
     * ALWAYS PRESENT, empty where the question does not arise: no cut-off to be
     * after at all ({@link #lateAfter} null), a cut-off at or ahead of now --
     * nothing can have been published after an instant that has not happened --
     * and a frozen issue, whose contents are what was printed rather than a
     * question about today.
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

    public Long getViewedFrom() {
        return viewedFrom;
    }

    public void setViewedFrom(Long viewedFrom) {
        this.viewedFrom = viewedFrom;
    }

    public List<IssueMemberVo> getMembers() {
        return members;
    }

    public void setMembers(List<IssueMemberVo> members) {
        this.members = members;
    }

    public Long getLateAfter() {
        return lateAfter;
    }

    public void setLateAfter(Long lateAfter) {
        this.lateAfter = lateAfter;
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
