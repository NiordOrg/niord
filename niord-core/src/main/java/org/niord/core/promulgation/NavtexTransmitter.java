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

package org.niord.core.promulgation;

import org.niord.core.area.Area;
import org.niord.core.model.BaseEntity;
import org.niord.core.promulgation.vo.NavtexTransmitterVo;
import org.niord.model.DataFilter;
import org.niord.model.message.AreaVo;

import jakarta.persistence.Cacheable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OrderColumn;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Defines a NAVTEX transmitter station for a specific NAVTEX promulgation type
 */
@Entity
@Cacheable
@NamedQueries({
        @NamedQuery(name="NavtexTransmitter.findByName",
                query="SELECT t FROM NavtexTransmitter t where t.promulgationType.typeId = :typeId "
                        + " and lower(t.name) = lower(:name)"),
        @NamedQuery(name="NavtexTransmitter.findByType",
                query="SELECT t FROM NavtexTransmitter t where t.promulgationType.typeId = :typeId "
                        + " order by t.name asc")
})
@SuppressWarnings("unused")
public class NavtexTransmitter extends BaseEntity<Integer> {

    @NotNull
    @ManyToOne
    PromulgationType promulgationType;

    @Column(unique = true, nullable = false)
    String name;

    boolean active;

    @ManyToMany
    @OrderColumn
    List<Area> areas = new ArrayList<>();


    /** Constructor **/
    public NavtexTransmitter() {
    }


    /** Constructor **/
    public NavtexTransmitter(String name) {
        this.name = name;
    }


    /** Constructor **/
    public NavtexTransmitter(NavtexTransmitterVo transmitter) {
        this.promulgationType = new PromulgationType(transmitter.getPromulgationType());
        this.name = transmitter.getName();
        this.active = transmitter.isActive();
        if (transmitter.getAreas() != null) {
            this.areas = transmitter.getAreas().stream()
                    .map(Area::new)
                    .collect(Collectors.toList());
        }
    }


    /** Returns a value object representation of this entity **/
    public NavtexTransmitterVo toVo(DataFilter filter) {
        NavtexTransmitterVo transmitter = new NavtexTransmitterVo();
        transmitter.setPromulgationType(promulgationType.toVo());
        transmitter.setName(name);
        transmitter.setActive(active);
        transmitter.setAreas(areas.stream()
            .map(a -> a.toVo(AreaVo.class, filter))
            .collect(Collectors.toList()));
        return transmitter;
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    public PromulgationType getPromulgationType() {
        return promulgationType;
    }

    public void setPromulgationType(PromulgationType promulgationType) {
        this.promulgationType = promulgationType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public List<Area> getAreas() {
        return areas;
    }

    public void setAreas(List<Area> areas) {
        this.areas = areas;
    }
}
