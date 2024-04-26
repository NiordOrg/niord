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

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToMany;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Defines the message promulgation entity associated with NAVTEX mailing list promulgations
 */
@Entity
@DiscriminatorValue(NavtexMessagePromulgation.SERVICE_ID)
@SuppressWarnings("unused")
public class NavtexMessagePromulgation
        extends BaseMessagePromulgation<NavtexMessagePromulgationVo>
        implements IMailPromulgation {

    public static final String SERVICE_ID = "navtex";

    /** Navtex Priority */
    public enum NavtexPriority {
        NONE,
        ROUTINE,
        IMPORTANT,
        VITAL
    }


    @NotNull
    @Enumerated(EnumType.STRING)
    NavtexPriority priority = NavtexPriority.ROUTINE;

    @ManyToMany
    List<NavtexTransmitter> transmitters = new ArrayList<>();

    String preamble;

    @Column(length = 16_777_216)
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
        this.preamble = promulgation.getPreamble();
    }


    /** Returns a value object for this entity */
    @Override
    public NavtexMessagePromulgationVo toVo() {

        NavtexMessagePromulgationVo data = toVo(new NavtexMessagePromulgationVo());

        data.setPriority(priority);
        data.setTransmitters(transmitters.stream()
            .collect(Collectors.toMap(NavtexTransmitter::getName, t -> Boolean.TRUE)));
        data.setText(text);
        data.setPreamble(preamble);
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
            this.preamble = p.getPreamble();
        }
    }


    /**
     * Returns if this NAVTEX should be promulgated to the transmitter with the given name
     * @param transmitterName the transmitter name
     * @return if this NAVTEX should be promulgated to the transmitter with the given name
     */
    public boolean useTransmitter(String transmitterName) {
        return transmitters.stream().anyMatch(t -> t.getName().equalsIgnoreCase(transmitterName));
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

    public String getPreamble() {
        return preamble;
    }

    public void setPreamble(String preamble) {
        this.preamble = preamble;
    }
}
