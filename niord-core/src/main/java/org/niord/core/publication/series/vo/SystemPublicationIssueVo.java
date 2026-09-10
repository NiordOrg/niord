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

import org.niord.core.publication.series.criteria.IssueCriteriaVo;

import java.util.Date;
import java.util.List;

/**
 * The EDITOR face of an issue.
 * 
 * Carries the interval, the stamp, the snapshot header and the provenance --
 * none of which the public needs and some of which would be actively
 * misleading out of context.
 */
public class SystemPublicationIssueVo extends PublicationIssueVo {

    /**
     * The row's revision, so a write can say which revision it was composed
     * against.
     *
     * Filled by the read and COMPARED by the write, never assigned from a body.
     * It also moves when something changes ABOUT the issue that is not stored on
     * it -- a curation writes child rows, and the revision is forced on so two
     * curators working the same open issue collide instead of silently overwriting
     * each other's decisions.
     */
    private Integer version;

    private String status;

    private Date intervalFrom;

    private Date intervalTo;

    private String intervalFromSource;

    private Date cutoffStampedAt;

    private boolean cutoffReconstructed;

    /** Where the cut-off came from: stamped at the release, at a chosen instant, or recovered by the import. */
    private String cutoffSource;

    private Date publishedAt;

    private String publishedBy;

    private Date retiredAt;

    private String retiredReason;

    private Integer memberCount;

    private String membershipProvenance;

    private Date snapshotIntervalFrom;

    private String snapshotTimeRelation;

    private String supersedesPublicId;

    private String legacyPublicationId;

    private String repoPath;

    private String intervalToSource;

    /** Derived: cutoffStampedAt ?? intervalTo. Emitted so no client re-implements the coalesce. */
    private Date effectiveCutoff;

    /**
     * When this edition is EXPECTED to go public, where that is not yet decided.
     *
     * A PROJECTION, NEVER A STORED VALUE, and the distinction is the whole of the
     * pair. publicFrom and publicTo are what the archive holds: the window an
     * edition actually occupied, written by the publish action and by nothing
     * else. An issue nobody has published has neither, because nothing has
     * happened -- and a screen that shows a blank there is telling the truth about
     * the row while being useless to the person planning next week.
     *
     * So the expectation is computed for the list and sent beside the stored pair
     * rather than in place of it. A client renders it as a forecast -- differently
     * from a window that exists -- and a write that echoed it back would be
     * writing a date nothing decided.
     *
     * Filled for an unreleased issue of a cadenced series only: its planned
     * cut-off, and one cadence period on from there. Absent everywhere else,
     * including on every synthesized row.
     */
    private Date expectedPublicFrom;

    /**
     * When this edition is expected to STOP being the current one.
     *
     * Two rows carry it, and for the same reason seen from either end. On an
     * unreleased issue it is its own planned window closing, one cadence period
     * after it opens. On the newest PUBLISHED issue -- the one still current,
     * whose window is open-ended because nothing has superseded it -- it is the
     * instant the unreleased successor is planned to take over, which is what the
     * publish action will cap this window at when that happens.
     *
     * That second case is why it is emitted regardless of the stored publicTo: the
     * current edition's window says "no end recorded", and the answer the screen
     * needs is "until the next one comes out, which is planned for Wednesday".
     */
    private Date expectedPublicTo;

    /** OPEN | PUBLISHED | RETIRED for a real row; MISSING | UPCOMING for a synthesized one. */
    private String computedStatus;

    /**
     * One key space for real and synthesized rows, so the merged list is a single
     * sequence rather than two interleaved ones.
     */
    private Long sortKey;

    /**
     * MISSING or UPCOMING, and ABSENT on a real row rather than null.
     *
     * The absence is IJsonSerializable's doing -- every VO here omits its nulls
     * -- and this field is the one that turns that into a contract: a client
     * spots a pseudo-row by the key being there at all, so a real row must carry
     * no trace of the gap vocabulary. The wire test asserts it in both
     * directions rather than trusting the inherited policy to stay put.
     */
    private String pseudo;

    /** What each configured language would call this period. Pseudo-rows only. */
    private List<PublicationIssueDescVo> suggestedDescs;

    /** The issues either side of a gap. Null at the head or the tail of the run. */
    private String precedingPublicId;

    /**
     * What THIS issue selects, when that is not what its series selects.
     *
     * Absent means it inherits. The raw document rather than a rendered summary,
     * because the only screen that reads it is the admin issue detail, which
     * already resolves criteria labels for the series form and can reuse that.
     */
    private IssueCriteriaVo criteriaOverride;

    /**
     * Whether the issue is selecting something other than its series says.
     *
     * Derived, and NOT the same as criteriaOverride being present. A published
     * issue answers from the snapshot it went out with -- the override may since
     * have been edited or removed -- and an override equal to the series' own
     * criteria is no deviation at all. Both are comparisons a client should not
     * be asked to make.
     */
    private boolean criteriaOverridden;

    /**
     * What the SERIES selects, so the issue screen can compare without a second fetch.
     *
     * Read-only, and projected rather than looked up: an override is only
     * meaningful against the document it deviates from, and fetching the series
     * separately would give the screen a second source that can disagree with the
     * criteriaOverridden flag computed here.
     */
    private IssueCriteriaVo seriesCriteria;

    private String followingPublicId;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getIntervalFrom() {
        return intervalFrom;
    }

    public void setIntervalFrom(Date intervalFrom) {
        this.intervalFrom = intervalFrom;
    }

    public Date getIntervalTo() {
        return intervalTo;
    }

    public void setIntervalTo(Date intervalTo) {
        this.intervalTo = intervalTo;
    }

    public String getIntervalFromSource() {
        return intervalFromSource;
    }

    public void setIntervalFromSource(String intervalFromSource) {
        this.intervalFromSource = intervalFromSource;
    }

    public Date getCutoffStampedAt() {
        return cutoffStampedAt;
    }

    public void setCutoffStampedAt(Date cutoffStampedAt) {
        this.cutoffStampedAt = cutoffStampedAt;
    }

    public boolean isCutoffReconstructed() {
        return cutoffReconstructed;
    }

    public void setCutoffReconstructed(boolean cutoffReconstructed) {
        this.cutoffReconstructed = cutoffReconstructed;
    }

    public Date getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Date publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getPublishedBy() {
        return publishedBy;
    }

    public void setPublishedBy(String publishedBy) {
        this.publishedBy = publishedBy;
    }

    public Date getRetiredAt() {
        return retiredAt;
    }

    public void setRetiredAt(Date retiredAt) {
        this.retiredAt = retiredAt;
    }

    public String getRetiredReason() {
        return retiredReason;
    }

    public void setRetiredReason(String retiredReason) {
        this.retiredReason = retiredReason;
    }

    public Integer getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(Integer memberCount) {
        this.memberCount = memberCount;
    }

    public String getMembershipProvenance() {
        return membershipProvenance;
    }

    public void setMembershipProvenance(String membershipProvenance) {
        this.membershipProvenance = membershipProvenance;
    }

    public Date getSnapshotIntervalFrom() {
        return snapshotIntervalFrom;
    }

    public void setSnapshotIntervalFrom(Date snapshotIntervalFrom) {
        this.snapshotIntervalFrom = snapshotIntervalFrom;
    }

    public String getSnapshotTimeRelation() {
        return snapshotTimeRelation;
    }

    public void setSnapshotTimeRelation(String snapshotTimeRelation) {
        this.snapshotTimeRelation = snapshotTimeRelation;
    }

    public String getSupersedesPublicId() {
        return supersedesPublicId;
    }

    public void setSupersedesPublicId(String supersedesPublicId) {
        this.supersedesPublicId = supersedesPublicId;
    }

    public String getLegacyPublicationId() {
        return legacyPublicationId;
    }

    public void setLegacyPublicationId(String legacyPublicationId) {
        this.legacyPublicationId = legacyPublicationId;
    }

    public String getRepoPath() {
        return repoPath;
    }

    public void setRepoPath(String repoPath) {
        this.repoPath = repoPath;
    }

    public String getIntervalToSource() {
        return intervalToSource;
    }

    public void setIntervalToSource(String intervalToSource) {
        this.intervalToSource = intervalToSource;
    }

    public Date getEffectiveCutoff() {
        return effectiveCutoff;
    }

    public void setEffectiveCutoff(Date effectiveCutoff) {
        this.effectiveCutoff = effectiveCutoff;
    }

    public Date getExpectedPublicFrom() {
        return expectedPublicFrom;
    }

    public void setExpectedPublicFrom(Date expectedPublicFrom) {
        this.expectedPublicFrom = expectedPublicFrom;
    }

    public Date getExpectedPublicTo() {
        return expectedPublicTo;
    }

    public void setExpectedPublicTo(Date expectedPublicTo) {
        this.expectedPublicTo = expectedPublicTo;
    }

    public String getComputedStatus() {
        return computedStatus;
    }

    public void setComputedStatus(String computedStatus) {
        this.computedStatus = computedStatus;
    }

    public Long getSortKey() {
        return sortKey;
    }

    public void setSortKey(Long sortKey) {
        this.sortKey = sortKey;
    }

    public String getPseudo() {
        return pseudo;
    }

    public void setPseudo(String pseudo) {
        this.pseudo = pseudo;
    }

    public List<PublicationIssueDescVo> getSuggestedDescs() {
        return suggestedDescs;
    }

    public void setSuggestedDescs(List<PublicationIssueDescVo> suggestedDescs) {
        this.suggestedDescs = suggestedDescs;
    }

    public String getPrecedingPublicId() {
        return precedingPublicId;
    }

    public void setPrecedingPublicId(String precedingPublicId) {
        this.precedingPublicId = precedingPublicId;
    }

    public IssueCriteriaVo getCriteriaOverride() {
        return criteriaOverride;
    }

    public void setCriteriaOverride(IssueCriteriaVo criteriaOverride) {
        this.criteriaOverride = criteriaOverride;
    }

    public IssueCriteriaVo getSeriesCriteria() {
        return seriesCriteria;
    }

    public void setSeriesCriteria(IssueCriteriaVo seriesCriteria) {
        this.seriesCriteria = seriesCriteria;
    }

    public boolean isCriteriaOverridden() {
        return criteriaOverridden;
    }

    public void setCriteriaOverridden(boolean criteriaOverridden) {
        this.criteriaOverridden = criteriaOverridden;
    }

    public String getFollowingPublicId() {
        return followingPublicId;
    }

    public void setFollowingPublicId(String followingPublicId) {
        this.followingPublicId = followingPublicId;
    }

    public String getCutoffSource() {
        return cutoffSource;
    }

    public void setCutoffSource(String cutoffSource) {
        this.cutoffSource = cutoffSource;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
