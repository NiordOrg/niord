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

import jakarta.validation.constraints.NotNull;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import org.niord.core.publication.series.vo.PublicationIssueVo;
import org.niord.core.publication.series.vo.PublicationIssueDescVo;
import org.niord.core.publication.series.vo.SystemPublicationIssueDescVo;
import org.niord.core.publication.series.vo.SystemPublicationIssueVo;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.niord.core.db.JpaPropertiesAttributeConverter;
import org.niord.core.model.VersionedEntity;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.criteria.JpaCriteriaAttributeConverter;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.user.User;
import org.niord.model.ILocalizable;

/**
 * One concrete udgave: its interval, its stamped cut-off, its public window, and its frozen
 * snapshot header.
 *
 * Identity comes from VersionedEntity and nothing else. Every id in this
 * system is drawn from one shared sequence row, and inheriting the base class IS the whole
 * contract. Giving this table its own id generator would break that silently, for this
 * table alone. EntityContractTest.noEntityBringsItsOwnIdGenerator() enforces it.
 */
@Entity
// NAMED ONLY WHERE THE SAME QUESTION IS ASKED TWICE.
//
// A named query is parsed and validated when the persistence unit boots, so a
// typo fails the deploy rather than the request -- but that is not why these
// three are here. They are here because each was written out verbatim in two
// unrelated classes, and two copies of an ORDER BY are two answers waiting to
// disagree: "the newest issue of this series" decides which issue the draft
// builder chains from AND which one the shape derivation reads its predecessor
// from, and a divergence there produces an overlapping interval nobody sees
// until publish. Queries asked from one place stay where they are read.
@NamedQueries({
        @NamedQuery(name = "PublicationIssue.findBySeriesNewestFirst",
                query = "SELECT i FROM PublicationIssue i WHERE i.series = :series "
                        + "ORDER BY COALESCE(i.cutoffStampedAt, i.intervalTo) DESC, i.publicId DESC"),
        // "Has anything of this series ever been released" -- RETIRED counts,
        // because it was published and the citations it wrote are still out there.
        @NamedQuery(name = "PublicationIssue.countReleasedBySeries",
                query = "SELECT COUNT(i) FROM PublicationIssue i WHERE i.series = :series "
                        + "AND i.status <> :openStatus"),
        @NamedQuery(name = "PublicationIssue.countReleasedPerSeries",
                query = "SELECT i.series.seriesId, COUNT(i) FROM PublicationIssue i "
                        + "WHERE i.status <> :openStatus GROUP BY i.series.seriesId")
})
// The indexes are DECLARED here because the schema has them, not to make the
// schema have them -- Flyway owns the DDL and V12 creates these two. Naming them
// on the entity is what lets a reader see the access paths this table is tuned
// for without opening the migrations; leaving them off would make the mapping
// describe a database nobody has, which is the same defect in the other
// direction. The sizing rationale, and why PublicationSeries gets none, is in
// V12.
@Table(indexes = {
        @Index(name = "publication_issue_series_status_k", columnList = "series_id, status"),
        @Index(name = "publication_issue_status_public_from_k", columnList = "status, publicFrom")
})
public class PublicationIssue extends VersionedEntity<Integer> implements ILocalizable<PublicationIssueDesc> {

    @NotNull
    @Column(length = 36, nullable = false, unique = true)
    private String publicId;

    @Column(length = 36, unique = true)
    private String legacyPublicationId;

    @ManyToOne(optional = false)
    @NotNull
    @JoinColumn(nullable = false)
    private PublicationSeries series;

    @NotNull
    @Column(length = 128, nullable = false)
    private String repoPath;

    @Temporal(TemporalType.TIMESTAMP)
    private Date intervalFrom;

    @Temporal(TemporalType.TIMESTAMP)
    private Date intervalTo;

    @Enumerated(EnumType.STRING)
    private IntervalBoundSource intervalFromSource;

    /**
     * Where the CLOSE of the content period came from -- the twin of
     * intervalFromSource, and the same vocabulary.
     *
     * Typed, not a free string. Every writer already stored an
     * IntervalBoundSource name and every reader parsed one back out, so the
     * String only ever meant that a typo would survive the write and fail at the
     * read instead. The column stays varchar and the enum is persisted by name,
     * so the stored values are byte-identical and no migration is involved; the
     * columnDefinition pins that, because the schema-generation default for an
     * enum-typed field on MySQL is a native ENUM column, which this column is not
     * and must not become -- a native enum needs an ALTER TABLE before a
     * constant can be added.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 255, columnDefinition = "varchar(255)")
    private IntervalBoundSource intervalToSource;

    @Temporal(TemporalType.TIMESTAMP)
    private Date cutoffStampedAt;

    @Column(length = 255)
    private String cutoffSource;

    @Column(nullable = false)
    private boolean cutoffReconstructed = false;

    @Temporal(TemporalType.TIMESTAMP)
    private Date publicFrom;

    @Temporal(TemporalType.TIMESTAMP)
    private Date publicTo;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(nullable = false)
    private PublicWindowSource publicWindowSource = PublicWindowSource.DERIVED;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(nullable = false)
    private IssueStatus status = IssueStatus.OPEN;

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

    @NotNull
    @Column(nullable = false)
    private Integer memberCount = 0;

    /**
     * The time relation this issue's membership was frozen under.
     *
     * Typed for the same reason as intervalToSource, and stored the same way:
     * varchar holding the constant's name, pinned by columnDefinition so schema
     * generation does not turn it into a native ENUM. It is a SNAPSHOT of what
     * the series said at publish, deliberately not read back off the series --
     * which is why it is a column here at all.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 255, columnDefinition = "varchar(255)")
    private TimeRelation snapshotTimeRelation;

    private Boolean snapshotAliveAtCutoff;

    @Temporal(TemporalType.TIMESTAMP)
    private Date snapshotIntervalFrom;

    @Column(columnDefinition = "TEXT")
    private String snapshotSeriesIds;

    // The rest of the resolved operands, recorded so a published issue can still
    // say what it selected on. The criteria snapshot holds the DOCUMENT; these
    // hold what it resolved to, and the two answer different questions -- a
    // domain node expands to a series set that the document never spells out, and
    // an area MRN that has since been renamed is only recoverable from what was
    // written down at the time.
    @Column(length = 255)
    private String snapshotMainTypes;

    @Column(columnDefinition = "TEXT")
    private String snapshotAreaIds;

    @Column(columnDefinition = "TEXT")
    private String snapshotCategoryIds;

    @Column(columnDefinition = "TEXT")
    private String snapshotChartNumbers;

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

    // Initialised for the same reason as PublicationSeries.reportParams: the
    // converter hands back an empty map for a null column, so a loaded entity
    // always has one. Leaving it null here would mean a caller could tell a
    // constructed issue from a loaded one, which is the difference that turned
    // the equivalent field on the series into a 500 on every create.
    @Column(columnDefinition = "TEXT")
    @Convert(converter = JpaPropertiesAttributeConverter.class)
    private Map<String, Object> reportParams = new LinkedHashMap<>();

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

    public IntervalBoundSource getIntervalToSource() {
        return intervalToSource;
    }

    public void setIntervalToSource(IntervalBoundSource intervalToSource) {
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

    public TimeRelation getSnapshotTimeRelation() {
        return snapshotTimeRelation;
    }

    public void setSnapshotTimeRelation(TimeRelation snapshotTimeRelation) {
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

    public String getSnapshotMainTypes() {
        return snapshotMainTypes;
    }

    public void setSnapshotMainTypes(String snapshotMainTypes) {
        this.snapshotMainTypes = snapshotMainTypes;
    }

    public String getSnapshotAreaIds() {
        return snapshotAreaIds;
    }

    public void setSnapshotAreaIds(String snapshotAreaIds) {
        this.snapshotAreaIds = snapshotAreaIds;
    }

    public String getSnapshotCategoryIds() {
        return snapshotCategoryIds;
    }

    public void setSnapshotCategoryIds(String snapshotCategoryIds) {
        this.snapshotCategoryIds = snapshotCategoryIds;
    }

    public String getSnapshotChartNumbers() {
        return snapshotChartNumbers;
    }

    public void setSnapshotChartNumbers(String snapshotChartNumbers) {
        this.snapshotChartNumbers = snapshotChartNumbers;
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


    /**
     * Converts to a value object. Same split as the series: the operational
     * fields exist only on the system type.
     *
     * The interval, the stamp and the snapshot header are all editor-tier. Out
     * of context they are worse than absent -- a public reader seeing
     * snapshotIntervalFrom would reasonably take it for the issue period.
     */
    public <V extends PublicationIssueVo> V toVo(Class<V> clz) {
        V vo;
        try {
            vo = clz.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("cannot instantiate " + clz, e);
        }

        vo.setPublicId(publicId);
        vo.setSeriesId(series == null ? null : series.getSeriesId());
        vo.setCreated(getCreated());
        vo.setUpdated(getUpdated());
        vo.setPublicFrom(publicFrom);
        vo.setPublicTo(publicTo);
        vo.setWeek(week);
        vo.setWeekTo(weekTo);
        vo.setYear(year);
        vo.setEdition(edition);

        // The editor shape carries the document-management fields; the public one
        // does not. A public reader has no use for where a file came from, and the
        // fields only make sense next to the buttons that change them.
        boolean editorShape = vo instanceof SystemPublicationIssueVo;
        for (PublicationIssueDesc d : getDescs()) {
            PublicationIssueDescVo dv = editorShape
                    ? new SystemPublicationIssueDescVo()
                    : new PublicationIssueDescVo();
            dv.setLang(d.getLang());
            dv.setName(d.getName());
            dv.setFileName(d.getFileName());
            dv.setLink(d.getLink());
            dv.setMessageReferenceFormat(d.getMessageReferenceFormat());
            if (dv instanceof SystemPublicationIssueDescVo sysDesc) {
                sysDesc.setHref(IssuePublicationMapping.linkOf(d));
                sysDesc.setFileSource(d.getFileSource() == null ? null : d.getFileSource().name());
                sysDesc.setFileSourceSticky(d.isFileSourceSticky());
            }
            vo.getDescs().add(dv);
        }

        if (vo instanceof SystemPublicationIssueVo sys) {
            // See the series' toVo: the revision travels with the read so the
            // write that follows can say which one it was composed against.
            sys.setVersion(getVersion());
            sys.setStatus(status == null ? null : status.name());
            sys.setIntervalFrom(intervalFrom);
            sys.setIntervalTo(intervalTo);
            sys.setIntervalFromSource(intervalFromSource == null ? null : intervalFromSource.name());
            sys.setIntervalToSource(intervalToSource == null ? null : intervalToSource.name());
            sys.setCutoffStampedAt(cutoffStampedAt);

            // The coalesce is emitted rather than left to each client to repeat.
            // A synthesized row sits in the same list and has no columns to
            // coalesce, so both kinds have to answer "when did this period close"
            // in one field, or the merged list cannot be sorted as one sequence.
            Date effective = effectiveCutoff();
            sys.setEffectiveCutoff(effective);
            sys.setSortKey(effective == null ? null : effective.getTime());

            // A real row computes to its stored status. MISSING and UPCOMING
            // describe rows with no entity, and only the synthesizer sets those.
            sys.setComputedStatus(status == null ? null : status.name());
            sys.setCutoffReconstructed(cutoffReconstructed);
            sys.setCutoffSource(cutoffSource);
            sys.setPublishedAt(publishedAt);
            sys.setPublishedBy(publishedBy == null ? null : publishedBy.getUsername());
            sys.setRetiredAt(retiredAt);
            sys.setRetiredReason(retiredReason);
            // TRI-STATE ON THE WIRE, while the column stays INTEGER NOT NULL.
            // A NO_MEMBERSHIP publication has no membership semantics at all --
            // it is a PDF or a link, and nothing was ever resolved for it. Emitting
            // its column value of 0 says "resolved, and empty", which is precisely
            // the confusion membershipProvenance exists to prevent, reintroduced one
            // field along. Invariant I-11 compares the COLUMN and is unaffected.
            sys.setMemberCount(membershipProvenance == MembershipProvenance.NO_MEMBERSHIP
                    ? null : memberCount);
            sys.setMembershipProvenance(membershipProvenance == null ? null : membershipProvenance.name());
            sys.setSnapshotIntervalFrom(snapshotIntervalFrom);
            sys.setSnapshotTimeRelation(snapshotTimeRelation == null ? null : snapshotTimeRelation.name());
            sys.setSupersedesPublicId(supersedes == null ? null : supersedes.getPublicId());
            sys.setLegacyPublicationId(legacyPublicationId);
            sys.setRepoPath(repoPath);
            sys.setCriteriaOverride(criteriaOverride);
            // Derived rather than "is the override set": a published issue answers
            // from the snapshot it went out with, and an override equal to the
            // series' criteria is no deviation. Both are comparisons the client
            // should not be asked to make.
            sys.setCriteriaOverridden(EffectiveCriteria.isOverridden(this));
            sys.setSeriesCriteria(series == null ? null : series.getCriteria());
        }
        return vo;
    }

    /**
     * When this period actually closed: the stamped cut-off where there is one,
     * the nominal bound where there is not.
     *
     * The one place the coalesce is written. It decides list order, interval
     * bounds and gap arithmetic alike, and a second copy that drifted would put
     * a row in one position while bounding a gap at another.
     */
    public Date effectiveCutoff() {
        return cutoffStampedAt != null ? cutoffStampedAt : intervalTo;
    }

}
