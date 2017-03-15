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
import org.niord.core.promulgation.RadioMessagePromulgation;

/**
 * Defines the value object data associated with radio mailing list promulgation.
 * The audio text is meant to be read up on radio, and thus more verbose.
 */
public class RadioMessagePromulgationVo extends BaseMessagePromulgationVo<RadioMessagePromulgation> implements IMailPromulgation {

    String text;


    /** Constructor **/
    public RadioMessagePromulgationVo() {
        super();
    }


    /** Constructor **/
    public RadioMessagePromulgationVo(PromulgationTypeVo type) {
        super(type);
    }


    /** {@inheritDoc} **/
    @Override
    public RadioMessagePromulgation toEntity() {
        return new RadioMessagePromulgation(this);
    }


    /** {@inheritDoc} **/
    @Override
    public boolean promulgationDataDefined() {
        return StringUtils.isNotBlank(text);
    }


    /** Resets data of this message promulgation **/
    public RadioMessagePromulgationVo reset() {
        text = null;
        return this;
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    @Override
    public String getText() {
        return text;
    }

    @Override
    public void setText(String text) {
        this.text = text;
    }
}
