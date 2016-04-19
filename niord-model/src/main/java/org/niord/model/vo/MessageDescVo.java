/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.niord.model.vo;

import io.swagger.annotations.ApiModel;
import org.niord.model.IJsonSerializable;
import org.niord.model.ILocalizedDesc;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;

/**
 * The message description VO
 */
@ApiModel(value = "MessageDesc", description = "Translatable fields of the Message model")
@XmlType(propOrder = { "title", "subject", "description", "otherCategories",
        "time", "vicinity", "note", "publication", "source" })
@SuppressWarnings("unused")
public class MessageDescVo implements ILocalizedDesc, IJsonSerializable {

    String lang;
    String title;
    String subject;
    String description;
    String otherCategories;
    String time;
    String vicinity;
    String note;
    String publication;
    String source;

    /** {@inheritDoc} */
    @Override
    public boolean descDefined() {
        return ILocalizedDesc.fieldsDefined(title, subject, description, otherCategories,
                time, vicinity, note, publication, source);
    }

    /** {@inheritDoc} */
    @Override
    public void copyDesc(ILocalizedDesc localizedDesc) {
        MessageDescVo desc = (MessageDescVo)localizedDesc;
        this.title = desc.getTitle();
        this.subject = desc.getSubject();
        this.description = desc.getDescription();
        this.otherCategories = desc.getOtherCategories();
        this.time = desc.getTime();
        this.vicinity = desc.getVicinity();
        this.note = desc.getNote();
        this.publication = desc.getPublication();
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
}
