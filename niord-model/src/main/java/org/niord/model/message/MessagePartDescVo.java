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

package org.niord.model.message;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.niord.model.IJsonSerializable;
import org.niord.model.ILocalizedDesc;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;

/**
 * The message part description VO
 */
@Schema(name = "MessagePartDesc", description = "Translatable fields of the MessagePart model")
@XmlType(propOrder = { "subject", "details"})
@SuppressWarnings("unused")
public class MessagePartDescVo implements ILocalizedDesc, IJsonSerializable {

    String lang;
    String subject;
    String details;

    /** {@inheritDoc} */
    @Override
    public boolean descDefined() {
        return ILocalizedDesc.fieldsDefined(subject, details);
    }

    /** {@inheritDoc} */
    @Override
    public void copyDesc(ILocalizedDesc localizedDesc) {
        MessagePartDescVo desc = (MessagePartDescVo)localizedDesc;
        this.subject = desc.getSubject();
        this.details = desc.getDetails();
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    @XmlAttribute
    @Override
    public String getLang() {
        return lang;
    }

    @Override
    public void setLang(String lang) {
        this.lang = lang;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

}
