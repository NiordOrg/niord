package org.niord.core.publication.series.vo;

import java.util.Date;
import org.niord.model.IJsonSerializable;

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

}
