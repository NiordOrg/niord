/*
 * Copyright 2017 Danish Maritime Authority.
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

package org.niord.core.template;

import org.niord.model.search.PagedSearchParamsVo;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.commons.lang.StringUtils.isNotBlank;

/**
 * Defines the message template search parameters
 */
@SuppressWarnings("unused")
public class TemplateSearchParams extends PagedSearchParamsVo {

    String language;
    String name;
    Integer category;       // If defined, only include templates for the category or sub-categories
    String domain;          // If defined, only include templates for the given domain
    boolean inactive;       // Include inactive templates

    /**
     * Returns a string representation of the search criteria
     * @return a string representation of the search criteria
     */
    @Override
    public String toString() {
        List<String> desc = new ArrayList<>();
        if (category != null) { desc.add(String.format("Category: %d", category)); }
        if (isNotBlank(language)) { desc.add(String.format("Language: %s", language)); }
        if (isNotBlank(name)) { desc.add(String.format("Name: '%s'", name)); }
        if (isNotBlank(domain)) { desc.add(String.format("Domain: '%s'", domain)); }
        if (inactive) { desc.add("Include inactive: true"); }

        return desc.stream().collect(Collectors.joining(", "));
    }

    /*******************************************/
    /** Method chaining Getters and Setters   **/
    /*******************************************/

    public String getLanguage() {
        return language;
    }

    public TemplateSearchParams language(String language) {
        this.language = language;
        return this;
    }

    public String getName() {
        return name;
    }

    public TemplateSearchParams name(String name) {
        this.name = name;
        return this;
    }

    public Integer getCategory() {
        return category;
    }

    public TemplateSearchParams category(Integer category) {
        this.category = category;
        return this;
    }

    public String getDomain() {
        return domain;
    }

    public TemplateSearchParams domain(String domain) {
        this.domain = domain;
        return this;
    }

    public boolean isInactive() {
        return inactive;
    }

    public TemplateSearchParams inactive(boolean inactive) {
        this.inactive = inactive;
        return this;
    }
}
