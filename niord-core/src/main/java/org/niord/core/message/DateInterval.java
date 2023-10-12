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
import org.niord.core.model.IndexedEntity;
import org.niord.model.message.DateIntervalVo;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.validation.constraints.NotNull;
import java.util.Date;

/**
 * Represents a single date interval for a message
 */
@Entity
@SuppressWarnings("unused")
public class DateInterval extends BaseEntity<Integer> implements IndexedEntity, Comparable<DateInterval> {

    @NotNull
    @ManyToOne
    MessagePart messagePart;

    int indexNo;

    /**
     * The all-day flag can be used to signal to the UI that the date interval must be treated as an all-day interval.
     * So, the editor will force the time part of fromDate to "00:00:00" and the time part of toDate to "23:59:59"
     * in the current time zone.<br>
     * Similarly, when presenting the message details, only print out the date part and not the time part.<br>
     */
    Boolean allDay;

    @Temporal(TemporalType.TIMESTAMP)
    Date fromDate;

    @Temporal(TemporalType.TIMESTAMP)
    Date toDate;

    /** Constructor */
    public DateInterval() {
    }


    /** Constructor */
    public DateInterval(Boolean allDay, Date fromDate, Date toDate) {
        this.allDay = allDay;
        this.fromDate = fromDate;
        this.toDate = toDate;
    }


    /** Constructor */
    public DateInterval(DateIntervalVo dateInterval) {
        this(dateInterval.getAllDay(), dateInterval.getFromDate(), dateInterval.getToDate());
    }


    /** Updates this date interval from another **/
    public void updateDateInterval(DateInterval dateInterval) {
        this.indexNo = dateInterval.getIndexNo();
        this.allDay = dateInterval.getAllDay();
        this.fromDate = dateInterval.getFromDate();
        this.toDate = dateInterval.getToDate();
    }


    /** Converts this entity to a value object */
    @SuppressWarnings("all")
    public DateIntervalVo toVo() {
        DateIntervalVo dateInterval = new DateIntervalVo();
        dateInterval.setAllDay(allDay);
        dateInterval.setFromDate(fromDate);
        dateInterval.setToDate(toDate);
        return dateInterval;
    }


    /** Returns if the date interval is properly defined **/
    public boolean dateIntervalDefined() {
        // To-date must not be before from-date
        if (fromDate != null && toDate != null && toDate.before(fromDate)) {
            toDate = fromDate;
        }
        return fromDate != null || toDate != null;
    }


    /** Utility method that returns if the date interval is open-ended **/
    public boolean openEnded() {
        return fromDate != null && toDate == null;
    }


    /** {@inheritDoc} **/
    @Override
    @SuppressWarnings("all")
    public int compareTo(DateInterval di) {
        if (fromDate == null && di.fromDate == null) {
            return 0;
        } else if (fromDate == null) {
            return -1;  // NB: null from-date before any other dates
        } else if (di.fromDate == null) {
            return 1;
        } else {
            int result = fromDate.compareTo(di.fromDate);
            if (result == 0) {
                if (toDate == null && di.toDate == null) {
                    return 0;
                } else if (toDate == null) {
                    return 1; // NB: null to-date after all other dates
                } else if (di.toDate == null) {
                    return -1;
                }
                result = toDate.compareTo(di.toDate);
            }
            return result;
        }
    }


    /*************************/
    /** Getters and Setters **/
    /***/

    public MessagePart getMessagePart() {
        return messagePart;
    }

    public void setMessagePart(MessagePart messagePart) {
        this.messagePart = messagePart;
    }

    @Override
    public int getIndexNo() {
        return indexNo;
    }

    @Override
    public void setIndexNo(int indexNo) {
        this.indexNo = indexNo;
    }

    public Boolean getAllDay() {
        return allDay;
    }

    public void setAllDay(Boolean allDay) {
        this.allDay = allDay;
    }

    public Date getFromDate() {
        return fromDate;
    }

    public void setFromDate(Date fromDate) {
        this.fromDate = fromDate;
    }

    public Date getToDate() {
        return toDate;
    }

    public void setToDate(Date toDate) {
        this.toDate = toDate;
    }
}
