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
import org.niord.core.promulgation.vo.PromulgationTypeVo;
import org.niord.model.DataFilter;
import org.niord.model.message.Type;

import javax.persistence.Cacheable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.ManyToMany;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OrderColumn;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Defines a promulgation type.
 * <p>
 * Niord sports a plug-in architecture where promulgation services (such as MailingListPromulgationService,
 * NavtexPromulgationService, etc.) all register with the {@linkplain PromulgationManager}.
 * <p>
 * Each promulgation service is associated with a list of promulgation types, as defined by this class.
 * As an example, there may be "NAVTEX-DK" and "NAVTEX-GL" types managed by NAVTEX promulgation service.
 * <p>
 * In turn, messages are associated with a list of {@linkplain BaseMessagePromulgation}-derived entities that
 * are each tied to a promulgation type.
 */
@Entity
@Cacheable
@NamedQueries({
        @NamedQuery(name= "PromulgationType.findByTypeId",
                query="SELECT pt FROM PromulgationType pt where pt.typeId = :typeId"),
        @NamedQuery(name= "PromulgationType.findByTypeIds",
                query="SELECT pt FROM PromulgationType pt where pt.typeId in (:typeIds)"),
        @NamedQuery(name= "PromulgationType.findAll",
                query="SELECT pt FROM PromulgationType pt order by pt.priority asc"),
        @NamedQuery(name= "PromulgationType.findActive",
                query="SELECT pt FROM PromulgationType pt where pt.active = true order by pt.priority asc")
})
@SuppressWarnings("unused")
public class PromulgationType extends VersionedEntity<Integer> {

    public enum Requirement { OPTIONAL, DEFAULT, MANDATORY }

    @Column(unique = true, nullable = false)
    String typeId;

    @NotNull
    String serviceId;

    @NotNull
    String name;

    int priority;

    boolean active;

    // Whether the promulgation type is "mandatory", "optional" or "default"
    @Enumerated(EnumType.STRING)
    Requirement requirement;

    String language;

    @ManyToMany
    List<Domain> domains = new ArrayList<>();

    @ElementCollection(targetClass = Type.class)
    @Enumerated(EnumType.STRING)
    Set<Type> messageTypes = new HashSet<>();

    /** The script resources to execute as part of the process of executing a message template **/
    @OrderColumn(name = "indexNo")
    @ElementCollection
    List<String> scriptResourcePaths = new ArrayList<>();

    /** Constructor **/
    public PromulgationType() {
    }


    /** Constructor **/
    public PromulgationType(PromulgationTypeVo typeVo) {
        this.typeId = typeVo.getTypeId();
        this.serviceId = typeVo.getServiceId();
        this.name = typeVo.getName();
        this.priority = typeVo.getPriority();
        this.active = typeVo.isActive() != null && typeVo.isActive();
        this.requirement = typeVo.getRequirement();
        this.language = typeVo.getLanguage();
        if (typeVo.getDomains() != null) {
            this.domains = typeVo.getDomains().stream()
                    .map(Domain::new)
                    .collect(Collectors.toList());
        }
        if (typeVo.getMessageTypes() != null) {
            this.messageTypes.addAll(typeVo.getMessageTypes());
        }
        if (typeVo.getScriptResourcePaths() != null) {
            scriptResourcePaths.addAll(typeVo.getScriptResourcePaths());
        }
    }

    /** Returns the value object representation of this entity **/
    public PromulgationTypeVo toVo() {
        return toVo(DataFilter.get().fields(DataFilter.DETAILS));
    }


    /** Returns the value object representation of this entity **/
    public PromulgationTypeVo toVo(DataFilter filter) {

        DataFilter compFilter = filter.forComponent(PromulgationType.class);

        PromulgationTypeVo typeVo = new PromulgationTypeVo();
        typeVo.setTypeId(typeId);
        typeVo.setServiceId(serviceId);
        typeVo.setName(name);
        typeVo.setPriority(priority);
        typeVo.setRequirement(requirement);
        if (compFilter.includeDetails()) {
            typeVo.setActive(active);
            typeVo.setLanguage(language);
            typeVo.setDomains(domains.stream()
                    .map(Domain::toVo)
                    .collect(Collectors.toList()));
            if (!messageTypes.isEmpty()) {
                typeVo.setMessageTypes(new HashSet<>(messageTypes));
            }
            if (!scriptResourcePaths.isEmpty()) {
                typeVo.setScriptResourcePaths(new ArrayList<>(scriptResourcePaths));
            }
        }
        return typeVo;
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getTypeId() {
        return typeId;
    }

    public void setTypeId(String type) {
        this.typeId = type;
    }

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public Requirement getRequirement() {
        return requirement;
    }

    public void setRequirement(Requirement requirement) {
        this.requirement = requirement;
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

    public Set<Type> getMessageTypes() {
        return messageTypes;
    }

    public void setMessageTypes(Set<Type> messageTypes) {
        this.messageTypes = messageTypes;
    }

    public List<String> getScriptResourcePaths() {
        return scriptResourcePaths;
    }

    public void setScriptResourcePaths(List<String> scriptResourcePaths) {
        this.scriptResourcePaths = scriptResourcePaths;
    }
}
