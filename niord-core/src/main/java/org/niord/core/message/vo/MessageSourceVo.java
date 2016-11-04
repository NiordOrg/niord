/*
 * Copyright 2016 Danish Maritime Authority.
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

package org.niord.core.message.vo;

import org.niord.model.IJsonSerializable;
import org.niord.model.ILocalizable;

import java.util.Date;
import java.util.List;

/**
 * Defines a message source that may be associated with a message.
 */
public class MessageSourceVo implements ILocalizable<MessageSourceDescVo>, IJsonSerializable {

    Date date;
    List<MessageSourceDescVo> descs;


    /** {@inheritDoc} */
    @Override
    public MessageSourceDescVo createDesc(String lang) {
        MessageSourceDescVo desc = new MessageSourceDescVo();
        desc.setLang(lang);
        checkCreateDescs().add(desc);
        return desc;
    }

    /** Returns if the message source is valid **/
    public boolean sourceDefined() {
        return descs != null && !descs.isEmpty() && date != null;
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    @Override
    public List<MessageSourceDescVo> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<MessageSourceDescVo> descs) {
        this.descs = descs;
    }
}
