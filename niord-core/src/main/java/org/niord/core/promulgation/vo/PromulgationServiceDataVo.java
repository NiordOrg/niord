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

import org.niord.core.promulgation.BasePromulgationService;
import org.niord.model.IJsonSerializable;

/**
 * Defines the persistent data of a promulgation service
 */
public class PromulgationServiceDataVo implements IJsonSerializable, Comparable<PromulgationServiceDataVo> {

    String type;
    int priority;
    boolean active;
    Class<? extends BasePromulgationService> serviceClass;


    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("all")
    public int compareTo(PromulgationServiceDataVo p) {
        return p == null ? -1 : priority - p.getPriority();
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

    public Class<? extends BasePromulgationService> getServiceClass() {
        return serviceClass;
    }

    public void setServiceClass(Class<? extends BasePromulgationService> serviceClass) {
        this.serviceClass = serviceClass;
    }
}
