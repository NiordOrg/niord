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
import org.niord.core.promulgation.vo.BaseMailPromulgationVo;

import javax.persistence.MappedSuperclass;

/**
 * Abstract super-class for mailing list-based promulgation data types.
 */
@MappedSuperclass
public abstract class BaseMailPromulgation<P extends BaseMailPromulgationVo> extends BasePromulgation<P> {

    String text;


    /** Constructor **/
    public BaseMailPromulgation() {
        super();
    }


    /** Constructor **/
    public BaseMailPromulgation(P promulgation, Message message) {
        super(promulgation, message);
        setText(promulgation.getText());
    }


    /** {@inheritDoc} **/
    @Override
    protected P toVo(P vo) {
        super.toVo(vo);
        vo.setText(text);
        return vo;
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
