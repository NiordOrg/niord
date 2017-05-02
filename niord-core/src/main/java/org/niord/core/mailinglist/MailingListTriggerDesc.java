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
package org.niord.core.mailinglist;

import org.niord.core.mailinglist.vo.MailingListTriggerDescVo;
import org.niord.core.model.DescEntity;
import org.niord.model.ILocalizedDesc;

import javax.persistence.Cacheable;
import javax.persistence.Entity;
import javax.validation.constraints.NotNull;

/**
 * Localized contents for the MailingListTrigger entity
 */
@Entity
@Cacheable
@SuppressWarnings("unused")
public class MailingListTriggerDesc extends DescEntity<MailingListTrigger> {

    @NotNull
    private String subject;

    /** Constructor */
    public MailingListTriggerDesc() {
    }


    /** Constructor */
    public MailingListTriggerDesc(MailingListTriggerDescVo desc) {
        super(desc);
        this.subject = desc.getSubject();
    }


    /** Converts this entity to a value object */
    public MailingListTriggerDescVo toVo() {
        MailingListTriggerDescVo desc = new MailingListTriggerDescVo();
        desc.setLang(lang);
        desc.setSubject(subject);
        return desc;
    }

    /** {@inheritDoc} */
    @Override
    public boolean descDefined() {
        return ILocalizedDesc.fieldsDefined(subject);
    }


    /** {@inheritDoc} */
    @Override
    public void copyDesc(ILocalizedDesc desc) {
        this.subject = ((MailingListTriggerDesc)desc).getSubject();
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
}
