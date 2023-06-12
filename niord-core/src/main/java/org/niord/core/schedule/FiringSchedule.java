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

package org.niord.core.schedule;

import org.niord.core.domain.Domain;
import org.niord.core.message.MessageSeries;
import org.niord.core.model.VersionedEntity;
import org.niord.core.schedule.vo.FiringScheduleVo;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.validation.constraints.NotNull;
import java.util.TimeZone;

/**
 * Defines a firing schedule.
 * <p>
 * A firing schedule is tied to a domain and has a target domain and message series ID.
 * <p>
 * The firing schedule will be "executed" periodically, meaning that for each published message in
 * the associated domain that is tied to a firing area, the firing periods for the area will
 * be used to generate and maintain "firing exercise" messages in the target message series.
 */
@Entity
@SuppressWarnings("unused")
public class FiringSchedule extends VersionedEntity<Integer> {

    /** The domain defines the messages used as firing area templates with associated firing periods **/
    @OneToOne
    @NotNull
    Domain domain;

    /**
     * The target domain defines the domain where the firing exercise messages will be generated and maintained.
     * NB: We use target domain as well as target message series, because we need the timezone associated with the domain
     **/
    @ManyToOne
    @NotNull
    Domain targetDomain;

    /** Defines the target message series of the target domain where the firing exercise messages will be generated **/
    @ManyToOne
    @NotNull
    MessageSeries targetMessageSeries;

    /** Number of scheduled days to include in the generated firing exercise messages **/
    Integer scheduleDays;

    /** If the schedule is active or inactive **/
    boolean active;


    /** Constructor */
    public FiringSchedule() {
    }


    /** Constructor */
    public FiringSchedule(FiringScheduleVo schedule) {
        this.setId(schedule.getId());
        this.domain = new Domain(schedule.getDomain());
        this.targetDomain = new Domain(schedule.getTargetDomain());
        this.targetMessageSeries = new MessageSeries(schedule.getTargetSeriesId());
        this.scheduleDays = schedule.getScheduleDays();
        this.active = schedule.isActive();
    }


    /** Converts this entity to a value object */
    public FiringScheduleVo toVo() {
        FiringScheduleVo schedule = new FiringScheduleVo();
        schedule.setId(this.getId());
        schedule.setDomain(domain.toVo());
        schedule.setTargetDomain(targetDomain.toVo());
        schedule.setTargetSeriesId(targetMessageSeries.getSeriesId());
        schedule.setScheduleDays(scheduleDays);
        schedule.setActive(active);
        return schedule;
    }


    /**
     * Computes the target time zone to use
     * @return the target time zone to use
     */
    public TimeZone targetTimeZone() {
        return targetDomain != null ? targetDomain.timeZone() : TimeZone.getDefault();
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    public Domain getDomain() {
        return domain;
    }

    public void setDomain(Domain domain) {
        this.domain = domain;
    }

    public Domain getTargetDomain() {
        return targetDomain;
    }

    public void setTargetDomain(Domain targetDomain) {
        this.targetDomain = targetDomain;
    }

    public MessageSeries getTargetMessageSeries() {
        return targetMessageSeries;
    }

    public void setTargetMessageSeries(MessageSeries targetMessageSeries) {
        this.targetMessageSeries = targetMessageSeries;
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
