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

import io.swagger.annotations.ApiModel;
import org.niord.model.IJsonSerializable;
import org.niord.model.ILocalizedDesc;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;

/**
 * The entity description VO
 */
@ApiModel(value = "PublicationDesc", description = "Translatable fields of the Publication model")
@XmlType(propOrder = { "title", "titleFormat", "link", "fileName", "messagePublicationFormat" })
@SuppressWarnings("unused")
public class PublicationDescVo implements ILocalizedDesc, IJsonSerializable {

    /**
     * The Lang.
     */
    String lang;
    /**
     * The Title.
     */
    String title;
    /**
     * The Title format.
     */
    String titleFormat;
    /**
     * The Link.
     */
    String link;
    /**
     * The File name.
     */
    String fileName;
    /**
     * The Message publication format.
     */
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

    /**
     * Gets title.
     *
     * @return the title
     */
    public String getTitle() {
        return title;
    }

    /**
     * Sets title.
     *
     * @param title the title
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Gets title format.
     *
     * @return the title format
     */
    public String getTitleFormat() {
        return titleFormat;
    }

    /**
     * Sets title format.
     *
     * @param titleFormat the title format
     */
    public void setTitleFormat(String titleFormat) {
        this.titleFormat = titleFormat;
    }

    /**
     * Gets link.
     *
     * @return the link
     */
    public String getLink() {
        return link;
    }

    /**
     * Sets link.
     *
     * @param link the link
     */
    public void setLink(String link) {
        this.link = link;
    }

    /**
     * Gets file name.
     *
     * @return the file name
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Sets file name.
     *
     * @param fileName the file name
     */
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    /**
     * Gets message publication format.
     *
     * @return the message publication format
     */
    public String getMessagePublicationFormat() {
        return messagePublicationFormat;
    }

    /**
     * Sets message publication format.
     *
     * @param messagePublicationFormat the message publication format
     */
    public void setMessagePublicationFormat(String messagePublicationFormat) {
        this.messagePublicationFormat = messagePublicationFormat;
    }
}
