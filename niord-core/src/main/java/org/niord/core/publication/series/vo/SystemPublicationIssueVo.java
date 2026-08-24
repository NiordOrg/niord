package org.niord.core.publication.series.vo;

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

    private String status;

    private Date intervalFrom;

    private Date intervalTo;

    private String intervalFromSource;

    private Date cutoffStampedAt;

    private boolean cutoffReconstructed;

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

    /** Computed, never stored. */
    private boolean previewStale;

    private String intervalToSource;

    /** Derived: cutoffStampedAt ?? intervalTo. Emitted so no client re-implements the coalesce. */
    private Date effectiveCutoff;

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

    public boolean isPreviewStale() {
        return previewStale;
    }

    public void setPreviewStale(boolean previewStale) {
        this.previewStale = previewStale;
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

    public String getFollowingPublicId() {
        return followingPublicId;
    }

    public void setFollowingPublicId(String followingPublicId) {
        this.followingPublicId = followingPublicId;
    }

}
