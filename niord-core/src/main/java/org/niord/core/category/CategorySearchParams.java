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

package org.niord.core.category;

import org.niord.model.search.PagedSearchParamsVo;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.commons.lang.StringUtils.isNotBlank;

/**
 * Defines the category search parameters
 */
@SuppressWarnings("unused")
public class CategorySearchParams extends PagedSearchParamsVo {

    Integer parentId;
    String language;
    String name;
    boolean exact;          // Match the name exactly (though case insensitive) rather than a substring match
    boolean domain;         // Only include categories for current domain
    boolean inactive;       // Include inactive categories

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
        if (exact) { desc.add("Exact: true"); }
        if (domain) { desc.add("Domain: true"); }
        if (inactive) { desc.add("Include inactive: true"); }

        return desc.stream().collect(Collectors.joining(", "));
    }

    /*******************************************/
    /** Method chaining Getters and Setters   **/
    /*******************************************/

    public Integer getParentId() {
        return parentId;
    }

    public CategorySearchParams parentId(Integer parentId) {
        this.parentId = parentId;
        return this;
    }

    public String getLanguage() {
        return language;
    }

    public CategorySearchParams language(String language) {
        this.language = language;
        return this;
    }

    public String getName() {
        return name;
    }

    public CategorySearchParams name(String name) {
        this.name = name;
        return this;
    }

    public boolean isExact() {
        return exact;
    }

    public CategorySearchParams exact(boolean exact) {
        this.exact = exact;
        return this;
    }

    public boolean isDomain() {
        return domain;
    }

    public CategorySearchParams domain(boolean domain) {
        this.domain = domain;
        return this;
    }

    public boolean isInactive() {
        return inactive;
    }

    public CategorySearchParams inactive(boolean inactive) {
        this.inactive = inactive;
        return this;
    }
}
