/*
 * Copyright 2017 Danish Maritime Authority.
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
package org.niord.core.script;

import org.niord.core.script.vo.ScriptResourceHistoryVo;
import org.niord.core.model.BaseEntity;
import org.niord.core.user.User;
import org.niord.core.util.GzipUtils;

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

/**
 * The {@code ScriptResourceHistory} registers the history of a {@code ScriptResource} by storing a JSON snapshot
 * of the resource for every change, along with the changing user and time.
 */
@Entity
@NamedQueries({
        @NamedQuery(name = "ScriptResourceHistory.findByResourceId",
                query = "SELECT th FROM ScriptResourceHistory th where th.resource.id = :resourceId order by th.created desc")
})
@SuppressWarnings("unused")
public class ScriptResourceHistory extends BaseEntity<Integer> {

    @NotNull
    @ManyToOne
    ScriptResource resource;

    @ManyToOne
    User user;

    int version;

    @NotNull
    @Temporal(TemporalType.TIMESTAMP)
    Date created;

    @Lob
    byte[] snapshot;

    @PrePersist
    protected void onCreate() {
        if (created == null) {
            created = new Date();
        }
    }

    /** Converts this entity to a value object */
    public ScriptResourceHistoryVo toVo() {
        ScriptResourceHistoryVo history = new ScriptResourceHistoryVo();
        history.setTemplateId(id);
        if (user != null) {
            history.setUser(user.getName());
        }
        history.setVersion(version);
        history.setCreated(created);
        history.setSnapshot(GzipUtils.decompressStringIgnoreError(snapshot));

        return history;
    }


    /** Sets and compresses the given snapshot **/
    public void compressSnapshot(String snapshot) {
        this.snapshot = GzipUtils.compressStringIgnoreError(snapshot);
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    public ScriptResource getResource() {
        return resource;
    }

    public void setResource(ScriptResource template) {
        this.resource = template;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public byte[] getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(byte[] snapshot) {
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
