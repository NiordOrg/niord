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
import org.niord.core.publication.vo.PublicationDescVo;
import org.niord.model.ILocalizedDesc;

import javax.persistence.Entity;
import javax.validation.constraints.NotNull;

/**
 * Localized contents for the Publication entity
 */
@Entity
@SuppressWarnings("unused")
public class PublicationDesc extends DescEntity<Publication> {

    @NotNull
    String name;

    /**
     * The format can be used to format a message publication
     */
    String format;

    /** Constructor */
    public PublicationDesc() {
    }


    /** Constructor */
    public PublicationDesc(PublicationDescVo desc) {
        super(desc);
        this.name = desc.getName();
        this.format = desc.getFormat();
    }


    /** Converts this entity to a value object */
    public PublicationDescVo toVo() {
        PublicationDescVo desc = new PublicationDescVo();
        desc.setLang(lang);
        desc.setName(name);
        desc.setFormat(format);
        return desc;
    }


    /** {@inheritDoc} */
    @Override
    public boolean descDefined() {
        return ILocalizedDesc.fieldsDefined(name, format);
    }


    /** {@inheritDoc} */
    @Override
    public void copyDesc(ILocalizedDesc localizedDesc) {
        PublicationDesc desc = (PublicationDesc)localizedDesc;
        this.name = desc.getName();
        this.format = desc.getFormat();
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

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }
}
