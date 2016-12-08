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

package org.niord.core.area;

import org.niord.core.area.vo.FiringPeriodVo;
import org.niord.core.model.VersionedEntity;

import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Transient;
import javax.validation.constraints.NotNull;
import java.util.Date;

/**
 * Areas with the type AreaType.FIRING_AREA may have an associated schedule of firing periods
 */
@Entity
@Table(indexes = {
        @Index(name = "firing_period_from_date", columnList="fromDate"),
        @Index(name = "firing_period_to_date", columnList="toDate")
})
@NamedQueries({
        @NamedQuery(name="FiringPeriod.findByArea",
                query = "select fp FROM FiringPeriod fp where fp.area = :area order by fp.fromDate, fp.toDate"),
        @NamedQuery(name="FiringPeriod.findLegacyFiringPeriods",
                query = "select fp FROM FiringPeriod fp where fp.legacyId is not null order by fp.fromDate, fp.toDate"),
        @NamedQuery(name="FiringPeriod.findByAreaAndInterval",
                query = "select fp FROM FiringPeriod fp where fp.area = :area "
                        + " and fp.fromDate = :fromDate and fp.toDate = :toDate"),
        @NamedQuery(name="FiringPeriod.findByDateInterval",
                query = "select fp FROM FiringPeriod fp where fp.fromDate <= :toDate and fp.toDate >= :fromDate "
                        + " and fp.area.active in (:active) "
                        + " order by fp.area, fp.fromDate, fp.toDate")
})
@SuppressWarnings("unused")
public class FiringPeriod extends VersionedEntity<Integer> implements Comparable<FiringPeriod> {

    String legacyId;

    @NotNull
    @ManyToOne
    Area area;

    @NotNull
    @Temporal(TemporalType.TIMESTAMP)
    Date fromDate;

    @NotNull
    @Temporal(TemporalType.TIMESTAMP)
    Date toDate;

    /** Constructor */
    public FiringPeriod() {
    }


    /** Constructor */
    public FiringPeriod(FiringPeriodVo firingPeriod) {
        this(null, firingPeriod);
    }


    /** Constructor */
    public FiringPeriod(Area area, FiringPeriodVo firingPeriod) {
        this.area = area;
        this.id = firingPeriod.getId();
        this.legacyId = firingPeriod.getLegacyId();
        this.fromDate = firingPeriod.getFromDate();
        this.toDate = firingPeriod.getToDate();
    }


    /**
     * Updates this entity from another
     * @param firingPeriod the firing period to update this from
     */
    public void updateFiringPeriod(FiringPeriod firingPeriod) {
        this.legacyId = firingPeriod.getLegacyId();
        this.fromDate = firingPeriod.getFromDate();
        this.toDate = firingPeriod.getToDate();
    }


    /** Converts this entity to a value object */
    @SuppressWarnings("all")
    public FiringPeriodVo toVo() {
        FiringPeriodVo firingPeriod = new FiringPeriodVo();
        firingPeriod.setId(id);
        firingPeriod.setLegacyId(legacyId);
        firingPeriod.setAreaId(area.getId());
        firingPeriod.setFromDate(fromDate);
        firingPeriod.setToDate(toDate);
        return firingPeriod;
    }


    /**
     * Checks if the time interval defined by this firing period has changed
     * @param firingPeriod the firing period to compare with
     * @return if the time interval defined by this firing period has changed
     */
    @Transient
    public boolean hasChanged(FiringPeriod firingPeriod) {
        return fromDate.getTime() !=  firingPeriod.getFromDate().getTime()
                || toDate.getTime() != firingPeriod.getToDate().getTime();
    }

    /** Returns if the firing period is properly defined **/
    public boolean firingPeriodDefined() {
        return area != null && fromDate != null && toDate != null;
    }


    /** {@inheritDoc} **/
    @Override
    @SuppressWarnings("all")
    public int compareTo(FiringPeriod di) {
        int result = fromDate.compareTo(di.fromDate);
        if (result == 0) {
            result = toDate.compareTo(di.toDate);
        }
        return result;
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getLegacyId() {
        return legacyId;
    }

    public void setLegacyId(String legacyId) {
        this.legacyId = legacyId;
    }

    public Area getArea() {
        return area;
    }

    public void setArea(Area area) {
        this.area = area;
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
