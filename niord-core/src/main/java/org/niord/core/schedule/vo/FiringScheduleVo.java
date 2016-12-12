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

package org.niord.core.schedule.vo;

import org.niord.core.domain.vo.DomainVo;
import org.niord.model.IJsonSerializable;

import java.util.List;

/**
 * Defines a firing schedule value object
 */
@SuppressWarnings("unused")
public class FiringScheduleVo implements IJsonSerializable {

    Integer id;
    DomainVo domain;
    DomainVo targetDomain;
    String targetSeriesId;
    List<String> messageFields;
    Integer scheduleDays;
    boolean active;

    @Override
    public String toString() {
        return "FiringScheduleVo{" +
                "id=" + id +
                ", domain=" + domain.getDomainId() +
                ", targetDomain=" + targetDomain.getDomainId() +
                ", targetSeriesId='" + targetSeriesId + '\'' +
                ", messageFields=" + messageFields +
                ", scheduleDays=" + scheduleDays +
                ", active=" + active +
                '}';
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public DomainVo getDomain() {
        return domain;
    }

    public void setDomain(DomainVo domain) {
        this.domain = domain;
    }

    public DomainVo getTargetDomain() {
        return targetDomain;
    }

    public void setTargetDomain(DomainVo targetDomain) {
        this.targetDomain = targetDomain;
    }

    public String getTargetSeriesId() {
        return targetSeriesId;
    }

    public void setTargetSeriesId(String targetSeriesId) {
        this.targetSeriesId = targetSeriesId;
    }

    public List<String> getMessageFields() {
        return messageFields;
    }

    public void setMessageFields(List<String> messageFields) {
        this.messageFields = messageFields;
    }

    public Integer getScheduleDays() {
        return scheduleDays;
    }

    public void setScheduleDays(Integer scheduleDays) {
        this.scheduleDays = scheduleDays;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
