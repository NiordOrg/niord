/*
 * Copyright 2023 GLA UK Research and Development Directive
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
package org.niord.core.aton;

import org.niord.model.search.PagedSearchParamsVo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.commons.lang.StringUtils.isNotBlank;

/**
 * Defines the AtoN link search parameters
 */
@SuppressWarnings("unused")
public class AtonLinkSearchParams extends PagedSearchParamsVo {

    String name;
    Set<AtonLinkType> types = new HashSet<>();

    /** Returns whether to sort by name or not */
    public boolean sortByName() {
        return "name".equalsIgnoreCase(sortBy);
    }

    /** Returns whether to sort by type or not */
    public boolean sortByType() {
        return "linkType".equalsIgnoreCase(sortBy);
    }

    /** Returns whether to sort by created date or not */
    public boolean sortByCreated() {
        return "created".equalsIgnoreCase(sortBy);
    }

    /** Returns whether to sort by AtoN count or not */
    public boolean sortByAtonCount() {
        return "aton_count".equalsIgnoreCase(sortBy);
    }

    /**
     * Returns a string representation of the search criteria
     * @return a string representation of the search criteria
     */
    @Override
    public String toString() {
        List<String> desc = new ArrayList<>();
        if (types != null) { desc.add(String.format("Types: %s", types)); }
        if (isNotBlank(name)) { desc.add(String.format("Name: '%s'", name)); }
        return desc.stream().collect(Collectors.joining(", "));
    }

    /*******************************************/
    /** Method chaining Getters and Setters   **/
    /*******************************************/

    public String getName() {
        return name;
    }

    public AtonLinkSearchParams name(String name) {
        this.name = name;
        return this;
    }

    public Set<AtonLinkType> getTypes() {
        return types;
    }

    public AtonLinkSearchParams types(Set<AtonLinkType> types) {
        this.types = types;
        return this;
    }
}
