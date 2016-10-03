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

/**
 * Localized contents for the Area entity
 */
@Entity
@SuppressWarnings("unused")
public class MessageDesc extends DescEntity<Message> {

    @Column(length = 1000)
    String title;

    String otherCategories;

    @Column(length = 1000)
    String time;

    String vicinity;

    String publication;

    String source;

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
            this.title = trunc(desc.getTitle(), 1000);
            this.otherCategories = trunc(desc.getOtherCategories(), 256);
            this.time = trunc(desc.getTime(), 1000);
            this.vicinity = trunc(desc.getVicinity(), 256);
            this.publication = trunc(desc.getPublication(), 256);
            this.source = trunc(desc.getSource(), 256);
        }
    }


    /** Converts this entity to a value object */
    public MessageDescVo toVo(DataFilter filter) {

        DataFilter compFilter = filter.forComponent(MessageDesc.class);

        MessageDescVo desc = new MessageDescVo();
        desc.setLang(lang);
        desc.setTitle(title);
        if (!compFilter.includeField("title")) {
            desc.setOtherCategories(otherCategories);
            desc.setTime(time);
            desc.setVicinity(vicinity);
            desc.setPublication(publication);
            desc.setSource(source);
        }
        return desc;
    }


    /** {@inheritDoc} */
    @Override
    public boolean descDefined() {
        return ILocalizedDesc.fieldsDefined(title, otherCategories,
                time, vicinity, publication, source);
    }


    /** {@inheritDoc} */
    @Override
    public void copyDesc(ILocalizedDesc localizedDesc) {
        MessageDesc desc = (MessageDesc)localizedDesc;
        this.lang = desc.getLang();
        this.title = desc.getTitle();
        this.otherCategories = desc.getOtherCategories();
        this.time = desc.getTime();
        this.vicinity = desc.getVicinity();
        this.publication = desc.getPublication();
        this.source = desc.getSource();
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
}
