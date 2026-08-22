package org.niord.core.publication.series;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.niord.core.db.JpaPropertiesAttributeConverter;
import org.niord.core.domain.Domain;
import org.niord.core.model.VersionedEntity;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.criteria.JpaCriteriaAttributeConverter;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.model.ILocalizable;
import org.niord.model.search.PagedSearchParamsVo;

/**
 * The definition of a publication: what an issue of it IS, how often, over which query, and
 * rendered by which report. Replaces the legacy template publication.
 *
 * Identity comes from VersionedEntity and nothing else. Every id in this
 * system is drawn from one shared sequence row, and inheriting the base class IS the whole
 * contract. Giving this table its own id generator would break that silently, for this
 * table alone. EntityIdentityTest enforces it.
 */
@Entity
public class PublicationSeries extends VersionedEntity<Integer> implements ILocalizable<PublicationSeriesDesc> {

    @Column(length = 64, nullable = false, unique = true)
    private String seriesId;

    @Column(length = 36, unique = true)
    private String legacyTemplateId;

    @Column(length = 255)
    private String importSource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeriesStatus status = SeriesStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContentMode contentMode = ContentMode.GENERATED_FROM_QUERY;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeriesCadence cadence = SeriesCadence.NONE;

    @Enumerated(EnumType.STRING)
    private CutoffDay nominalCutoffDay;

    private Integer nominalCutoffDayOfMonth;

    private Integer nominalCutoffMonth;

    @Column(length = 5)
    private String nominalCutoffTime;

    @Column(length = 64)
    private String nominalCutoffTimeZone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NumberingScheme numberingScheme = NumberingScheme.NONE;

    @Temporal(TemporalType.TIMESTAMP)
    private Date firstIssueStartsAt;

    @Enumerated(EnumType.STRING)
    private TimeRelation timeRelation;

    private Boolean aliveAtCutoff;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = JpaCriteriaAttributeConverter.class)
    private IssueCriteriaVo criteria;

    @ManyToOne(optional = false)
    @JoinColumn(nullable = false)
    private PublicationCategory category;

    @ManyToOne
    private Domain domain;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessagePublication messagePublication = MessagePublication.NONE;

    private Integer sortOrder;

    @Column(length = 64)
    private String reportId;

    @Enumerated(EnumType.STRING)
    private PageSize pageSize;

    @Enumerated(EnumType.STRING)
    private PageOrientation pageOrientation;

    private Boolean mapThumbnails;

    @Column(length = 32)
    private String messageSortBy;

    @Enumerated(EnumType.STRING)
    private PagedSearchParamsVo.SortOrder messageSortOrder;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = JpaPropertiesAttributeConverter.class)
    private Map<String, Object> reportParams;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReleaseMode releaseMode = ReleaseMode.MANUAL_GATE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NextIssueCreation nextIssueCreation = NextIssueCreation.AUTO_ON_PUBLISH;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PublicAuthority publicAuthority = PublicAuthority.LEGACY;

    @Column(nullable = false)
    private boolean languageSpecific = true;

    public String getSeriesId() {
        return seriesId;
    }

    public void setSeriesId(String seriesId) {
        this.seriesId = seriesId;
    }

    public String getLegacyTemplateId() {
        return legacyTemplateId;
    }

    public void setLegacyTemplateId(String legacyTemplateId) {
        this.legacyTemplateId = legacyTemplateId;
    }

    public String getImportSource() {
        return importSource;
    }

    public void setImportSource(String importSource) {
        this.importSource = importSource;
    }

    public SeriesStatus getStatus() {
        return status;
    }

    public void setStatus(SeriesStatus status) {
        this.status = status;
    }

    public ContentMode getContentMode() {
        return contentMode;
    }

    public void setContentMode(ContentMode contentMode) {
        this.contentMode = contentMode;
    }

    public SeriesCadence getCadence() {
        return cadence;
    }

    public void setCadence(SeriesCadence cadence) {
        this.cadence = cadence;
    }

    public CutoffDay getNominalCutoffDay() {
        return nominalCutoffDay;
    }

    public void setNominalCutoffDay(CutoffDay nominalCutoffDay) {
        this.nominalCutoffDay = nominalCutoffDay;
    }

    public Integer getNominalCutoffDayOfMonth() {
        return nominalCutoffDayOfMonth;
    }

    public void setNominalCutoffDayOfMonth(Integer nominalCutoffDayOfMonth) {
        this.nominalCutoffDayOfMonth = nominalCutoffDayOfMonth;
    }

    public Integer getNominalCutoffMonth() {
        return nominalCutoffMonth;
    }

    public void setNominalCutoffMonth(Integer nominalCutoffMonth) {
        this.nominalCutoffMonth = nominalCutoffMonth;
    }

    public String getNominalCutoffTime() {
        return nominalCutoffTime;
    }

    public void setNominalCutoffTime(String nominalCutoffTime) {
        this.nominalCutoffTime = nominalCutoffTime;
    }

    public String getNominalCutoffTimeZone() {
        return nominalCutoffTimeZone;
    }

    public void setNominalCutoffTimeZone(String nominalCutoffTimeZone) {
        this.nominalCutoffTimeZone = nominalCutoffTimeZone;
    }

    public NumberingScheme getNumberingScheme() {
        return numberingScheme;
    }

    public void setNumberingScheme(NumberingScheme numberingScheme) {
        this.numberingScheme = numberingScheme;
    }

    public Date getFirstIssueStartsAt() {
        return firstIssueStartsAt;
    }

    public void setFirstIssueStartsAt(Date firstIssueStartsAt) {
        this.firstIssueStartsAt = firstIssueStartsAt;
    }

    public TimeRelation getTimeRelation() {
        return timeRelation;
    }

    public void setTimeRelation(TimeRelation timeRelation) {
        this.timeRelation = timeRelation;
    }

    public Boolean getAliveAtCutoff() {
        return aliveAtCutoff;
    }

    public void setAliveAtCutoff(Boolean aliveAtCutoff) {
        this.aliveAtCutoff = aliveAtCutoff;
    }

    public IssueCriteriaVo getCriteria() {
        return criteria;
    }

    public void setCriteria(IssueCriteriaVo criteria) {
        this.criteria = criteria;
    }

    public PublicationCategory getCategory() {
        return category;
    }

    public void setCategory(PublicationCategory category) {
        this.category = category;
    }

    public Domain getDomain() {
        return domain;
    }

    public void setDomain(Domain domain) {
        this.domain = domain;
    }

    public MessagePublication getMessagePublication() {
        return messagePublication;
    }

    public void setMessagePublication(MessagePublication messagePublication) {
        this.messagePublication = messagePublication;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getReportId() {
        return reportId;
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    public PageSize getPageSize() {
        return pageSize;
    }

    public void setPageSize(PageSize pageSize) {
        this.pageSize = pageSize;
    }

    public PageOrientation getPageOrientation() {
        return pageOrientation;
    }

    public void setPageOrientation(PageOrientation pageOrientation) {
        this.pageOrientation = pageOrientation;
    }

    public Boolean getMapThumbnails() {
        return mapThumbnails;
    }

    public void setMapThumbnails(Boolean mapThumbnails) {
        this.mapThumbnails = mapThumbnails;
    }

    public String getMessageSortBy() {
        return messageSortBy;
    }

    public void setMessageSortBy(String messageSortBy) {
        this.messageSortBy = messageSortBy;
    }

    public PagedSearchParamsVo.SortOrder getMessageSortOrder() {
        return messageSortOrder;
    }

    public void setMessageSortOrder(PagedSearchParamsVo.SortOrder messageSortOrder) {
        this.messageSortOrder = messageSortOrder;
    }

    public Map<String, Object> getReportParams() {
        return reportParams;
    }

    public void setReportParams(Map<String, Object> reportParams) {
        this.reportParams = reportParams;
    }

    public ReleaseMode getReleaseMode() {
        return releaseMode;
    }

    public void setReleaseMode(ReleaseMode releaseMode) {
        this.releaseMode = releaseMode;
    }

    public NextIssueCreation getNextIssueCreation() {
        return nextIssueCreation;
    }

    public void setNextIssueCreation(NextIssueCreation nextIssueCreation) {
        this.nextIssueCreation = nextIssueCreation;
    }

    public PublicAuthority getPublicAuthority() {
        return publicAuthority;
    }

    public void setPublicAuthority(PublicAuthority publicAuthority) {
        this.publicAuthority = publicAuthority;
    }

    public boolean isLanguageSpecific() {
        return languageSpecific;
    }

    public void setLanguageSpecific(boolean languageSpecific) {
        this.languageSpecific = languageSpecific;
    }

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "entity", orphanRemoval = true)
    private List<PublicationSeriesDesc> descs = new ArrayList<>();

    @Override
    public List<PublicationSeriesDesc> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<PublicationSeriesDesc> descs) {
        this.descs = descs;
    }

    /** Creates and attaches a desc for the given language. */
    public PublicationSeriesDesc createDesc(String lang) {
        PublicationSeriesDesc desc = new PublicationSeriesDesc();
        desc.setLang(lang);
        desc.setEntity(this);
        getDescs().add(desc);
        return desc;
    }

    /**
     * The configured language list, ordered. DECLARED, never inferred from which desc
     * rows happen to exist: "this series has no Danish name yet" and "this series is not
     * published in Danish" are different facts.
     */
    @ElementCollection
    @OrderColumn(name = "indexNo")
    private List<String> languages = new ArrayList<>();

    public List<String> getLanguages() {
        return languages;
    }

    public void setLanguages(List<String> languages) {
        this.languages = languages;
    }

}
