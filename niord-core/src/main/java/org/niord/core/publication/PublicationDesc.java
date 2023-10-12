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
import org.niord.model.ILocalizedDesc;
import org.niord.model.publication.PublicationDescVo;

import jakarta.persistence.Entity;
import jakarta.validation.constraints.NotNull;

/**
 * Localized contents for the Publication entity
 */
@Entity
@SuppressWarnings("unused")
public class PublicationDesc extends DescEntity<Publication> {

    @NotNull
    String title;

    /**
     * The title format is used by templates to define the format of concrete publications based on the template
     */
    String titleFormat;

    /**
     * If defined, will be used as a link for all message publications
     */
    String link;

    /**
     * The file name of the linked file
     */
    String fileName;

    /**
     * The format can be used to format a message publication
     */
    String messagePublicationFormat;


    /** Constructor */
    public PublicationDesc() {
    }


    /** Updates this entity from a value object **/
    public void update(PublicationDescVo desc) {
        this.title = desc.getTitle();
        this.titleFormat = desc.getTitleFormat();
        this.link = desc.getLink();
        this.fileName = desc.getFileName();
        this.messagePublicationFormat = desc.getMessagePublicationFormat();
    }


    /** Converts this entity to a value object */
    public PublicationDescVo toVo() {
        PublicationDescVo desc = new PublicationDescVo();
        desc.setLang(lang);
        desc.setTitle(title);
        desc.setTitleFormat(titleFormat);
        desc.setLink(link);
        desc.setFileName(fileName);
        desc.setMessagePublicationFormat(messagePublicationFormat);
        return desc;
    }


    /** {@inheritDoc} */
    @Override
    public boolean descDefined() {
        return ILocalizedDesc.fieldsDefined(title, titleFormat, messagePublicationFormat, link, fileName);
    }


    /** {@inheritDoc} */
    @Override
    public void copyDesc(ILocalizedDesc localizedDesc) {
        PublicationDesc desc = (PublicationDesc)localizedDesc;
        this.title = desc.getTitle();
        this.titleFormat = desc.getTitleFormat();
        this.link = desc.getLink();
        this.fileName = desc.getFileName();
        this.messagePublicationFormat = desc.getMessagePublicationFormat();
    }


    /*************************/
    /** Getters and Setters **/
    /***/

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTitleFormat() {
        return titleFormat;
    }

    public void setTitleFormat(String titleFormat) {
        this.titleFormat = titleFormat;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getMessagePublicationFormat() {
        return messagePublicationFormat;
    }

    public void setMessagePublicationFormat(String messagePublicationFormat) {
        this.messagePublicationFormat = messagePublicationFormat;
    }
}
