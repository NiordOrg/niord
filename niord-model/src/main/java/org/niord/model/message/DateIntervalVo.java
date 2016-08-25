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
public class DateIntervalVo implements IJsonSerializable {

    Boolean allDay;
    Date fromDate;
    Date toDate;

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