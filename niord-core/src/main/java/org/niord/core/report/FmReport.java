/*
 * Copyright 2017 Danish Maritime Authority.
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
package org.niord.core.report;

import org.niord.core.db.JpaPropertiesAttributeConverter;
import org.niord.core.domain.Domain;
import org.niord.core.model.BaseEntity;
import org.niord.core.report.vo.FmReportVo;
import org.niord.model.DataFilter;

import jakarta.persistence.Cacheable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Defines a freemarker report that generates PDF for a list of messages
 */
@Entity
@Cacheable
@NamedQueries({
        @NamedQuery(name="FmReport.findByReportId",
                query="SELECT r FROM FmReport r where r.reportId = :reportId"),
        @NamedQuery(name="FmReport.findAll",
                query="SELECT r FROM FmReport r order by r.sortOrder ASC"),
        @NamedQuery(name="FmReport.findPublicReports",
                query="SELECT r FROM FmReport r where r.domains is empty order by r.sortOrder ASC"),
        @NamedQuery(name="FmReport.findReportsByDomain",
                query="SELECT r FROM FmReport r join r.domains d where d = :domain  order by r.sortOrder ASC")
})
@SuppressWarnings("unused")
public class FmReport extends BaseEntity<Integer> implements Comparable<FmReport> {

    @Column(unique = true, nullable = false)
    String reportId;

    Integer sortOrder;

    @NotNull
    String name;

    @NotNull
    String templatePath;

    @ManyToMany
    List<Domain> domains = new ArrayList<>();

    /**
     * Fixed report-specific properties
     **/
    @Column(name = "properties", columnDefinition = "TEXT")
    @Convert(converter = JpaPropertiesAttributeConverter.class)
    Map<String, Object> properties = new HashMap<>();

    /**
     * User input parameters
     **/
    @Column(name = "params", columnDefinition = "TEXT")
    @Convert(converter = JpaPropertiesAttributeConverter.class)
    Map<String, Object> params = new HashMap<>();

    /**
     * Constructor
     */
    public FmReport() {
    }


    /**
     * Constructor
     */
    public FmReport(FmReportVo report) {
        updateReport(report);
    }


    /**
     * Updates this report from the value object
     **/
    public void updateReport(FmReportVo report) {
        this.reportId = report.getReportId();
        this.sortOrder = report.getSortOrder();
        this.name = report.getName();
        this.templatePath = report.getTemplatePath();
        this.properties.putAll(report.getProperties());
        this.params.putAll(report.getParams());
        if (report.getDomains() != null) {
            this.domains = report.getDomains().stream()
                    .map(Domain::new)
                    .collect(Collectors.toList());
        }
    }


    /** Converts this entity to a value object */
    public FmReportVo toVo() {
        return toVo(DataFilter.get());
    }


    /** Converts this entity to a value object */
    public FmReportVo toVo(DataFilter filter) {
        FmReportVo report = new FmReportVo();
        report.setReportId(reportId);
        report.setSortOrder(sortOrder);
        report.setName(name);
        report.setTemplatePath(templatePath);
        report.getProperties().putAll(properties);
        report.getParams().putAll(params);
        if (filter.includeField("domains")) {
            report.setDomains(domains.stream()
                .map(Domain::toVo)
                .collect(Collectors.toList()));
        }
        return report;
    }


    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("all")
    public int compareTo(FmReport t) {
        return t == null
                ? -1
                : Integer.compare(nonNullSortOrder(), t.nonNullSortOrder());
    }


    private int nonNullSortOrder() {
        return sortOrder == null ? Integer.MAX_VALUE : sortOrder;
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getReportId() {
        return reportId;
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTemplatePath() {
        return templatePath;
    }

    public void setTemplatePath(String templatePath) {
        this.templatePath = templatePath;
    }

    public List<Domain> getDomains() {
        return domains;
    }

    public void setDomains(List<Domain> domains) {
        this.domains = domains;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }
}
