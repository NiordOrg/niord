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
