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

import org.niord.core.aton.vo.AtonNodeVo;
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

    public static final String TREE_SORT_ORDER = "TREE_ORDER";

    Integer parentId;       // Direct parent
    Integer ancestorId;     // Any ancestor
    String language;
    String name;
    CategoryType type;
    String domain;          // If defined, only include categories for the given domain
    boolean exact;          // Match the name exactly (though case insensitive) rather than a substring match
    boolean inactive;       // Include inactive categories
    List<AtonNodeVo> atons = new ArrayList<>();

    /**
     * Returns a string representation of the search criteria
     * @return a string representation of the search criteria
     */
    @Override
    public String toString() {
        List<String> desc = new ArrayList<>();
        if (parentId != null) { desc.add(String.format("Parent: %s", parentId)); }
        if (ancestorId != null) { desc.add(String.format("Ancestor: %s", ancestorId)); }
        if (type != null) { desc.add(String.format("Type: %s", type)); }
        if (isNotBlank(language)) { desc.add(String.format("Language: %s", language)); }
        if (isNotBlank(name)) { desc.add(String.format("Name: '%s'", name)); }
        if (isNotBlank(domain)) { desc.add(String.format("Domain: '%s'", domain)); }
        if (exact) { desc.add("Exact: true"); }
        if (inactive) { desc.add("Include inactive: true"); }
        if (atons != null && !atons.isEmpty()) { desc.add("#AtoNs: " + atons.size()); }

        return desc.stream().collect(Collectors.joining(", "));
    }

    /*******************************************/
    /** Method chaining Getters and Setters   **/
    /*******************************************/

    public CategoryType getType() {
        return type;
    }

    public CategorySearchParams type(CategoryType type) {
        this.type = type;
        return this;
    }

    public Integer getParentId() {
        return parentId;
    }

    public CategorySearchParams parentId(Integer parentId) {
        this.parentId = parentId;
        return this;
    }

    public Integer getAncestorId() {
        return ancestorId;
    }

    public CategorySearchParams ancestorId(Integer ancestorId) {
        this.ancestorId = ancestorId;
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

    public String getDomain() {
        return domain;
    }

    public CategorySearchParams domain(String domain) {
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

    public List<AtonNodeVo> getAtons() {
        return atons;
    }

    public CategorySearchParams atons(List<AtonNodeVo> atons) {
        this.atons = atons;
        return this;
    }
}
