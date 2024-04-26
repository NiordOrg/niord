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
package org.niord.core.model;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Date;

/**
 * Base class for versioned entity beans.
 *
 * The created and updated fields will automatically be updated upon persisting the entity.
 */
@MappedSuperclass
public abstract class VersionedEntity<ID extends Serializable> extends BaseEntity<ID> {

    @Version
    @Column(name="version")
    int version;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name="created")
    Date created;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name="updated")
    Date updated;

    @PrePersist
    protected void onCreate() {
        updated = new Date();
        if (created == null) {
            created = updated;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updated = new Date();
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

    public Date getUpdated() {
        return updated;
    }

    public void setUpdated(Date updated) {
        this.updated = updated;
    }
}
