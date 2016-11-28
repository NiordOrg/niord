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
@XmlType(propOrder = { "title", "titleFormat", "link", "messagePublicationFormat" })
public class PublicationDescVo implements ILocalizedDesc, IJsonSerializable {

    String lang;
    String title;
    String titleFormat;
    String link;
    String messagePublicationFormat;


    /** {@inheritDoc} */
    @Override
    public boolean descDefined() {
        return ILocalizedDesc.fieldsDefined(title, link);
    }


    /** {@inheritDoc} */
    @Override
    public void copyDesc(ILocalizedDesc localizedDesc) {
        PublicationDescVo desc = (PublicationDescVo)localizedDesc;
        this.title = desc.getTitle();
        this.link = desc.getLink();
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

    public String getMessagePublicationFormat() {
        return messagePublicationFormat;
    }

    public void setMessagePublicationFormat(String messagePublicationFormat) {
        this.messagePublicationFormat = messagePublicationFormat;
    }
}
