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
import org.niord.core.promulgation.vo.NavtexPromulgationVo;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.JoinColumn;
import javax.persistence.MapKeyColumn;
import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;

/**
 * Defines the promulgation data associated with NAVTEX mailing list promulgation
 */
@Entity
@DiscriminatorValue(NavtexPromulgation.TYPE)
@SuppressWarnings("unused")
public class NavtexPromulgation extends BaseMailPromulgation<NavtexPromulgationVo> {

    public static final String  TYPE = "navtex";

    /** Navtex Priority */
    public enum NavtexPriority {
        NONE,
        ROUTINE,
        IMPORTANT,
        VITAL
    }


    @NotNull
    @Enumerated(EnumType.STRING)
    NavtexPriority priority = NavtexPriority.NONE;

    @ElementCollection
    @MapKeyColumn(name="transmitter")
    @Column(name="send")
    @CollectionTable(name="NavtexTransmitters", joinColumns=@JoinColumn(name="NavtexTransmitters_id"))
    Map<String, Boolean> transmitters = new HashMap<>();


    /** Constructor **/
    public NavtexPromulgation() {
        super();
        this.type = TYPE;
    }


    /** Constructor **/
    public NavtexPromulgation(NavtexPromulgationVo promulgation, Message message) {
        super(promulgation, message);
        this.priority = promulgation.getPriority();
        this.transmitters.putAll(promulgation.getTransmitters());
    }


    /** Returns a value object for this entity */
    public NavtexPromulgationVo toVo() {
        NavtexPromulgationVo data = toVo(new NavtexPromulgationVo());
        data.setPriority(priority);
        data.getTransmitters().putAll(transmitters);
        return data;
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public NavtexPriority getPriority() {
        return priority;
    }

    public void setPriority(NavtexPriority priority) {
        this.priority = priority;
    }

    public Map<String, Boolean> getTransmitters() {
        return transmitters;
    }

    public void setTransmitters(Map<String, Boolean> transmitters) {
        this.transmitters = transmitters;
    }
}
