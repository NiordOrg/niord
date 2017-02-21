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

import org.niord.core.promulgation.vo.RadioMessagePromulgationVo;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Lob;

/**
 * Defines the message promulgation entity associated with radio (or "audio") mailing list promulgation.
 *
 * Radio promulgations are verbose textual versions of the messages suitable for being read up on the radio
 * and sent to the radio station via e-mail.
 */
@Entity
@DiscriminatorValue(RadioMessagePromulgation.SERVICE_ID)
@SuppressWarnings("unused")
public class RadioMessagePromulgation extends BaseMessagePromulgation<RadioMessagePromulgationVo> implements IMailPromulgation {

    public static final String SERVICE_ID = "radio";

    @Lob
    String text;

    /** Constructor **/
    public RadioMessagePromulgation() {
        super();
    }


    /** Constructor **/
    public RadioMessagePromulgation(RadioMessagePromulgationVo promulgation) {
        super(promulgation);
        this.text = promulgation.getText();
    }


    /** Returns a value object for this entity */
    public RadioMessagePromulgationVo toVo() {
        RadioMessagePromulgationVo data = toVo(new RadioMessagePromulgationVo());
        data.setText(text);
        return data;
    }


    /** Updates this promulgation from another promulgation **/
    @Override
    public void update(BaseMessagePromulgation promulgation) {
        if (promulgation instanceof RadioMessagePromulgation) {
            super.update(promulgation);
            RadioMessagePromulgation p = (RadioMessagePromulgation)promulgation;
            p.setText(text);
        }
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
