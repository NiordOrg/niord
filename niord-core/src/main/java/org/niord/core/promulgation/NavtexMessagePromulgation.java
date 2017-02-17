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

import org.niord.core.promulgation.vo.NavtexMessagePromulgationVo;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Lob;
import javax.persistence.ManyToMany;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Defines the message promulgation entity associated with NAVTEX mailing list promulgations
 */
@Entity
@DiscriminatorValue(NavtexMessagePromulgation.TYPE)
@SuppressWarnings("unused")
public class NavtexMessagePromulgation
        extends BaseMessagePromulgation<NavtexMessagePromulgationVo>
        implements IMailPromulgation {

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

    @ManyToMany
    List<NavtexTransmitter> transmitters = new ArrayList<>();

    @Lob
    String text;

    /** Constructor **/
    public NavtexMessagePromulgation() {
        super();
    }


    /** Constructor **/
    public NavtexMessagePromulgation(NavtexMessagePromulgationVo promulgation) {
        super(promulgation);

        this.priority = promulgation.getPriority();
        this.transmitters = promulgation.getTransmitters().entrySet().stream()
                .filter(Map.Entry::getValue) // Only selected transmitters
                .map(e -> new NavtexTransmitter(e.getKey()))
                .collect(Collectors.toList());
        this.text = promulgation.getText();
    }


    /** Returns a value object for this entity */
    public NavtexMessagePromulgationVo toVo() {

        NavtexMessagePromulgationVo data = toVo(new NavtexMessagePromulgationVo());

        data.setPriority(priority);
        data.setTransmitters(transmitters.stream()
            .collect(Collectors.toMap(NavtexTransmitter::getName, t -> Boolean.TRUE)));
        data.setText(text);
        return data;
    }


    /** Updates this promulgation from another promulgation **/
    @Override
    public void update(BaseMessagePromulgation promulgation) {
        if (promulgation instanceof NavtexMessagePromulgation) {
            super.update(promulgation);

            NavtexMessagePromulgation p = (NavtexMessagePromulgation)promulgation;
            this.priority = p.getPriority();
            this.transmitters.clear();
            this.transmitters.addAll(p.getTransmitters());
            this.text = p.getText();
        }
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

    public List<NavtexTransmitter> getTransmitters() {
        return transmitters;
    }

    public void setTransmitters(List<NavtexTransmitter> transmitters) {
        this.transmitters = transmitters;
    }

    @Override
    public String getText() {
        return text;
    }

    @Override
    public void setText(String text) {
        this.text = text;
    }
}
