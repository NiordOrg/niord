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
package org.niord.core.report.vo;

import org.niord.core.domain.vo.DomainVo;
import org.niord.model.IJsonSerializable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Defines a freemarker report that generates PDF for a list of messages
 */
@SuppressWarnings("unused")
public class FmReportVo implements IJsonSerializable {

    String reportId;
    Integer sortOrder;
    String name;
    String templatePath;
    List<DomainVo> domains;
    Map<String, Object> properties = new HashMap<>();
    Map<String, Object> params = new HashMap<>();

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

    public List<DomainVo> getDomains() {
        return domains;
    }

    public void setDomains(List<DomainVo> domains) {
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
