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
package org.niord.model.message;

import io.swagger.annotations.ApiModel;
import org.niord.model.IJsonSerializable;

import javax.xml.bind.annotation.XmlType;
import java.util.Date;

/**
 * Represents a single date interval for a message
 */
@ApiModel(value = "DateInterval", description = "Date interval")
@XmlType(propOrder = {
        "allDay", "fromDate", "toDate"
})
@SuppressWarnings("unused")
public class DateIntervalVo implements IJsonSerializable {

    /**
     * The All day.
     */
    Boolean allDay;
    /**
     * The From date.
     */
    Date fromDate;
    /**
     * The To date.
     */
    Date toDate;

    /**
     * Returns a filtered copy of this entity  @return the date interval vo
     */
    @SuppressWarnings("all")
    public DateIntervalVo copy() {
        DateIntervalVo dateInterval = new DateIntervalVo();
        dateInterval.setAllDay(allDay);
        dateInterval.setFromDate(fromDate);
        dateInterval.setToDate(toDate);
        return dateInterval;
    }

    /**
     * Returns if the given date is within this interval  @param date the date
     *
     * @return the boolean
     */
    public boolean containsDate(Date date) {
        if (fromDate != null && toDate != null) {
            return date.after(fromDate) && date.before(toDate);
        } else if (fromDate != null) {
            return date.after(fromDate);
        } else if (toDate != null) {
            return date.before(toDate);
        }
        return false;
    }

    /*************************/
    /** Getters and Setters **/
    /**
     * Gets all day.
     *
     * @return the all day
     */

    public Boolean getAllDay() {
        return allDay;
    }

    /**
     * Sets all day.
     *
     * @param allDay the all day
     */
    public void setAllDay(Boolean allDay) {
        this.allDay = allDay;
    }

    /**
     * Gets from date.
     *
     * @return the from date
     */
    public Date getFromDate() {
        return fromDate;
    }

    /**
     * Sets from date.
     *
     * @param fromDate the from date
     */
    public void setFromDate(Date fromDate) {
        this.fromDate = fromDate;
    }

    /**
     * Gets to date.
     *
     * @return the to date
     */
    public Date getToDate() {
        return toDate;
    }

    /**
     * Sets to date.
     *
     * @param toDate the to date
     */
    public void setToDate(Date toDate) {
        this.toDate = toDate;
    }
}
