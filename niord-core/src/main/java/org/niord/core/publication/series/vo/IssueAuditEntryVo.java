/*
 * Copyright 2026 Danish Emergency Management Agency.
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

package org.niord.core.publication.series.vo;

import java.util.Date;
import org.niord.model.IJsonSerializable;

/**
 * One line of the Historik panel.
 */
public class IssueAuditEntryVo implements IJsonSerializable {

    private Integer id;

    private String action;

    private String actorKind;

    private String actorLabel;

    private Date created;

    private String reason;

    private String archivePath;

    private Object detail;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getActorKind() {
        return actorKind;
    }

    public void setActorKind(String actorKind) {
        this.actorKind = actorKind;
    }

    public String getActorLabel() {
        return actorLabel;
    }

    public void setActorLabel(String actorLabel) {
        this.actorLabel = actorLabel;
    }

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getArchivePath() {
        return archivePath;
    }

    public void setArchivePath(String archivePath) {
        this.archivePath = archivePath;
    }

    public Object getDetail() {
        return detail;
    }

    public void setDetail(Object detail) {
        this.detail = detail;
    }

}
