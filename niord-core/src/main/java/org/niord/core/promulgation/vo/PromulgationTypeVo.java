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

package org.niord.core.promulgation.vo;

import org.niord.core.domain.vo.DomainVo;
import org.niord.model.IJsonSerializable;
import org.niord.model.message.Type;

import java.util.List;
import java.util.Set;

import static org.niord.core.promulgation.PromulgationType.Requirement;

/**
 * Defines the persistent data of a promulgation type
 */
public class PromulgationTypeVo implements IJsonSerializable, Comparable<PromulgationTypeVo> {

    String typeId;
    String serviceId;
    String name;
    int priority;
    Boolean active;
    Requirement requirement;
    String language;
    List<DomainVo> domains;
    Set<Type> messageTypes;    // If defined, can be limit which sub-types to use the promulgation type for
    List<String> scriptResourcePaths;


    /**
     * Returns a public version of the promulgation type, i.e. a version that unauthenticated users may use
     * @return a public version of the promulgation type
     */
    public PublicPromulgationTypeVo toPublicPromulgationType() {
        return new PublicPromulgationTypeVo(this);
    }


    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("all")
    public int compareTo(PromulgationTypeVo p) {
        return p == null ? -1 : priority - p.getPriority();
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getTypeId() {
        return typeId;
    }

    public void setTypeId(String typeId) {
        this.typeId = typeId;
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

    public Boolean isActive() {
        return active;
    }

    public void setActive(Boolean active) {
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

    public List<DomainVo> getDomains() {
        return domains;
    }

    public void setDomains(List<DomainVo> domains) {
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
