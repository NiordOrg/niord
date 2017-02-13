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

import org.niord.core.model.VersionedEntity;
import org.niord.core.promulgation.vo.PromulgationServiceDataVo;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;

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

    /** Returns the value object representation of this entity **/
    public PromulgationServiceDataVo toVo(Class<? extends BasePromulgationService> serviceClass) {
        PromulgationServiceDataVo serviceData = new PromulgationServiceDataVo();
        serviceData.setType(type);
        serviceData.setServiceClass(serviceClass);
        serviceData.setPriority(priority);
        serviceData.setActive(active);
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
}
