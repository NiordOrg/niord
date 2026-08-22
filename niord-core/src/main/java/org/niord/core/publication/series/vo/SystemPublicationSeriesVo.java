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

    private String status;

    private String contentMode;

    private String cadence;

    private String nominalCutoffDay;

    private String nominalCutoffTime;

    private String nominalCutoffTimeZone;

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

    private String nextIssueCreation;

    /** The per-series cutover switch. */
    private String publicAuthority;

    private String legacyTemplateId;

    private String importSource;

    private Map<String, Object> reportParams = new LinkedHashMap<>();

    /** DERIVED, never stored. */
    private boolean dormant;

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

    public String getNominalCutoffDay() {
        return nominalCutoffDay;
    }

    public void setNominalCutoffDay(String nominalCutoffDay) {
        this.nominalCutoffDay = nominalCutoffDay;
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

    public boolean isDormant() {
        return dormant;
    }

    public void setDormant(boolean dormant) {
        this.dormant = dormant;
    }

}
