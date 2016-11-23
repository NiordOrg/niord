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

package org.niord.core.publication;

import org.niord.core.model.DescEntity;
import org.niord.model.publication.PublicationTypeDescVo;
import org.niord.model.ILocalizedDesc;

import javax.persistence.Entity;
import javax.validation.constraints.NotNull;

/**
 * Localized contents for the PublicationType entity
 */
@Entity
@SuppressWarnings("unused")
public class PublicationTypeDesc extends DescEntity<PublicationType> {

    @NotNull
    String name;

    String description;


    /** Constructor */
    public PublicationTypeDesc() {
    }


    /** Constructor */
    public PublicationTypeDesc(PublicationTypeDescVo desc) {
        super(desc);
        this.name = desc.getName();
        this.description = desc.getDescription();
    }


    /** Converts this entity to a value object */
    public PublicationTypeDescVo toVo() {
        PublicationTypeDescVo desc = new PublicationTypeDescVo();
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
    public void copyDesc(ILocalizedDesc localizedDesc) {
        PublicationTypeDesc desc = (PublicationTypeDesc)localizedDesc;
        this.name = desc.getName();
        this.description = desc.getDescription();
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
