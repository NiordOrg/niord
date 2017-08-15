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

package org.niord.core.integration.vo;

import org.niord.model.IJsonSerializable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Value object for the {@code NiordIntegration} class
 */
@SuppressWarnings("unused")
public class NiordIntegrationVo implements IJsonSerializable {

    Integer id;
    String url;
    boolean active;
    boolean assignNewUids;
    boolean createBaseData;
    List<MessageSeriesMappingVo> messageSeriesMappings = new ArrayList<>();
    Date nextScheduledExecution;

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isAssignNewUids() {
        return assignNewUids;
    }

    public void setAssignNewUids(boolean assignNewUids) {
        this.assignNewUids = assignNewUids;
    }

    public boolean isCreateBaseData() {
        return createBaseData;
    }

    public void setCreateBaseData(boolean createBaseData) {
        this.createBaseData = createBaseData;
    }

    public List<MessageSeriesMappingVo> getMessageSeriesMappings() {
        return messageSeriesMappings;
    }

    public void setMessageSeriesMappings(List<MessageSeriesMappingVo> messageSeriesMappings) {
        this.messageSeriesMappings = messageSeriesMappings;
    }

    public Date getNextScheduledExecution() {
        return nextScheduledExecution;
    }

    public void setNextScheduledExecution(Date nextScheduledExecution) {
        this.nextScheduledExecution = nextScheduledExecution;
    }
}
