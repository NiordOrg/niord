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

import org.niord.core.message.Message;
import org.niord.core.model.VersionedEntity;
import org.niord.core.promulgation.vo.BasePromulgationVo;

import javax.persistence.Column;
import javax.persistence.DiscriminatorColumn;
import javax.persistence.DiscriminatorType;
import javax.persistence.Entity;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotNull;

/**
 * Abstract super-class for all types of message promulgation data entities.
 */
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "type", discriminatorType = DiscriminatorType.STRING)
@SuppressWarnings("unused")
public class BasePromulgation<P extends BasePromulgationVo>
        extends VersionedEntity<Integer> {

    // NB: Managed by JPA
    @Column(name = "type", insertable = false, updatable = false)
    @SuppressWarnings("all")
    String type;

    @NotNull
    @ManyToOne
    Message message;

    boolean promulgate;


    /** Constructor **/
    public BasePromulgation() {
    }


    /** Constructor **/
    public BasePromulgation(P promulgation) {
        this();
        this.id = promulgation.getId();
        this.type = promulgation.getType();
        this.promulgate = promulgation.isPromulgate();
    }


    /** Returns a value object for this entity */
    public P toVo() {
        return null;
    }


    /** Returns a value object for this entity */
    protected P toVo(P vo) {
        vo.setType(getType());
        vo.setId(id);
        vo.setPromulgate(promulgate);
        return vo;
    }


    /** Updates this promulgation from another promulgation **/
    public void update(BasePromulgation promulgation) {
        this.type = promulgation.getType();
        this.promulgate = promulgation.isPromulgate();
    }


    /**
     * Returns whether this promulgation is properly defined or not.
     * Promulgations not properly defined will not be persisted.
     **/
    public boolean promulgationDataDefined() {
        return toVo().promulgationDataDefined();
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getType() {
        return type;
    }

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }

    public boolean isPromulgate() {
        return promulgate;
    }

    public void setPromulgate(boolean promulgate) {
        this.promulgate = promulgate;
    }
}
