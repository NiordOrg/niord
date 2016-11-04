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

import org.apache.commons.lang.StringUtils;
import org.niord.core.message.vo.MessageSourceDescVo;
import org.niord.core.message.vo.MessageSourceVo;
import org.niord.core.model.BaseEntity;
import org.niord.core.model.IndexedEntity;
import org.niord.model.DataFilter;
import org.niord.model.ILocalizable;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.validation.constraints.NotNull;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Defines a message source
 */
@Entity
@SuppressWarnings("unused")
public class MessageSource extends BaseEntity<Integer> implements ILocalizable<MessageSourceDesc>, IndexedEntity {

    @NotNull
    @ManyToOne
    Message message;

    int indexNo;

    @Temporal(TemporalType.TIMESTAMP)
    Date date;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "entity", orphanRemoval = true)
    List<MessageSourceDesc> descs = new ArrayList<>();


    /** Constructor */
    public MessageSource() {
    }


    /** Constructor */
    public MessageSource(MessageSourceVo source) {

        this.date = source.getDate();

        if (source.getDescs() != null) {
            source.getDescs().stream()
                    .filter(MessageSourceDescVo::descDefined)
                    .forEach(desc -> createDesc(desc.getLang()).setName(desc.getName()));
        }
    }


    /** Updates this message source from another message source */
    public void updateMessageSource(MessageSource source) {
        this.indexNo = source.getIndexNo();
        this.date = source.getDate();
        copyDescsAndRemoveBlanks(source.getDescs());
    }


    /** Converts this entity to a value object */
    public MessageSourceVo toVo(DataFilter filter) {
        MessageSourceVo source = new MessageSourceVo();
        source.setDate(date);
        if (!descs.isEmpty()) {
            source.setDescs(getDescs(filter).stream()
                    .map(MessageSourceDesc::toVo)
                    .collect(Collectors.toList()));
        }
        return source;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public MessageSourceDesc createDesc(String lang) {
        MessageSourceDesc desc = new MessageSourceDesc();
        desc.setLang(lang);
        desc.setEntity(this);
        getDescs().add(desc);
        return desc;
    }


    /**
     * Computes the message source text
     * @param lang the language
     * @return the message source text
     */
    public String computeMessageSource(String lang) {
        MessageSourceDesc desc = getDesc(lang);
        if (desc != null && StringUtils.isNotBlank(desc.getName()) && date != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("d MMMMM yyyy", new Locale(lang));
            return desc.getName().trim() + " " + sdf.format(date);
        }
        return null;
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }

    @Override
    public int getIndexNo() {
        return indexNo;
    }

    @Override
    public void setIndexNo(int indexNo) {
        this.indexNo = indexNo;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    @Override
    public List<MessageSourceDesc> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<MessageSourceDesc> descs) {
        this.descs = descs;
    }
}
