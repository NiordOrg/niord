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
package org.niord.model.vo;

import io.swagger.annotations.ApiModel;
import org.niord.model.IJsonSerializable;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

/**
 * Represents a message series
 */
@ApiModel(value = "MessageSeries", description = "A message series")
@XmlRootElement(name = "messageSeries")
@XmlType(propOrder = {
        "seriesId", "mainType", "mrnFormat", "shortFormat"
})
public class MessageSeriesVo implements IJsonSerializable {

    String seriesId;
    MainType mainType;
    String mrnFormat;
    String shortFormat;

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
