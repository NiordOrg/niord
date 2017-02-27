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

import org.niord.model.search.PagedSearchParamsVo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.commons.lang.StringUtils.isNotBlank;

/**
 * Defines the area search parameters
 */
@SuppressWarnings("unused")
public class AreaSearchParams extends PagedSearchParamsVo {

    public static final String TREE_SORT_ORDER = "TREE_ORDER";

    Integer parentId;       // Subtree under the parent
    Set<Integer> areaIds;   // Subtrees under or including the areas
    String language;
    String name;
    AreaType type;
    String domain;          // If defined, only include areas for the given domain
    boolean exact;          // Match the name exactly (though case insensitive) rather than a substring match
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
        if (areaIds != null) { desc.add(String.format("Areas: %s", areaIds)); }
        if (isNotBlank(language)) { desc.add(String.format("Language: %s", language)); }
        if (isNotBlank(name)) { desc.add(String.format("Name: '%s'", name)); }
        if (type != null) { desc.add(String.format("Type: %s", type)); }
        if (isNotBlank(domain)) { desc.add(String.format("Domain: '%s'", domain)); }
        if (exact) { desc.add("Exact: true"); }
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

    public Set<Integer> getAreaIds() {
        return areaIds;
    }

    public AreaSearchParams areaIds(Set<Integer> areaIds) {
        this.areaIds = areaIds;
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

    public AreaSearchParams type(AreaType type) {
        this.type = type;
        return this;
    }

    public boolean isExact() {
        return exact;
    }

    public AreaSearchParams exact(boolean exact) {
        this.exact = exact;
        return this;
    }

    public String getDomain() {
        return domain;
    }

    public AreaSearchParams domain(String domain) {
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
