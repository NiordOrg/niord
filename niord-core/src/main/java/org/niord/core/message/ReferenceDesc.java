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
import org.niord.model.ILocalizedDesc;
import org.niord.model.message.ReferenceDescVo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;

/**
 * Localized contents for the Reference entity
 */
@Entity
@SuppressWarnings("unused")
public class ReferenceDesc extends DescEntity<Reference> {

    @Column(length = 512)
    String description;

    /** Constructor */
    public ReferenceDesc() {
    }


    /** Constructor */
    public ReferenceDesc(ReferenceDescVo desc) {
        super(desc);
        this.description = desc.getDescription();
    }


    /** Converts this entity to a value object */
    public ReferenceDescVo toVo() {
        ReferenceDescVo desc = new ReferenceDescVo();
        desc.setLang(lang);
        desc.setDescription(description);
        return desc;
    }


    /** {@inheritDoc} */
    @Override
    public boolean descDefined() {
        return ILocalizedDesc.fieldsDefined(description);
    }


    /** {@inheritDoc} */
    @Override
    public void copyDesc(ILocalizedDesc localizedDesc) {
        ReferenceDesc desc = (ReferenceDesc)localizedDesc;
        this.lang = desc.getLang();
        this.description = desc.getDescription();
    }

    /*************************/
    /** Getters and Setters **/
    /***/

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

}

