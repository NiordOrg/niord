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

package org.niord.core.mailinglist.vo;

import org.niord.core.mailinglist.MailingList;
import org.niord.core.mailinglist.MailingListTrigger;
import org.niord.core.mailinglist.ScheduleType;
import org.niord.core.mailinglist.TriggerType;
import org.niord.model.DataFilter;
import org.niord.model.IJsonSerializable;

/**
 * Encapsulates a SCHEDULED mailing list trigger that has the "publicReport" flag set, and the parent mailing list.
 */
@SuppressWarnings("unused")
public class MailingListReportVo implements IJsonSerializable {

    Integer id;
    String name;
    ScheduleType scheduleType;
    String scheduledExecutionTime;
    String scheduledExecutionTimeZone;
    String lang;

    public MailingListReportVo() {
    }


    public MailingListReportVo(MailingListTrigger trigger, String lang) {
        MailingList mailingList = trigger.getMailingList();

        // Check that this is a valid mailing list report
        if (trigger.getType() != TriggerType.SCHEDULED || trigger.getPublicReport() != Boolean.TRUE) {
            throw new IllegalArgumentException("Trigger " + trigger.getId() + " cannot be used as a public report");
        }

        this.lang = lang;
        this.id = trigger.getId();
        this.name = mailingList.getDescs(DataFilter.get().lang(lang)).get(0).getName();
        this.scheduleType = trigger.getScheduleType();
        this.scheduledExecutionTime = trigger.getScheduledExecutionTime();
        this.scheduledExecutionTimeZone = trigger.getScheduledExecutionTimeZone();
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public ScheduleType getScheduleType() {
        return scheduleType;
    }

    public void setScheduleType(ScheduleType scheduleType) {
        this.scheduleType = scheduleType;
    }

    public String getScheduledExecutionTime() {
        return scheduledExecutionTime;
    }

    public void setScheduledExecutionTime(String scheduledExecutionTime) {
        this.scheduledExecutionTime = scheduledExecutionTime;
    }

    public String getScheduledExecutionTimeZone() {
        return scheduledExecutionTimeZone;
    }

    public void setScheduledExecutionTimeZone(String scheduledExecutionTimeZone) {
        this.scheduledExecutionTimeZone = scheduledExecutionTimeZone;
    }
}
