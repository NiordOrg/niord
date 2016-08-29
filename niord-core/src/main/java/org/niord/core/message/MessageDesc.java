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

package org.niord.core.message;

import org.niord.core.model.DescEntity;
import org.niord.model.DataFilter;
import org.niord.model.ILocalizedDesc;
import org.niord.model.message.MessageDescVo;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Lob;

/**
 * Localized contents for the Area entity
 */
@Entity
@SuppressWarnings("unused")
public class MessageDesc extends DescEntity<Message> {

    @Column(length = 1000)
    String title;

    @Column(length = 500)
    String subject;

    @Lob
    String description;

    String otherCategories;

    @Column(length = 1000)
    String time;

    String vicinity;

    @Column(length = 1000)
    String note;

    String publication;

    String source;

    String prohibition;

    @Column(length = 2000)
    String signals;


    /** Constructor */
    public MessageDesc() {
    }

    /** Constructor */
    public MessageDesc(MessageDescVo desc) {
        this(desc, DataFilter.get());
    }

    /** Constructor */
    public MessageDesc(MessageDescVo desc, DataFilter filter) {
        super(desc);

        DataFilter compFilter = filter.forComponent(MessageDesc.class);

        if (compFilter.includeField("title")) {
            this.title = desc.getTitle();
        } else {
            // Copy all fields
            this.lang = desc.getLang();
            this.title = desc.getTitle();
            this.subject = desc.getSubject();
            this.description = desc.getDescription();
            this.otherCategories = desc.getOtherCategories();
            this.time = desc.getTime();
            this.vicinity = desc.getVicinity();
            this.note = desc.getNote();
            this.publication = desc.getPublication();
            this.source = desc.getSource();
            this.prohibition = desc.getProhibition();
            this.signals = desc.getSignals();
        }
    }


    /** Converts this entity to a value object */
    public MessageDescVo toVo(DataFilter filter) {

        DataFilter compFilter = filter.forComponent(MessageDesc.class);

        MessageDescVo desc = new MessageDescVo();
        desc.setLang(lang);
        desc.setTitle(title);
        if (!compFilter.includeField("title")) {
            desc.setSubject(subject);
            desc.setDescription(description);
            desc.setOtherCategories(otherCategories);
            desc.setTime(time);
            desc.setVicinity(vicinity);
            desc.setNote(note);
            desc.setPublication(publication);
            desc.setSource(source);
            desc.setProhibition(prohibition);
            desc.setSignals(signals);
        }
        return desc;
    }


    /** {@inheritDoc} */
    @Override
    public boolean descDefined() {
        return ILocalizedDesc.fieldsDefined(title, subject, description, otherCategories,
                time, vicinity, note, publication, source, prohibition, signals);
    }


    /** {@inheritDoc} */
    @Override
    public void copyDesc(ILocalizedDesc localizedDesc) {
        MessageDesc desc = (MessageDesc)localizedDesc;
        this.lang = desc.getLang();
        this.title = desc.getTitle();
        this.subject = desc.getSubject();
        this.description = desc.getDescription();
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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
