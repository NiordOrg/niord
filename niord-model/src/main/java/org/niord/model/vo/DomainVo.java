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

import org.niord.model.IJsonSerializable;

import java.util.List;

/**
 * Represents an application domain
 */
public class DomainVo implements IJsonSerializable {

    private String clientId;
    private String name;
    private String timeZone;
    private List<AreaVo> areas;
    private List<CategoryVo> categories;
    private List<MessageSeriesVo> messageSeries;
    private Boolean inKeycloak;

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public List<AreaVo> getAreas() {
        return areas;
    }

    public void setAreas(List<AreaVo> areas) {
        this.areas = areas;
    }

    public List<CategoryVo> getCategories() {
        return categories;
    }

    public void setCategories(List<CategoryVo> categories) {
        this.categories = categories;
    }

    public List<MessageSeriesVo> getMessageSeries() {
        return messageSeries;
    }

    public void setMessageSeries(List<MessageSeriesVo> messageSeries) {
        this.messageSeries = messageSeries;
    }

    public Boolean getInKeycloak() {
        return inKeycloak;
    }

    public void setInKeycloak(Boolean inKeycloak) {
        this.inKeycloak = inKeycloak;
    }
}
