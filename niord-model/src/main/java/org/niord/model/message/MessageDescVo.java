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
package org.niord.model.message;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.niord.model.IJsonSerializable;
import org.niord.model.ILocalizedDesc;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;

/**
 * The message description VO
 */
@Schema(name = "MessageDesc", description = "Translatable fields of the Message model")
@XmlType(propOrder = { "title", "vicinity", "publication", "internalPublication", "source" })
@SuppressWarnings("unused")
public class MessageDescVo implements ILocalizedDesc, IJsonSerializable {

    String lang;
    String title;
    String vicinity;
    String publication;
    String internalPublication;
    String source;

    /** {@inheritDoc} */
    @Override
    public boolean descDefined() {
        return ILocalizedDesc.fieldsDefined(title, vicinity, publication, internalPublication, source );
    }

    /** {@inheritDoc} */
    @Override
    public void copyDesc(ILocalizedDesc localizedDesc) {
        MessageDescVo desc = (MessageDescVo)localizedDesc;
        this.title = desc.getTitle();
        this.vicinity = desc.getVicinity();
        this.publication = desc.getPublication();
        this.internalPublication = desc.getInternalPublication();
        this.source = desc.getSource();
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

    public String getVicinity() {
        return vicinity;
    }

    public void setVicinity(String vicinity) {
        this.vicinity = vicinity;
    }

    public String getPublication() {
        return publication;
    }

    public void setPublication(String publication) {
        this.publication = publication;
    }

    public String getInternalPublication() {
        return internalPublication;
    }

    public void setInternalPublication(String internalPublication) {
        this.internalPublication = internalPublication;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
