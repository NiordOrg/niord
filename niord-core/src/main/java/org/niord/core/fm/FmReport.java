/*
 * Copyright 2016 Danish Maritime Authority.
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
package org.niord.core.fm;

import org.niord.core.db.JpaPropertiesAttributeConverter;
import org.niord.core.domain.Domain;
import org.niord.core.model.BaseEntity;
import org.niord.core.fm.vo.FmReportVo;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.ManyToMany;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Defines a freemarker report that generates PDF for a list of messages
 */
@Entity
@NamedQueries({
        @NamedQuery(name="FmReport.findByReportId",
                query="SELECT r FROM FmReport r where r.reportId = :reportId"),
        @NamedQuery(name="FmReport.findPublicReports",
                query="SELECT r FROM FmReport r where r.domains is empty"),
        @NamedQuery(name="FmReport.findReportsByDomain",
                query="SELECT r FROM FmReport r join r.domains d where d = :domain")
})
@SuppressWarnings("unused")
public class FmReport extends BaseEntity<Integer> implements Comparable<FmReport> {

    @Column(unique = true, nullable = false)
    String reportId;

    @NotNull
    String name;

    @NotNull
    String templatePath;

    @ManyToMany
    List<Domain> domains = new ArrayList<>();

    @Column(name="properties", columnDefinition = "TEXT")
    @Convert(converter = JpaPropertiesAttributeConverter.class)
    Map<String, Object> properties = new HashMap<>();

    /**
     * Constructor
     */
    public FmReport() {
    }

    /** Converts this entity to a value object */
    public FmReportVo toVo() {
        FmReportVo report = new FmReportVo();
        report.setReportId(reportId);
        report.setName(name);
        report.setTemplatePath(templatePath);
        report.setProperties(properties);
        return report;
    }


    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("all")
    public int compareTo(FmReport t) {
        return t == null ? -1 : name.toLowerCase().compareTo(t.getName().toLowerCase());
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
}
