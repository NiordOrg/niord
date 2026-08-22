package org.niord.core.publication.series;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.niord.core.db.JpaPropertiesAttributeConverter;
import org.niord.core.model.VersionedEntity;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.criteria.JpaCriteriaAttributeConverter;
import org.niord.core.user.User;
import org.niord.model.ILocalizable;

/**
 * One concrete udgave: its interval, its stamped cut-off, its public window, and its frozen
 * snapshot header.
 *
 * Identity comes from VersionedEntity and nothing else. Every id in this
 * system is drawn from one shared sequence row, and inheriting the base class IS the whole
 * contract. Giving this table its own id generator would break that silently, for this
 * table alone. EntityIdentityTest enforces it.
 */
@Entity
public class PublicationIssue extends VersionedEntity<Integer> implements ILocalizable<PublicationIssueDesc> {

    @Column(length = 36, nullable = false, unique = true)
    private String publicId;

    @Column(length = 36, unique = true)
    private String legacyPublicationId;

    @ManyToOne(optional = false)
    @JoinColumn(nullable = false)
    private PublicationSeries series;

    @Column(length = 128, nullable = false)
    private String repoPath;

    @Temporal(TemporalType.TIMESTAMP)
    private Date intervalFrom;

    @Temporal(TemporalType.TIMESTAMP)
    private Date intervalTo;

    @Enumerated(EnumType.STRING)
    private IntervalBoundSource intervalFromSource;

    @Column(length = 255)
    private String intervalToSource;

    @Temporal(TemporalType.TIMESTAMP)
    private Date cutoffStampedAt;

    @Column(length = 255)
    private String cutoffSource;

    @Column(nullable = false)
    private boolean cutoffReconstructed;

    @Temporal(TemporalType.TIMESTAMP)
    private Date publicFrom;

    @Temporal(TemporalType.TIMESTAMP)
    private Date publicTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PublicWindowSource publicWindowSource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssueStatus status;

    @Temporal(TemporalType.TIMESTAMP)
    private Date publishedAt;

    @ManyToOne
    private User publishedBy;

    @Temporal(TemporalType.TIMESTAMP)
    private Date retiredAt;

    @ManyToOne
    private User retiredBy;

    @Column(length = 512)
    private String retiredReason;

    @Enumerated(EnumType.STRING)
    private MembershipProvenance membershipProvenance;

    @Column(columnDefinition = "TEXT")
    private String membershipProvenanceNote;

    @Temporal(TemporalType.TIMESTAMP)
    private Date snapshotFrozenAt;

    @Column(nullable = false)
    private Integer memberCount;

    @Column(length = 255)
    private String snapshotTimeRelation;

    private Boolean snapshotAliveAtCutoff;

    @Temporal(TemporalType.TIMESTAMP)
    private Date snapshotIntervalFrom;

    @Column(columnDefinition = "TEXT")
    private String snapshotSeriesIds;

    @Column(length = 255)
    private String snapshotDomainId;

    @Column(length = 32)
    private String snapshotSortBy;

    @Column(length = 255)
    private String snapshotSortOrder;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = JpaCriteriaAttributeConverter.class)
    private IssueCriteriaVo criteriaSnapshot;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = JpaCriteriaAttributeConverter.class)
    private IssueCriteriaVo criteriaOverride;

    private Integer week;

    private Integer weekTo;

    private Integer year;

    @Column(length = 64)
    private String edition;

    @ManyToOne
    private PublicationIssue supersedes;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = JpaPropertiesAttributeConverter.class)
    private Map<String, Object> reportParams;

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    public String getLegacyPublicationId() {
        return legacyPublicationId;
    }

    public void setLegacyPublicationId(String legacyPublicationId) {
        this.legacyPublicationId = legacyPublicationId;
    }

    public PublicationSeries getSeries() {
        return series;
    }

    public void setSeries(PublicationSeries series) {
        this.series = series;
    }

    public String getRepoPath() {
        return repoPath;
    }

    public void setRepoPath(String repoPath) {
        this.repoPath = repoPath;
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

    public IntervalBoundSource getIntervalFromSource() {
        return intervalFromSource;
    }

    public void setIntervalFromSource(IntervalBoundSource intervalFromSource) {
        this.intervalFromSource = intervalFromSource;
    }

    public String getIntervalToSource() {
        return intervalToSource;
    }

    public void setIntervalToSource(String intervalToSource) {
        this.intervalToSource = intervalToSource;
    }

    public Date getCutoffStampedAt() {
        return cutoffStampedAt;
    }

    public void setCutoffStampedAt(Date cutoffStampedAt) {
        this.cutoffStampedAt = cutoffStampedAt;
    }

    public String getCutoffSource() {
        return cutoffSource;
    }

    public void setCutoffSource(String cutoffSource) {
        this.cutoffSource = cutoffSource;
    }

    public boolean isCutoffReconstructed() {
        return cutoffReconstructed;
    }

    public void setCutoffReconstructed(boolean cutoffReconstructed) {
        this.cutoffReconstructed = cutoffReconstructed;
    }

    public Date getPublicFrom() {
        return publicFrom;
    }

    public void setPublicFrom(Date publicFrom) {
        this.publicFrom = publicFrom;
    }

    public Date getPublicTo() {
        return publicTo;
    }

    public void setPublicTo(Date publicTo) {
        this.publicTo = publicTo;
    }

    public PublicWindowSource getPublicWindowSource() {
        return publicWindowSource;
    }

    public void setPublicWindowSource(PublicWindowSource publicWindowSource) {
        this.publicWindowSource = publicWindowSource;
    }

    public IssueStatus getStatus() {
        return status;
    }

    public void setStatus(IssueStatus status) {
        this.status = status;
    }

    public Date getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Date publishedAt) {
        this.publishedAt = publishedAt;
    }

    public User getPublishedBy() {
        return publishedBy;
    }

    public void setPublishedBy(User publishedBy) {
        this.publishedBy = publishedBy;
    }

    public Date getRetiredAt() {
        return retiredAt;
    }

    public void setRetiredAt(Date retiredAt) {
        this.retiredAt = retiredAt;
    }

    public User getRetiredBy() {
        return retiredBy;
    }

    public void setRetiredBy(User retiredBy) {
        this.retiredBy = retiredBy;
    }

    public String getRetiredReason() {
        return retiredReason;
    }

    public void setRetiredReason(String retiredReason) {
        this.retiredReason = retiredReason;
    }

    public MembershipProvenance getMembershipProvenance() {
        return membershipProvenance;
    }

    public void setMembershipProvenance(MembershipProvenance membershipProvenance) {
        this.membershipProvenance = membershipProvenance;
    }

    public String getMembershipProvenanceNote() {
        return membershipProvenanceNote;
    }

    public void setMembershipProvenanceNote(String membershipProvenanceNote) {
        this.membershipProvenanceNote = membershipProvenanceNote;
    }

    public Date getSnapshotFrozenAt() {
        return snapshotFrozenAt;
    }

    public void setSnapshotFrozenAt(Date snapshotFrozenAt) {
        this.snapshotFrozenAt = snapshotFrozenAt;
    }

    public Integer getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(Integer memberCount) {
        this.memberCount = memberCount;
    }

    public String getSnapshotTimeRelation() {
        return snapshotTimeRelation;
    }

    public void setSnapshotTimeRelation(String snapshotTimeRelation) {
        this.snapshotTimeRelation = snapshotTimeRelation;
    }

    public Boolean getSnapshotAliveAtCutoff() {
        return snapshotAliveAtCutoff;
    }

    public void setSnapshotAliveAtCutoff(Boolean snapshotAliveAtCutoff) {
        this.snapshotAliveAtCutoff = snapshotAliveAtCutoff;
    }

    public Date getSnapshotIntervalFrom() {
        return snapshotIntervalFrom;
    }

    public void setSnapshotIntervalFrom(Date snapshotIntervalFrom) {
        this.snapshotIntervalFrom = snapshotIntervalFrom;
    }

    public String getSnapshotSeriesIds() {
        return snapshotSeriesIds;
    }

    public void setSnapshotSeriesIds(String snapshotSeriesIds) {
        this.snapshotSeriesIds = snapshotSeriesIds;
    }

    public String getSnapshotDomainId() {
        return snapshotDomainId;
    }

    public void setSnapshotDomainId(String snapshotDomainId) {
        this.snapshotDomainId = snapshotDomainId;
    }

    public String getSnapshotSortBy() {
        return snapshotSortBy;
    }

    public void setSnapshotSortBy(String snapshotSortBy) {
        this.snapshotSortBy = snapshotSortBy;
    }

    public String getSnapshotSortOrder() {
        return snapshotSortOrder;
    }

    public void setSnapshotSortOrder(String snapshotSortOrder) {
        this.snapshotSortOrder = snapshotSortOrder;
    }

    public IssueCriteriaVo getCriteriaSnapshot() {
        return criteriaSnapshot;
    }

    public void setCriteriaSnapshot(IssueCriteriaVo criteriaSnapshot) {
        this.criteriaSnapshot = criteriaSnapshot;
    }

    public IssueCriteriaVo getCriteriaOverride() {
        return criteriaOverride;
    }

    public void setCriteriaOverride(IssueCriteriaVo criteriaOverride) {
        this.criteriaOverride = criteriaOverride;
    }

    public Integer getWeek() {
        return week;
    }

    public void setWeek(Integer week) {
        this.week = week;
    }

    public Integer getWeekTo() {
        return weekTo;
    }

    public void setWeekTo(Integer weekTo) {
        this.weekTo = weekTo;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public String getEdition() {
        return edition;
    }

    public void setEdition(String edition) {
        this.edition = edition;
    }

    public PublicationIssue getSupersedes() {
        return supersedes;
    }

    public void setSupersedes(PublicationIssue supersedes) {
        this.supersedes = supersedes;
    }

    public Map<String, Object> getReportParams() {
        return reportParams;
    }

    public void setReportParams(Map<String, Object> reportParams) {
        this.reportParams = reportParams;
    }

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "entity", orphanRemoval = true)
    private List<PublicationIssueDesc> descs = new ArrayList<>();

    @Override
    public List<PublicationIssueDesc> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<PublicationIssueDesc> descs) {
        this.descs = descs;
    }

    /** Creates and attaches a desc for the given language. */
    public PublicationIssueDesc createDesc(String lang) {
        PublicationIssueDesc desc = new PublicationIssueDesc();
        desc.setLang(lang);
        desc.setEntity(this);
        getDescs().add(desc);
        return desc;
    }

}
