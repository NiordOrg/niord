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
@XmlType(propOrder = { "title", "otherCategories",
        "time", "vicinity", "note", "publication", "source", "prohibition", "signals" })
@SuppressWarnings("unused")
public class MessageDescVo implements ILocalizedDesc, IJsonSerializable {

    String lang;
    String title;
    String otherCategories;
    String time;
    String vicinity;
    String note;
    String publication;
    String source;
    String prohibition;
    String signals;

    /** {@inheritDoc} */
    @Override
    public boolean descDefined() {
        return ILocalizedDesc.fieldsDefined(title, otherCategories,
                time, vicinity, note, publication, source, prohibition, signals);
    }

    /** {@inheritDoc} */
    @Override
    public void copyDesc(ILocalizedDesc localizedDesc) {
        MessageDescVo desc = (MessageDescVo)localizedDesc;
        this.title = desc.getTitle();
        this.otherCategories = desc.getOtherCategories();
        this.time = desc.getTime();
        this.vicinity = desc.getVicinity();
        this.note = desc.getNote();
        this.publication = desc.getPublication();
        this.source = desc.getSource();
        this.prohibition = desc.getProhibition();
        this.signals = desc.getSignals();
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

    public String getOtherCategories() {
        return otherCategories;
    }

    public void setOtherCategories(String otherCategories) {
        this.otherCategories = otherCategories;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getVicinity() {
        return vicinity;
    }

    public void setVicinity(String vicinity) {
        this.vicinity = vicinity;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getPublication() {
        return publication;
    }

    public void setPublication(String publication) {
        this.publication = publication;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getProhibition() {
        return prohibition;
    }

    public void setProhibition(String prohibition) {
        this.prohibition = prohibition;
    }

    public String getSignals() {
        return signals;
    }

    public void setSignals(String signals) {
        this.signals = signals;
    }
}
