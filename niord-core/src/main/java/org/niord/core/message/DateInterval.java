/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.niord.core.message;

import org.niord.core.model.BaseEntity;
import org.niord.core.model.IndexedEntity;
import org.niord.model.vo.DateIntervalVo;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.validation.constraints.NotNull;
import java.util.Date;

/**
 * Represents a single date interval for a message
 */
@Entity
@SuppressWarnings("unused")
public class DateInterval extends BaseEntity<Integer> implements IndexedEntity, Comparable<DateInterval> {

    @NotNull
    @ManyToOne
    Message message;

    int indexNo;

    /**
     * The all-day flag can be used to signal to the UI that the date interval must be treated as an al-day
     * interval. So, the editor will force the time part of fromDate to "00:00:00" and the time part
     * of toDate to "23:59:59" in the current time zone.<br>
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
    public DateInterval(DateIntervalVo dateInterval) {
        this.allDay = dateInterval.getAllDay();
        this.fromDate = dateInterval.getFromDate();
        this.toDate = dateInterval.getToDate();
    }


    /** Converts this entity to a value object */
    public DateIntervalVo toVo() {
        DateIntervalVo dateInterval = new DateIntervalVo();
        dateInterval.setAllDay(allDay);
        dateInterval.setFromDate(fromDate);
        dateInterval.setToDate(toDate);
        return dateInterval;
    }


    /** Check that the date interval is valid */
    @PrePersist
    @PreUpdate
    public void checkDateInterval() {
        // To-date must not be before from-date
        if (fromDate != null && toDate != null && toDate.before(fromDate)) {
            toDate = fromDate;
        }
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
    /*************************/

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
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
