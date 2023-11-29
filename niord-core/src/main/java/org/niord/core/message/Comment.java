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

import org.niord.core.message.vo.CommentVo;
import org.niord.core.model.BaseEntity;
import org.niord.core.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.validation.constraints.NotNull;
import java.util.*;

/**
 * Represents a comment to a messages. Comments can be acknowledged.
 */
@Entity
@NamedQueries({
        @NamedQuery(name = "Comment.findByMessageUid",
                query = "SELECT c FROM Comment c where c.message.uid = :uid order by c.created desc")
})
@SuppressWarnings("unused")
public class Comment extends BaseEntity<Integer> {

    @NotNull
    @ManyToOne
    Message message;

    @NotNull
    @ManyToOne
    User user;

    @NotNull
    @Temporal(TemporalType.TIMESTAMP)
    Date created;

    @ManyToOne
    User acknowledgedBy;

    @Temporal(TemporalType.TIMESTAMP)
    Date acknowledgeDate;

    @Lob
    @Column(length = 16_777_216) 
    String comment;

    @ElementCollection
    List<String> emailAddresses = new ArrayList<>();


    /**
     * Constructor
     **/
    public Comment() {
    }


    /**
     * Constructor
     **/
    public Comment(CommentVo comment, Message message, User user) {
        this.setId(comment.getId());
        this.message = message;
        this.user = user;
        this.comment = comment.getComment();
        if (comment.getEmailAddresses() != null) {
            this.emailAddresses.addAll(comment.getEmailAddresses());
        }
    }


    /** Converts this entity to a value object */
    public CommentVo toVo() {
        CommentVo vo = new CommentVo();
        vo.setId(this.getId());
        vo.setUser(user.getName());
        vo.setCreated(created);
        vo.setAcknowledgedBy(acknowledgedBy != null ? acknowledgedBy.getName() : null);
        vo.setAcknowledgeDate(acknowledgeDate);
        vo.setComment(comment);
        if (!emailAddresses.isEmpty()) {
            vo.setEmailAddresses(new ArrayList<>(emailAddresses));
        }
        return vo;
    }


    /** Converts this entity to a value object. Also flags if the user is the author of the comment */
    public CommentVo toVo(User user) {
        CommentVo vo = toVo();
        vo.setOwnComment(user != null && Objects.equals(user.getId(), this.user.getId()));
        return vo;
    }


    @PrePersist
    protected void onCreate() {
        if (created == null) {
            created = new Date();
        }
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

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public User getAcknowledgedBy() {
        return acknowledgedBy;
    }

    public void setAcknowledgedBy(User acknowledgedBy) {
        this.acknowledgedBy = acknowledgedBy;
    }

    public Date getAcknowledgeDate() {
        return acknowledgeDate;
    }

    public void setAcknowledgeDate(Date acknowledgeDate) {
        this.acknowledgeDate = acknowledgeDate;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public List<String> getEmailAddresses() {
        return emailAddresses;
    }

    public void setEmailAddresses(List<String> emailAddresses) {
        this.emailAddresses = emailAddresses;
    }
}
