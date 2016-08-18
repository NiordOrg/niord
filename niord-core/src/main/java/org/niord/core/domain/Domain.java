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
package org.niord.core.domain;

import org.apache.commons.lang.StringUtils;
import org.niord.core.area.Area;
import org.niord.core.category.Category;
import org.niord.core.message.MessageSeries;
import org.niord.core.model.BaseEntity;
import org.niord.model.DataFilter;
import org.niord.model.message.DomainVo;
import org.niord.model.message.MainType;

import javax.persistence.Cacheable;
import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.ManyToMany;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents an application domain
 */
@Entity
@Cacheable
@Table(indexes = {
        @Index(name = "domain_domain_id", columnList="domainId", unique = true)
})
@NamedQueries({
        @NamedQuery(name="Domain.findByDomainId",
                query="SELECT d FROM Domain d where d.domainId = :domainId")
})
@SuppressWarnings("unused")
public class Domain extends BaseEntity<Integer> {

    @NotNull
    String domainId;

    @NotNull
    String name;

    String timeZone;

    Float latitude;

    Float longitude;

    Integer zoomLevel;

    @ManyToMany
    List<Area> areas = new ArrayList<>();

    @ManyToMany
    List<Category> categories = new ArrayList<>();

    @ManyToMany
    List<MessageSeries> messageSeries = new ArrayList<>();

    Boolean schedule;

    @Transient
    Boolean inKeycloak;

    /** Constructor */
    public Domain() {
    }


    /** Constructor */
    public Domain(DomainVo domain) {
        updateDomain(domain);
    }


    /** Updates this domain from the given domain */
    public void updateDomain(DomainVo domain) {
        this.domainId = domain.getDomainId();
        this.name = domain.getName();
        this.timeZone = domain.getTimeZone();
        this.latitude = domain.getLat();
        this.longitude = domain.getLon();
        this.zoomLevel = domain.getZoomLevel();
        this.schedule = domain.getSchedule();
        this.inKeycloak = domain.getInKeycloak();

        this.areas.clear();
        if (domain.getAreas() != null) {
            this.areas = domain.getAreas().stream()
                .map(a -> new Area(a, DataFilter.get()))
                .collect(Collectors.toList());
        }

        this.categories.clear();
        if (domain.getCategories() != null) {
            this.categories = domain.getCategories().stream()
                    .map(c -> new Category(c, DataFilter.get()))
                    .collect(Collectors.toList());
        }

        this.messageSeries.clear();
        if (domain.getMessageSeries() != null) {
            this.messageSeries = domain.getMessageSeries().stream()
                    .map(MessageSeries::new)
                    .collect(Collectors.toList());
        }
    }


    /** Converts this entity to a value object */
    public DomainVo toVo() {
        DomainVo domain = new DomainVo();
        domain.setDomainId(domainId);
        domain.setName(name);
        domain.setTimeZone(timeZone);
        domain.setLat(latitude);
        domain.setLon(longitude);
        domain.setZoomLevel(zoomLevel);
        domain.setSchedule(schedule);
        domain.setInKeycloak(inKeycloak);

        if (!areas.isEmpty()) {
            domain.setAreas(areas.stream()
                .map(a -> a.toVo(DataFilter.get()))
                .collect(Collectors.toList()));
        }

        if (!categories.isEmpty()) {
            domain.setCategories(categories.stream()
                .map(c -> c.toVo(DataFilter.get()))
                .collect(Collectors.toList()));
        }

        if (!messageSeries.isEmpty()) {
            domain.setMessageSeries(messageSeries.stream()
                .map(MessageSeries::toVo)
                .collect(Collectors.toList()));
        }

        return domain;
    }


    /**
     * Checks if the values of the domain has changed.
     * Only checks relevant values, not e.g. database id, created date, etc.
     * Hence we do not use equals()
     *
     * @param template the template to compare with
     * @return if the domain has changed
     */
    @Transient
    public boolean hasChanged(Domain template) {
        return !Objects.equals(domainId, template.getDomainId()) ||
                !Objects.equals(name, template.getName()) ||
                !Objects.equals(timeZone, template.getTimeZone()) ||
                !Objects.equals(latitude, template.getLatitude()) ||
                !Objects.equals(longitude, template.getLongitude()) ||
                !Objects.equals(zoomLevel, template.getZoomLevel()) ||
                !Objects.equals(schedule, template.getSchedule()) ||
                hasChanged(areas, template.getAreas()) ||
                hasChanged(categories, template.getCategories()) ||
                hasChanged(messageSeries, template.getMessageSeries());
    }


    /** Checks if the base entity lists are different */
    private <T extends BaseEntity<Integer>> boolean hasChanged(List<T> list1, List<T> list2) {
        if (list1.size() != list2.size()) {
            return true;
        }

        Set<Integer> ids = list1.stream()
                .map(BaseEntity::getId)
                .collect(Collectors.toSet());

        return list2.stream()
                .anyMatch(e -> !ids.contains(e.getId()));
    }


    /** Returns if the domain contain the given message series **/
    public boolean containsMessageSeries(String seriesId) {
        return StringUtils.isNotBlank(seriesId) &&
                getMessageSeries().stream().anyMatch(ms -> Objects.equals(ms.getSeriesId(), seriesId));
    }


    /** Returns if the domain contain the given message series **/
    public boolean containsMessageSeries(MessageSeries messageSeries) {
        return messageSeries != null && containsMessageSeries(messageSeries.getSeriesId());
    }


    /** Returns if the domain supports the given main type **/
    public boolean supportsMainType(MainType mainType) {
        return mainType != null && getMessageSeries().stream().anyMatch(ms -> ms.getMainType() == mainType);
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

    public Float getLatitude() {
        return latitude;
    }

    public void setLatitude(Float latitude) {
        this.latitude = latitude;
    }

    public Float getLongitude() {
        return longitude;
    }

    public void setLongitude(Float longitude) {
        this.longitude = longitude;
    }

    public Integer getZoomLevel() {
        return zoomLevel;
    }

    public void setZoomLevel(Integer zoomLevel) {
        this.zoomLevel = zoomLevel;
    }

    public List<Area> getAreas() {
        return areas;
    }

    public void setAreas(List<Area> areas) {
        this.areas = areas;
    }

    public List<Category> getCategories() {
        return categories;
    }

    public void setCategories(List<Category> categories) {
        this.categories = categories;
    }

    public List<MessageSeries> getMessageSeries() {
        return messageSeries;
    }

    public void setMessageSeries(List<MessageSeries> messageSeries) {
        this.messageSeries = messageSeries;
    }

    public Boolean getSchedule() {
        return schedule;
    }

    public void setSchedule(Boolean scheduler) {
        this.schedule = scheduler;
    }

    public Boolean getInKeycloak() {
        return inKeycloak;
    }

    public void setInKeycloak(Boolean inKeycloak) {
        this.inKeycloak = inKeycloak;
    }
}
