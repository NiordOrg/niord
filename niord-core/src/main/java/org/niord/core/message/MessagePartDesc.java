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

package org.niord.core.message;

import org.niord.core.model.DescEntity;
import org.niord.model.DataFilter;
import org.niord.model.ILocalizedDesc;
import org.niord.model.message.MessagePartDescVo;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Lob;

/**
 * Localized contents for the MessagePart entity
 */
@Entity
@SuppressWarnings("unused")
public class MessagePartDesc extends DescEntity<MessagePart> {

    @Column(length = 500)
    String subject;

    @Lob
    String details;

    /** Constructor */
    public MessagePartDesc() {
    }

    /** Constructor */
    public MessagePartDesc(MessagePartDescVo desc) {
        this(desc, DataFilter.get());
    }

    /** Constructor */
    public MessagePartDesc(MessagePartDescVo desc, DataFilter filter) {
        super(desc);

        DataFilter compFilter = filter.forComponent(MessagePartDesc.class);

        this.subject = trunc(desc.getSubject(), 500);
        this.details = desc.getDetails();
    }


    /** Converts this entity to a value object */
    public MessagePartDescVo toVo(DataFilter filter) {

        DataFilter compFilter = filter.forComponent(MessagePartDesc.class);

        MessagePartDescVo desc = new MessagePartDescVo();
        desc.setLang(lang);
        desc.setSubject(subject);
        desc.setDetails(details);
        return desc;
    }


    /** {@inheritDoc} */
    @Override
    public boolean descDefined() {
        return ILocalizedDesc.fieldsDefined(subject, details);
    }


    /** {@inheritDoc} */
    @Override
    public void copyDesc(ILocalizedDesc localizedDesc) {
        MessagePartDesc desc = (MessagePartDesc)localizedDesc;
        this.lang = desc.getLang();
        this.subject = desc.getSubject();
        this.details = desc.getDetails();
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

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
