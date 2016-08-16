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

import org.niord.core.message.vo.CommentVo;
import org.niord.core.model.BaseEntity;
import org.niord.core.user.User;

import javax.persistence.Entity;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.PrePersist;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.validation.constraints.NotNull;
import java.util.Date;
import java.util.Objects;

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
    String comment;


    /**
     * Constructor
     **/
    public Comment() {
    }


    /**
     * Constructor
     **/
    public Comment(CommentVo comment, Message message, User user) {
        this.id = comment.getId();
        this.message = message;
        this.user = user;
        this.comment = comment.getComment();
    }


    /** Converts this entity to a value object */
    public CommentVo toVo() {
        CommentVo vo = new CommentVo();
        vo.setId(id);
        vo.setUser(user.getName());
        vo.setCreated(created);
        vo.setAcknowledgedBy(acknowledgedBy != null ? acknowledgedBy.getName() : null);
        vo.setAcknowledgeDate(acknowledgeDate);
        vo.setComment(comment);
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
}
