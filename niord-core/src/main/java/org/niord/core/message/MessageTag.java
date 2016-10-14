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
import org.niord.core.domain.Domain;
import org.niord.core.message.vo.MessageTagVo;
import org.niord.core.message.vo.MessageTagVo.MessageTagType;
import org.niord.core.model.VersionedEntity;
import org.niord.core.user.User;

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
        @NamedQuery(name="MessageTag.findTagsByTypeAndName",
                query="SELECT t FROM MessageTag t where t.name in (:names) and t.type = :type"),
        @NamedQuery(name="MessageTag.findTagsByTagIds",
                query="SELECT t FROM MessageTag t where t.tagId in (:tagIds)"),
        @NamedQuery(name= "MessageTag.findTagsByMessageUid",
                query="SELECT t FROM MessageTag t join t.messages m where m.uid = :messageUid and t.type <> 'TEMP'"),
        @NamedQuery(name= "MessageTag.findExpiredMessageTags",
                query="SELECT t FROM MessageTag t where t.expiryDate is not null and t.expiryDate < current_timestamp"),
})
@SuppressWarnings("unused")
public class MessageTag extends VersionedEntity<Integer> implements Comparable<MessageTag> {

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

    // Weak lock mechanism used by UI to prevent changes
    boolean locked;

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
        this.locked = tag.isLocked();
    }


    /** Converts this entity to a value object */
    public MessageTagVo toVo() {
        MessageTagVo tag = new MessageTagVo();
        tag.setTagId(tagId);
        tag.setName(name);
        tag.setCreated(getCreated());
        tag.setExpiryDate(expiryDate);
        tag.setType(type);
        tag.setLocked(locked);
        tag.setMessageCount(messageCount);
        return tag;
    }


    /** If no tag ID is defined, create one **/
    public MessageTag checkAssignTagId() {
        if (StringUtils.isBlank(tagId)) {
            tagId = UUID.randomUUID().toString();
        }
        return this;
    }


    /** Update the number of messages */
    @PrePersist
    @PreUpdate
    public void updateMessageCount() {
        checkAssignTagId();
        if (StringUtils.isBlank(name)) {
            name = UUID.randomUUID().toString();
        }
        messageCount = messages.size();
    }


    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("all")
    public int compareTo(MessageTag t) {
        return t == null ? -1 : name.toLowerCase().compareTo(t.getName().toLowerCase());
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

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    public int getMessageCount() {
        return messageCount;
    }
}
