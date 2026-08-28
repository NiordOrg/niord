package org.niord.core.publication.series.vo;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.niord.model.IJsonSerializable;

/**
 * The EDITOR face of a series.
 * 
 * Everything the public VO carries, plus the operational fields: the criteria
 * document, the schedule, the report settings, the cutover switch. None of it
 * is safe for an anonymous caller, and none of it is reachable from the public
 * type because it is not declared there.
 */
public class SystemPublicationSeriesVo extends PublicationSeriesVo {

    /**
     * The row's revision, so a save can say which revision it was composed against.
     *
     * Read-only in the sense that matters: it is filled by the read and COMPARED by
     * the write, never assigned from a body. A client that echoes what it read is
     * told when somebody else has changed the series underneath it; a client that
     * omits it keeps the older last-write-wins behaviour, which is what the
     * administration screens written before this field existed rely on.
     */
    private Integer version;

    private String status;

    private String contentMode;

    private String cadence;

    /**
     * SCHEDULED, UNSCHEDULED or ONE_OFF. Omitted by a client that does not edit
     * it, and an omission leaves the stored kind alone.
     */
    private String kind;

    private String nominalCutoffDay;

    /**
     * Day of the month a MONTHLY or YEARLY series cuts off, 1-31.
     *
     * S-6 requires it for those two cadences, so without it on the VO a monthly
     * or yearly series can never be given one and can never activate.
     */
    private Integer nominalCutoffDayOfMonth;

    /** Month a YEARLY series cuts off in, 1-12. Required by S-6 for YEARLY. */
    private Integer nominalCutoffMonth;

    private String nominalCutoffTime;

    private String numberingScheme;

    private String timeRelation;

    private Boolean aliveAtCutoff;

    private Date firstIssueStartsAt;

    /** The criteria document, verbatim. */
    private Object criteria;

    private String domainId;

    private List<String> languages = new ArrayList<>();

    private String reportId;

    private String pageSize;

    private String pageOrientation;

    private Boolean mapThumbnails;

    private String messageSortBy;

    private String messageSortOrder;

    private String messagePublication;

    private String releaseMode;

    /** Where the cut-off falls by default at publish: RELEASE_MOMENT, PERIOD_START or PERIOD_END. */
    private String cutoffDefault;

    private String nextIssueCreation;

    /** The per-series cutover switch. */
    private String publicAuthority;

    private String legacyTemplateId;

    private String importSource;

    private Map<String, Object> reportParams = new LinkedHashMap<>();

    /** DERIVED, never stored. */
    /**
     * Whether issues carry a separate file per language.
     *
     * Read by IssuePublicationMapping, so it changes what an issue produces --
     * which is why it belongs on the VO rather than staying importer-only.
     */
    private Boolean languageSpecific;

    private boolean dormant;

    /**
     * How many issues of this series have been released -- PUBLISHED plus RETIRED.
     *
     * READ-ONLY and DERIVED: it is a count of other rows, not a column, and a
     * value arriving in a save is ignored. It is on the wire because S-18 turns on
     * it: once an issue is out, the citation channel is fixed for the life of the
     * series, and the editor has to disable the control rather than let an admin
     * type a change the save will refuse. Zero and absent must not look alike to
     * that screen, so it is populated on every series read rather than only where
     * it is non-zero.
     */
    private Integer publishedIssueCount;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getContentMode() {
        return contentMode;
    }

    public void setContentMode(String contentMode) {
        this.contentMode = contentMode;
    }

    public String getCadence() {
        return cadence;
    }

    public void setCadence(String cadence) {
        this.cadence = cadence;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getNominalCutoffDay() {
        return nominalCutoffDay;
    }

    public void setNominalCutoffDay(String nominalCutoffDay) {
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


    public String getNumberingScheme() {
        return numberingScheme;
    }

    public void setNumberingScheme(String numberingScheme) {
        this.numberingScheme = numberingScheme;
    }

    public String getTimeRelation() {
        return timeRelation;
    }

    public void setTimeRelation(String timeRelation) {
        this.timeRelation = timeRelation;
    }

    public Boolean getAliveAtCutoff() {
        return aliveAtCutoff;
    }

    public void setAliveAtCutoff(Boolean aliveAtCutoff) {
        this.aliveAtCutoff = aliveAtCutoff;
    }

    public Date getFirstIssueStartsAt() {
        return firstIssueStartsAt;
    }

    public void setFirstIssueStartsAt(Date firstIssueStartsAt) {
        this.firstIssueStartsAt = firstIssueStartsAt;
    }

    public Object getCriteria() {
        return criteria;
    }

    public void setCriteria(Object criteria) {
        this.criteria = criteria;
    }

    public String getDomainId() {
        return domainId;
    }

    public void setDomainId(String domainId) {
        this.domainId = domainId;
    }

    public List<String> getLanguages() {
        return languages;
    }

    public void setLanguages(List<String> languages) {
        this.languages = languages;
    }

    public String getReportId() {
        return reportId;
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    public String getPageSize() {
        return pageSize;
    }

    public void setPageSize(String pageSize) {
        this.pageSize = pageSize;
    }

    public String getPageOrientation() {
        return pageOrientation;
    }

    public void setPageOrientation(String pageOrientation) {
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

    public String getMessageSortOrder() {
        return messageSortOrder;
    }

    public void setMessageSortOrder(String messageSortOrder) {
        this.messageSortOrder = messageSortOrder;
    }

    public String getMessagePublication() {
        return messagePublication;
    }

    public void setMessagePublication(String messagePublication) {
        this.messagePublication = messagePublication;
    }

    public String getCutoffDefault() {
        return cutoffDefault;
    }

    public void setCutoffDefault(String cutoffDefault) {
        this.cutoffDefault = cutoffDefault;
    }

    public String getReleaseMode() {
        return releaseMode;
    }

    public void setReleaseMode(String releaseMode) {
        this.releaseMode = releaseMode;
    }

    public String getNextIssueCreation() {
        return nextIssueCreation;
    }

    public void setNextIssueCreation(String nextIssueCreation) {
        this.nextIssueCreation = nextIssueCreation;
    }

    public String getPublicAuthority() {
        return publicAuthority;
    }

    public void setPublicAuthority(String publicAuthority) {
        this.publicAuthority = publicAuthority;
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

    public Map<String, Object> getReportParams() {
        return reportParams;
    }

    public void setReportParams(Map<String, Object> reportParams) {
        this.reportParams = reportParams;
    }

    public Boolean getLanguageSpecific() {
        return languageSpecific;
    }

    public void setLanguageSpecific(Boolean languageSpecific) {
        this.languageSpecific = languageSpecific;
    }

    public boolean isDormant() {
        return dormant;
    }

    public void setDormant(boolean dormant) {
        this.dormant = dormant;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Integer getPublishedIssueCount() {
        return publishedIssueCount;
    }

    public void setPublishedIssueCount(Integer publishedIssueCount) {
        this.publishedIssueCount = publishedIssueCount;
    }

}
