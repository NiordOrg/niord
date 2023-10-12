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

package org.niord.model.publication;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.niord.model.IJsonSerializable;
import org.niord.model.ILocalizedDesc;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlType;

/**
 * The entity description VO
 */
@Schema(name = "PublicationDesc", description = "Translatable fields of the Publication model")
@XmlType(propOrder = { "title", "titleFormat", "link", "fileName", "messagePublicationFormat" })
@SuppressWarnings("unused")
public class PublicationDescVo implements ILocalizedDesc, IJsonSerializable {

    String lang;
    String title;
    String titleFormat;
    String link;
    String fileName;
    String messagePublicationFormat;


    /** {@inheritDoc} */
    @Override
    public boolean descDefined() {
        return ILocalizedDesc.fieldsDefined(title, titleFormat, link, fileName);
    }


    /** {@inheritDoc} */
    @Override
    public void copyDesc(ILocalizedDesc localizedDesc) {
        PublicationDescVo desc = (PublicationDescVo)localizedDesc;
        this.title = desc.getTitle();
        this.titleFormat = desc.getTitleFormat();
        this.link = desc.getLink();
        this.fileName = desc.getFileName();
        this.messagePublicationFormat = desc.getMessagePublicationFormat();
    }

    /*************************/
    /** Getters and Setters **/
    /***/

    @XmlAttribute
    @Override
    public String getLang() {
        return lang;
    }

    @Override
    public void setLang(String lang) {
        this.lang = lang;
    }

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
