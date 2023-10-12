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

import org.niord.core.model.BaseEntity;
import org.niord.core.model.IndexedEntity;
import org.niord.model.DataFilter;
import org.niord.model.ILocalizable;
import org.niord.model.message.ReferenceDescVo;
import org.niord.model.message.ReferenceType;
import org.niord.model.message.ReferenceVo;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Defines a reference from one Message to another
 */
@Entity
@Table(indexes = {
        @Index(name = "reference_message_id_k", columnList="messageId")
})
@SuppressWarnings("unused")
public class Reference extends BaseEntity<Integer> implements ILocalizable<ReferenceDesc>, IndexedEntity {

    @NotNull
    @ManyToOne
    Message message;

    int indexNo;

    // Weak message ID reference, i.e. either a UID or a short ID of another message
    @NotNull
    String messageId;

    @NotNull
    ReferenceType type;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "entity", orphanRemoval = true)
    List<ReferenceDesc> descs = new ArrayList<>();


    /** Constructor */
    public Reference() {
    }


    /** Constructor */
    public Reference(ReferenceVo reference) {
        this.messageId = reference.getMessageId();
        this.type = reference.getType();

        if (reference.getDescs() != null) {
            reference.getDescs().stream()
                    .filter(ReferenceDescVo::descDefined)
                    .forEach(desc -> createDesc(desc.getLang()).setDescription(desc.getDescription()));
        }
    }


    /** Updates this reference from another reference */
    public void updateReference(Reference reference) {
        this.indexNo = reference.getIndexNo();
        this.messageId = reference.getMessageId();
        this.type = reference.getType();
        copyDescsAndRemoveBlanks(reference.getDescs());
    }


    /** Converts this entity to a value object */
    public ReferenceVo toVo(DataFilter filter) {
        ReferenceVo reference = new ReferenceVo();
        reference.setMessageId(messageId);
        reference.setType(type);
        if (!descs.isEmpty()) {
            reference.setDescs(getDescs(filter).stream()
                    .map(ReferenceDesc::toVo)
                    .collect(Collectors.toList()));
        }
        return reference;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public ReferenceDesc createDesc(String lang) {
        ReferenceDesc desc = new ReferenceDesc();
        desc.setLang(lang);
        desc.setEntity(this);
        getDescs().add(desc);
        return desc;
    }


    /*************************/
    /** Getters and Setters **/
    /***/

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

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public ReferenceType getType() {
        return type;
    }

    public void setType(ReferenceType type) {
        this.type = type;
    }

    @Override
    public List<ReferenceDesc> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<ReferenceDesc> descs) {
        this.descs = descs;
    }

}
