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

import org.niord.core.domain.Domain;
import org.niord.core.model.VersionedEntity;
import org.niord.core.promulgation.vo.PromulgationServiceDataVo;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.ManyToMany;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A value object representation of the persistent data of a promulgation service.
 * Also holds a transient reference to the associated promulgation service class.
 */
@Entity
@NamedQueries({
        @NamedQuery(name="PromulgationServiceData.findByType",
                query="SELECT p FROM PromulgationServiceData p where p.type = :type")
})
public class PromulgationServiceData extends VersionedEntity<Integer> {

    @Column(unique = true, nullable = false)
    String type;

    int priority;

    boolean active;

    String language;

    @ManyToMany
    List<Domain> domains = new ArrayList<>();

    /** Returns the value object representation of this entity **/
    public PromulgationServiceDataVo toVo(Class<? extends BasePromulgationService> serviceClass) {
        PromulgationServiceDataVo serviceData = new PromulgationServiceDataVo();
        serviceData.setType(type);
        serviceData.setServiceClass(serviceClass);
        serviceData.setPriority(priority);
        serviceData.setActive(active);
        serviceData.setLanguage(language);
        serviceData.setDomains(domains.stream()
            .map(Domain::toVo)
            .collect(Collectors.toList()));
        return serviceData;
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public List<Domain> getDomains() {
        return domains;
    }

    public void setDomains(List<Domain> domains) {
        this.domains = domains;
    }
}
