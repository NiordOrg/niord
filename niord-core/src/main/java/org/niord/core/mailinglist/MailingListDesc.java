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

import org.niord.core.mailinglist.vo.MailingListDescVo;
import org.niord.core.model.DescEntity;
import org.niord.model.ILocalizedDesc;

import jakarta.persistence.Cacheable;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;

/**
 * Localized contents for the MailingList entity
 */
@Entity
@Cacheable
@SuppressWarnings("unused")
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "lang", "entity_id" }))
public class MailingListDesc extends DescEntity<MailingList> {

    @NotNull
    private String name;
    private String description;

    /** Constructor */
    public MailingListDesc() {
    }


    /** Constructor */
    public MailingListDesc(MailingListDescVo desc) {
        super(desc);
        this.name = desc.getName();
        this.description = desc.getDescription();
    }


    /** Converts this entity to a value object */
    public MailingListDescVo toVo() {
        MailingListDescVo desc = new MailingListDescVo();
        desc.setLang(lang);
        desc.setName(name);
        desc.setDescription(description);
        return desc;
    }

    /** {@inheritDoc} */
    @Override
    public boolean descDefined() {
        return ILocalizedDesc.fieldsDefined(name, description);
    }


    /** {@inheritDoc} */
    @Override
    public void copyDesc(ILocalizedDesc desc) {
        this.name = ((MailingListDesc)desc).getName();
        this.description = ((MailingListDesc)desc).getDescription();
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
