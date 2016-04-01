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
import org.niord.model.vo.MessageSeriesVo;

import javax.persistence.Cacheable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;


/**
 * Represents a message series
 */
@Entity
@Cacheable
@NamedQueries({
        @NamedQuery(name="MessageSeries.findByMrnFormat",
                query="SELECT ms FROM MessageSeries ms where ms.mrnFormat = :mrnFormat"),
        @NamedQuery(name="MessageSeries.findByIds",
                query="SELECT ms FROM MessageSeries ms where ms.id in (:ids)"),
        @NamedQuery(name  = "MessageSeries.searchMessageSeries",
                query = "select ms FROM MessageSeries ms where lower(ms.mrnFormat) like lower(:term)")

})
public class MessageSeries extends VersionedEntity<Integer> {

    public enum MainType { NW, NM }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    MainType mainType;

    @Column(unique = true, nullable = false)
    String mrnFormat;

    String shortFormat;


    /** Constructor */
    public MessageSeries() {
    }


    /** Constructor */
    public MessageSeries(MessageSeriesVo series) {
        this.id = series.getId();
        this.mainType = MainType.valueOf(series.getMainType());
        this.mrnFormat = series.getMrnFormat();
        this.shortFormat = series.getShortFormat();
    }


    /** Converts this entity to a value object */
    public MessageSeriesVo toVo() {
        MessageSeriesVo series = new MessageSeriesVo();
        series.setId(id);
        series.setMainType(mainType.toString());
        series.setMrnFormat(mrnFormat);
        series.setShortFormat(shortFormat);
        return series;
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

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
