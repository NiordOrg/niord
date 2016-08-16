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
import org.niord.core.user.User;
import org.niord.core.message.vo.MessageHistoryVo;
import org.niord.model.message.Status;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.PrePersist;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.validation.constraints.NotNull;
import java.util.Date;

/**
 * The {@code MessageHistory} registers the history of a {@code Message} by storing a JSON snapshot
 * of the Message for every change, along with the changing user and time.
 */
@Entity
@NamedQueries({
    @NamedQuery(name = "MessageHistory.findByMessageId",
                query = "SELECT mh FROM MessageHistory mh where mh.message.id = :messageId order by mh.created desc")
})
@SuppressWarnings("unused")
public class MessageHistory extends BaseEntity<Integer> {

    @NotNull
    @ManyToOne
    Message message;

    @NotNull
    @Enumerated(EnumType.STRING)
    Status status;

    @ManyToOne
    User user;

    int version;

    @NotNull
    @Temporal(TemporalType.TIMESTAMP)
    Date created;

    @Lob
    String snapshot;

    @PrePersist
    protected void onCreate() {
        if (created == null) {
            created = new Date();
        }
    }

    /** Converts this entity to a value object */
    public MessageHistoryVo toVo() {
        MessageHistoryVo history = new MessageHistoryVo();
        history.setMessageId(id);
        history.setStatus(status);
        if (user != null) {
            history.setUser(user.getUsername());
        }
        history.setVersion(version);
        history.setCreated(created);
        history.setSnapshot(snapshot);

        return history;
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

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(String snapshot) {
        this.snapshot = snapshot;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

}
