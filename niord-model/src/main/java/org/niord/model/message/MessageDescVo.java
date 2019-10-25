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

import io.swagger.annotations.ApiModel;
import org.niord.model.IJsonSerializable;
import org.niord.model.ILocalizedDesc;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;

/**
 * The message description VO
 */
@ApiModel(value = "MessageDesc", description = "Translatable fields of the Message model")
@XmlType(propOrder = { "title", "vicinity", "publication", "internalPublication", "source" })
@SuppressWarnings("unused")
public class MessageDescVo implements ILocalizedDesc, IJsonSerializable {

    /**
     * The Lang.
     */
    String lang;
    /**
     * The Title.
     */
    String title;
    /**
     * The Vicinity.
     */
    String vicinity;
    /**
     * The Publication.
     */
    String publication;
    /**
     * The Internal publication.
     */
    String internalPublication;
    /**
     * The Source.
     */
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
     * Gets vicinity.
     *
     * @return the vicinity
     */
    public String getVicinity() {
        return vicinity;
    }

    /**
     * Sets vicinity.
     *
     * @param vicinity the vicinity
     */
    public void setVicinity(String vicinity) {
        this.vicinity = vicinity;
    }

    /**
     * Gets publication.
     *
     * @return the publication
     */
    public String getPublication() {
        return publication;
    }

    /**
     * Sets publication.
     *
     * @param publication the publication
     */
    public void setPublication(String publication) {
        this.publication = publication;
    }

    /**
     * Gets internal publication.
     *
     * @return the internal publication
     */
    public String getInternalPublication() {
        return internalPublication;
    }

    /**
     * Sets internal publication.
     *
     * @param internalPublication the internal publication
     */
    public void setInternalPublication(String internalPublication) {
        this.internalPublication = internalPublication;
    }

    /**
     * Gets source.
     *
     * @return the source
     */
    public String getSource() {
        return source;
    }

    /**
     * Sets source.
     *
     * @param source the source
     */
    public void setSource(String source) {
        this.source = source;
    }
}
