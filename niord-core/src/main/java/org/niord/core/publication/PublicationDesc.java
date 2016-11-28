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
import org.niord.model.publication.PublicationDescVo;
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
    String title;

    /**
     * The format can be used to format a message publication
     */
    String format;

    /**
     * If defined, will be used as a link for all message publications
     */
    String link;

    /** Constructor */
    public PublicationDesc() {
    }


    /** Constructor */
    public PublicationDesc(PublicationDescVo desc) {
        super(desc);
        this.title = desc.getTitle();
        this.format = desc.getFormat();
        this.link = desc.getLink();
    }


    /** Converts this entity to a value object */
    public PublicationDescVo toVo() {
        PublicationDescVo desc = new PublicationDescVo();
        desc.setLang(lang);
        desc.setTitle(title);
        desc.setFormat(format);
        desc.setLink(link);
        return desc;
    }


    /** {@inheritDoc} */
    @Override
    public boolean descDefined() {
        return ILocalizedDesc.fieldsDefined(title, format, link);
    }


    /** {@inheritDoc} */
    @Override
    public void copyDesc(ILocalizedDesc localizedDesc) {
        PublicationDesc desc = (PublicationDesc)localizedDesc;
        this.title = desc.getTitle();
        this.format = desc.getFormat();
        this.link = desc.getLink();
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }
}
