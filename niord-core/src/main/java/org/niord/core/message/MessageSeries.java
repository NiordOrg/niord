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

import org.niord.core.model.VersionedEntity;
import org.niord.model.DataFilter;
import org.niord.model.vo.MainType;
import org.niord.model.vo.MessageSeriesVo;

import javax.persistence.Cacheable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.validation.constraints.NotNull;


/**
 * Represents a message series
 */
@Entity
@Cacheable
@NamedQueries({
        @NamedQuery(name="MessageSeries.findBySeriesId",
                query="SELECT ms FROM MessageSeries ms where ms.seriesId = :seriesId"),
        @NamedQuery(name="MessageSeries.findBySeriesIds",
                query="SELECT ms FROM MessageSeries ms where ms.seriesId in (:seriesIds)"),
        @NamedQuery(name  = "MessageSeries.searchMessageSeries",
                query = "select ms FROM MessageSeries ms where lower(ms.mrnFormat) like lower(:term) "
                      + "or lower(ms.seriesId) like lower(:term)")
})
@SuppressWarnings("unused")
public class MessageSeries extends VersionedEntity<Integer> {

    @Column(unique = true, nullable = false)
    String seriesId;

    @NotNull
    @Enumerated(EnumType.STRING)
    MainType mainType;

    @NotNull
    String mrnFormat;

    String shortFormat;


    /** Constructor */
    public MessageSeries() {
    }


    /** Constructor */
    public MessageSeries(MessageSeriesVo series) {
        this.seriesId = series.getSeriesId();
        this.mainType = series.getMainType();
        this.mrnFormat = series.getMrnFormat();
        this.shortFormat = series.getShortFormat();
    }


    /** Converts this entity to a value object */
    public MessageSeriesVo toVo(DataFilter filter) {
        MessageSeriesVo series = new MessageSeriesVo();
        series.setSeriesId(seriesId);

        DataFilter compFilter = filter.forComponent(MessageSeries.class);
        if (compFilter.includeField(DataFilter.DETAILS)) {
            series.setMainType(mainType);
            series.setMrnFormat(mrnFormat);
            series.setShortFormat(shortFormat);
        }
        return series;
    }

    /** Converts this entity to a value object */
    public MessageSeriesVo toVo() {
        return toVo(DataFilter.get().fields(DataFilter.ALL));
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getSeriesId() {
        return seriesId;
    }

    public void setSeriesId(String seriesId) {
        this.seriesId = seriesId;
    }

    public MainType getMainType() {
        return mainType;
    }

    public void setMainType(MainType mainType) {
        this.mainType = mainType;
    }

    public String getMrnFormat() {
        return mrnFormat;
    }

    public void setMrnFormat(String mrnFormat) {
        this.mrnFormat = mrnFormat;
    }

    public String getShortFormat() {
        return shortFormat;
    }

    public void setShortFormat(String shortFormat) {
        this.shortFormat = shortFormat;
    }
}
