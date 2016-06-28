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

import org.apache.commons.lang.StringUtils;
import org.niord.core.domain.Domain;
import org.niord.core.model.BaseEntity;
import org.niord.core.user.User;
import org.niord.model.vo.MessageTagVo;
import org.niord.model.vo.MessageTagVo.MessageTagType;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Index;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Tags represents a named collection of messages.
 * They may be private, i.e. only accessible for the user that created it, tied to a domain or public.
 * Also, they may have an expiry date, after which they are purged.
 */
@Entity
@Table(
    indexes = {
            @Index(name = "message_tag_type_k", columnList="type"),
            @Index(name = "message_tag_id_k", columnList="tagId")
    }
)
@NamedQueries({
        @NamedQuery(name="MessageTag.findByUser",
                query="SELECT t FROM MessageTag t where t.type = 'PRIVATE' and t.user = :user"),
        @NamedQuery(name="MessageTag.findByDomain",
                query="SELECT t FROM MessageTag t where t.type = 'DOMAIN' and t.domain = :domain"),
        @NamedQuery(name="MessageTag.findPublic",
                query="SELECT t FROM MessageTag t where t.type = 'PUBLIC'"),
        @NamedQuery(name="MessageTag.findTagsByTagIds",
                query="SELECT t FROM MessageTag t where t.tagId in (:tagIds)"),
        @NamedQuery(name= "MessageTag.findTagsByMessageUid",
                query="SELECT t FROM MessageTag t join t.messages m where t.tagId in (:tagIds) and m.uid = :messageUid and m.type <> 'TEMP'"),
        @NamedQuery(name= "MessageTag.findExpiredMessageTags",
                query="SELECT t FROM MessageTag t where t.expiryDate is not null and t.expiryDate < current_timestamp"),
})
@SuppressWarnings("unused")
public class MessageTag extends BaseEntity<Integer> implements Comparable<MessageTag> {

    @Column(unique = true, nullable = false)
    String tagId;

    @NotNull
    @Enumerated(EnumType.STRING)
    MessageTagType type;

    @NotNull
    String name;

    @ManyToOne
    User user;

    @ManyToOne
    Domain domain;

    @Temporal(TemporalType.TIMESTAMP)
    Date expiryDate;

    @ManyToMany
    List<Message> messages = new ArrayList<>();

    // More efficient than counting related messages
    @Column(columnDefinition="INT default 0")
    int messageCount;

    /**
     * Constructor
     */
    public MessageTag() {
    }

    /**
     * Constructor
     */
    public MessageTag(MessageTagVo tag) {
        this.tagId = tag.getTagId();
        this.name = tag.getName();
        this.type = tag.getType();
        this.expiryDate = tag.getExpiryDate();
    }


    /** Converts this entity to a value object */
    public MessageTagVo toVo() {
        MessageTagVo tag = new MessageTagVo();
        tag.setTagId(tagId);
        tag.setName(name);
        tag.setExpiryDate(expiryDate);
        tag.setType(type);
        tag.setMessageCount(messageCount);
        return tag;
    }


    /** Update the number of messages */
    @PrePersist
    @PreUpdate
    public void updateMessageCount() {
        if (StringUtils.isBlank(tagId)) {
            tagId = UUID.randomUUID().toString();
        }
        if (StringUtils.isBlank(name)) {
            name = UUID.randomUUID().toString();
        }
        messageCount = messages.size();
    }


    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("all")
    public int compareTo(MessageTag t) {
        return t == null ? -1 : tagId.toLowerCase().compareTo(t.getTagId().toLowerCase());
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getTagId() {
        return tagId;
    }

    public void setTagId(String tagId) {
        this.tagId = tagId;
    }

    public MessageTagType getType() {
        return type;
    }

    public void setType(MessageTagType type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Domain getDomain() {
        return domain;
    }

    public void setDomain(Domain domain) {
        this.domain = domain;
    }

    public Date getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(Date expiryDate) {
        this.expiryDate = expiryDate;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    public int getMessageCount() {
        return messageCount;
    }
}
