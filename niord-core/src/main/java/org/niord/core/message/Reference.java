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
package org.niord.core.message;

import org.niord.core.model.BaseEntity;
import org.niord.core.model.IndexedEntity;
import org.niord.model.vo.ReferenceType;
import org.niord.model.vo.ReferenceVo;

import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

/**
 * Defines a reference from one Message to another
 */
@Entity
@Table(indexes = {
        @Index(name = "reference_message_id_k", columnList="messageId")
})
@SuppressWarnings("unused")
public class Reference extends BaseEntity<Integer> implements IndexedEntity {

    @ManyToOne
    Message message;

    int indexNo;

    // May be either MRN (globally unique) or short ID (within same message series)
    @NotNull
    String messageId;

    @NotNull
    ReferenceType type;

    String description;

    /** Constructor */
    public Reference() {
    }


    /** Constructor */
    public Reference(ReferenceVo reference) {
        this.messageId = reference.getMessageId();
        this.type = reference.getType();
        this.description = reference.getDescription();
    }


    /** Converts this entity to a value object */
    public ReferenceVo toVo() {
        ReferenceVo reference = new ReferenceVo();
        reference.setMessageId(messageId);
        reference.setType(type);
        reference.setDescription(description);
        return reference;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
