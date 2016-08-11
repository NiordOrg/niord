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
package org.niord.core.area;

import org.niord.model.PagedSearchParamsVo;
import org.niord.model.vo.AreaType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.commons.lang.StringUtils.isNotBlank;

/**
 * Defines the area search parameters
 */
@SuppressWarnings("unused")
public class AreaSearchParams extends PagedSearchParamsVo {

    Integer parentId;
    String language;
    String name;
    AreaType type;
    boolean domain;         // Only include areas for current domain
    boolean geometry;       // Only include areas that define a geometry
    boolean messageSorting; // Only include areas that define a message sorting type
    boolean inactive;       // Include inactive areas

    /**
     * Returns a string representation of the search criteria
     * @return a string representation of the search criteria
     */
    @Override
    public String toString() {
        List<String> desc = new ArrayList<>();
        if (parentId != null) { desc.add(String.format("Parent: %s", parentId)); }
        if (isNotBlank(language)) { desc.add(String.format("Language: %s", language)); }
        if (isNotBlank(name)) { desc.add(String.format("Name: '%s'", name)); }
        if (type != null) { desc.add(String.format("Type: %s", type)); }
        if (domain) { desc.add("Domain: true"); }
        if (geometry) { desc.add("Geometry: true"); }
        if (messageSorting) { desc.add("Message-Sorting: true"); }
        if (inactive) { desc.add("Include inactive: true"); }

        return desc.stream().collect(Collectors.joining(", "));
    }

    /*******************************************/
    /** Method chaining Getters and Setters   **/
    /*******************************************/

    public Integer getParentId() {
        return parentId;
    }

    public AreaSearchParams parentId(Integer parentId) {
        this.parentId = parentId;
        return this;
    }

    public String getLanguage() {
        return language;
    }

    public AreaSearchParams language(String language) {
        this.language = language;
        return this;
    }

    public String getName() {
        return name;
    }

    public AreaSearchParams name(String name) {
        this.name = name;
        return this;
    }

    public AreaType getType() {
        return type;
    }

    public AreaSearchParams setType(AreaType type) {
        this.type = type;
        return this;
    }

    public boolean isDomain() {
        return domain;
    }

    public AreaSearchParams domain(boolean domain) {
        this.domain = domain;
        return this;
    }

    public boolean isGeometry() {
        return geometry;
    }

    public AreaSearchParams geometry(boolean geometry) {
        this.geometry = geometry;
        return this;
    }

    public boolean isMessageSorting() {
        return messageSorting;
    }

    public AreaSearchParams messageSorting(boolean messageSorting) {
        this.messageSorting = messageSorting;
        return this;
    }

    public boolean isInactive() {
        return inactive;
    }

    public AreaSearchParams inactive(boolean inactive) {
        this.inactive = inactive;
        return this;
    }
}
