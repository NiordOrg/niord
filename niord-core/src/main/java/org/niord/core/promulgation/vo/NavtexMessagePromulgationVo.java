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

import org.apache.commons.lang.StringUtils;
import org.niord.core.promulgation.IMailPromulgation;
import org.niord.core.promulgation.NavtexMessagePromulgation;

import java.util.HashMap;
import java.util.Map;

/**
 * Defines the promulgation data associated with NAVTEX mailing list promulgation
 */
@SuppressWarnings("unused")
public class NavtexMessagePromulgationVo extends BaseMessagePromulgationVo<NavtexMessagePromulgation> implements IMailPromulgation {

    NavtexMessagePromulgation.NavtexPriority priority = NavtexMessagePromulgation.NavtexPriority.NONE;

    Map<String, Boolean> transmitters = new HashMap<>();

    String text;


    /** Constructor **/
    public NavtexMessagePromulgationVo() {
        super();
    }


    /** Constructor **/
    public NavtexMessagePromulgationVo(PromulgationTypeVo type) {
        super(type);
    }


    /** {@inheritDoc} **/
    @Override
    public NavtexMessagePromulgation toEntity() {
        return new NavtexMessagePromulgation(this);
    }


    /** {@inheritDoc} **/
    @Override
    public boolean promulgationDataDefined() {
        return StringUtils.isNotBlank(text)
                || transmitters.entrySet().stream().anyMatch(Map.Entry::getValue);
    }


    /** Resets data of this message promulgation **/
    public NavtexMessagePromulgationVo reset() {
        transmitters.clear();
        text = null;
        priority = NavtexMessagePromulgation.NavtexPriority.NONE;
        return this;
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    public NavtexMessagePromulgation.NavtexPriority getPriority() {
        return priority;
    }

    public void setPriority(NavtexMessagePromulgation.NavtexPriority priority) {
        this.priority = priority;
    }

    public Map<String, Boolean> getTransmitters() {
        return transmitters;
    }

    public void setTransmitters(Map<String, Boolean> transmitters) {
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
