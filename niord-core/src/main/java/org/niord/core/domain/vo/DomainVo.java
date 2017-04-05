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

package org.niord.core.domain.vo;

import org.niord.core.message.vo.SystemMessageSeriesVo;
import org.niord.model.IJsonSerializable;
import org.niord.model.message.AreaVo;
import org.niord.model.message.CategoryVo;

import java.util.List;

/**
 * Represents an application domain
 */
@SuppressWarnings("unused")
public class DomainVo implements IJsonSerializable {

    private String domainId;
    private boolean active;
    private Integer sortOrder;
    private String name;
    private String timeZone;
    private Float lat;
    private Float lon;
    private Integer zoomLevel;
    private String messageSortOrder;
    private List<AreaVo> areas;
    private List<CategoryVo> categories;
    private List<SystemMessageSeriesVo> messageSeries;
    private Boolean firingSchedule;
    private Boolean publish;
    private Boolean atons;
    private Boolean templates;
    private Boolean inKeycloak;

    /** Constructor **/
    public DomainVo() {
    }

    /** Constructor **/
    public DomainVo(String domainId) {
        this.domainId = domainId;
    }

    /** Constructor **/
    public DomainVo(String domainId, boolean active) {
        this(domainId);
        this.active = active;
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getDomainId() {
        return domainId;
    }

    public void setDomainId(String domainId) {
        this.domainId = domainId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
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

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public Float getLat() {
        return lat;
    }

    public void setLat(Float lat) {
        this.lat = lat;
    }

    public Float getLon() {
        return lon;
    }

    public void setLon(Float lon) {
        this.lon = lon;
    }

    public Integer getZoomLevel() {
        return zoomLevel;
    }

    public void setZoomLevel(Integer zoomLevel) {
        this.zoomLevel = zoomLevel;
    }

    public String getMessageSortOrder() {
        return messageSortOrder;
    }

    public void setMessageSortOrder(String messageSortOrder) {
        this.messageSortOrder = messageSortOrder;
    }

    public List<AreaVo> getAreas() {
        return areas;
    }

    public void setAreas(List<AreaVo> areas) {
        this.areas = areas;
    }

    public List<CategoryVo> getCategories() {
        return categories;
    }

    public void setCategories(List<CategoryVo> categories) {
        this.categories = categories;
    }

    public List<SystemMessageSeriesVo> getMessageSeries() {
        return messageSeries;
    }

    public void setMessageSeries(List<SystemMessageSeriesVo> messageSeries) {
        this.messageSeries = messageSeries;
    }

    public Boolean getPublish() {
        return publish;
    }

    public void setPublish(Boolean publish) {
        this.publish = publish;
    }

    public Boolean getAtons() {
        return atons;
    }

    public void setAtons(Boolean atons) {
        this.atons = atons;
    }

    public Boolean getTemplates() {
        return templates;
    }

    public void setTemplates(Boolean templates) {
        this.templates = templates;
    }

    public Boolean getFiringSchedule() {
        return firingSchedule;
    }

    public void setFiringSchedule(Boolean firingSchedule) {
        this.firingSchedule = firingSchedule;
    }

    public Boolean getInKeycloak() {
        return inKeycloak;
    }

    public void setInKeycloak(Boolean inKeycloak) {
        this.inKeycloak = inKeycloak;
    }
}
