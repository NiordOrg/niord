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

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.niord.core.promulgation.BasePromulgation;
import org.niord.model.IJsonSerializable;

/**
 * Abstract super-class for all types of promulgation data value objects.
 * <p>
 * NB: We cannot use the usual @JsonSubTypes mechanism, because new promulgation types are added via a plug-in mechanism.
 * Instead, we get Jackson to stamp the fully qualified class name into the serialized JSON in order to facilitate
 * proper unmarshalling into the correct promulgation data subtype.
 * This is not elegant, but an acceptable solution, since promulgations are not part of the public model and API,
 * but internal to Niord.
 */
@JsonTypeInfo(use=JsonTypeInfo.Id.CLASS, include= JsonTypeInfo.As.PROPERTY, property="@class")
@SuppressWarnings("unused")
public abstract class BasePromulgationVo<P extends BasePromulgation> implements IJsonSerializable {

    Integer id;
    String type;
    boolean promulgate;

    /**
     * Converts this value object to an associated template entity
     * @return an associated template entity
     */
    public abstract P toEntity();


    /**
     * Returns whether this promulgation is properly defined or not.
     * Promulgations not properly defined will not be persisted.
     **/
    public abstract boolean promulgationDataDefined();


    /*************************/
    /** Getters and Setters **/
    /*************************/

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isPromulgate() {
        return promulgate;
    }

    public void setPromulgate(boolean promulgate) {
        this.promulgate = promulgate;
    }
}
