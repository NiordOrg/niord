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

import org.niord.model.IJsonSerializable;

/**
 * A simplified version of the {@code PromulgationTypeVo} class that unauthenticated users may inspect
 */
@SuppressWarnings("unused")
public class PublicPromulgationTypeVo implements IJsonSerializable {

    String typeId;
    String name;
    Boolean active;

    /**
     * Constructor
     */
    public PublicPromulgationTypeVo() {
    }


    /**
     * Constructor
     */
    public PublicPromulgationTypeVo(PromulgationTypeVo type) {
        this.typeId = type.getTypeId();
        this.name = type.getName();
        this.active = type.isActive();
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean isActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
