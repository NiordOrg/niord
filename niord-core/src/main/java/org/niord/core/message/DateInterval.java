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
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.validation.constraints.NotNull;
import java.util.Date;

/**
 * Represents a single date interval for a message
 */
@Entity
public class DateInterval extends BaseEntity<Integer> implements IndexedEntity {

    @NotNull
    @ManyToOne
    Message message;

    int indexNo;

    @NotNull
    @Temporal(TemporalType.TIMESTAMP)
    Date fromDate;

    @Temporal(TemporalType.TIMESTAMP)
    Date toDate;

    /** Constructor */
    public DateInterval() {
    }


    /** Constructor */
    public DateInterval(DateIntervalVo dateInterval) {
        this.fromDate = dateInterval.getFromDate();
        this.toDate = dateInterval.getToDate();
    }


    /** Converts this entity to a value object */
    public DateIntervalVo toVo() {
        DateIntervalVo dateInterval = new DateIntervalVo();
        dateInterval.setFromDate(fromDate);
        dateInterval.setToDate(toDate);
        return dateInterval;
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
