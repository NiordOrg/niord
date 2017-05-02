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

import org.niord.core.mailinglist.ScheduleType;
import org.niord.core.mailinglist.TriggerType;
import org.niord.model.IJsonSerializable;
import org.niord.model.ILocalizable;
import org.niord.model.message.Status;

import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Defines the value object of a mailing list trigger.
 */
@SuppressWarnings("unused")
public class MailingListTriggerVo implements ILocalizable<MailingListTriggerDescVo>, IJsonSerializable {

    TriggerType type;
    ScheduleType scheduleType;
    Date scheduledTimeOfDay;
    Set<Status> statusChanges;
    String messageQuery;
    String messageFilter;
    List<String> scriptResourcePaths;
    List<MailingListTriggerDescVo> descs;


    /** {@inheritDoc} */
    @Override
    public MailingListTriggerDescVo createDesc(String lang) {
        MailingListTriggerDescVo desc = new MailingListTriggerDescVo();
        desc.setLang(lang);
        checkCreateDescs().add(desc);
        return desc;
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    public TriggerType getType() {
        return type;
    }

    public void setType(TriggerType type) {
        this.type = type;
    }

    public ScheduleType getScheduleType() {
        return scheduleType;
    }

    public void setScheduleType(ScheduleType scheduleType) {
        this.scheduleType = scheduleType;
    }

    public Date getScheduledTimeOfDay() {
        return scheduledTimeOfDay;
    }

    public void setScheduledTimeOfDay(Date scheduledTimeOfDay) {
        this.scheduledTimeOfDay = scheduledTimeOfDay;
    }

    public Set<Status> getStatusChanges() {
        return statusChanges;
    }

    public void setStatusChanges(Set<Status> statusChanges) {
        this.statusChanges = statusChanges;
    }

    public String getMessageQuery() {
        return messageQuery;
    }

    public void setMessageQuery(String messageQuery) {
        this.messageQuery = messageQuery;
    }

    public String getMessageFilter() {
        return messageFilter;
    }

    public void setMessageFilter(String messageFilter) {
        this.messageFilter = messageFilter;
    }

    public List<String> getScriptResourcePaths() {
        return scriptResourcePaths;
    }

    public void setScriptResourcePaths(List<String> scriptResourcePaths) {
        this.scriptResourcePaths = scriptResourcePaths;
    }

    @Override
    public List<MailingListTriggerDescVo> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<MailingListTriggerDescVo> descs) {
        this.descs = descs;
    }
}
