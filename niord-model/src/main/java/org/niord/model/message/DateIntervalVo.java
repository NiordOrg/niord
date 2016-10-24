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

    Boolean allDay;
    Date fromDate;
    Date toDate;

    /** Returns a filtered copy of this entity **/
    @SuppressWarnings("all")
    public DateIntervalVo copy() {
        DateIntervalVo dateInterval = new DateIntervalVo();
        dateInterval.setAllDay(allDay);
        dateInterval.setFromDate(fromDate);
        dateInterval.setToDate(toDate);
        return dateInterval;
    }

    /** Returns if the given date is within this interval **/
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
    /*************************/

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
