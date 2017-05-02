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
package org.niord.core.mailinglist;

import org.niord.model.search.PagedSearchParamsVo;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.commons.lang.StringUtils.isNotBlank;

/**
 * Defines the MailingList search parameters
 */
@SuppressWarnings("unused")
public class MailingListSearchParams extends PagedSearchParamsVo {

    String name;
    String language;
    String mailingListId;
    String username;
    String contactEmail;

    /**
     * Returns a string representation of the search criteria
     * @return a string representation of the search criteria
     */
    @Override
    public String toString() {
        List<String> desc = new ArrayList<>();
        if (isNotBlank(name)) { desc.add(String.format("Name: '%s'", name)); }
        if (isNotBlank(language)) { desc.add(String.format("Language: %s", language)); }
        if (isNotBlank(mailingListId)) { desc.add(String.format("Mailing-list ID: '%s'", mailingListId)); }
        if (isNotBlank(username)) { desc.add(String.format("Username: '%s'", username)); }
        if (isNotBlank(contactEmail)) { desc.add(String.format("Contact email: '%s'", contactEmail)); }
        return desc.stream().collect(Collectors.joining(", "));
    }

    /*******************************************/
    /** Method chaining Getters and Setters   **/
    /*******************************************/

    public String getName() {
        return name;
    }

    public MailingListSearchParams name(String name) {
        this.name = name;
        return this;
    }

    public String getLanguage() {
        return language;
    }

    public MailingListSearchParams language(String language) {
        this.language = language;
        return this;
    }

    public String getMailingListId() {
        return mailingListId;
    }

    public MailingListSearchParams mailingListId(String mailingListId) {
        this.mailingListId = mailingListId;
        return this;
    }

    public String getUsername() {
        return username;
    }

    public MailingListSearchParams username(String username) {
        this.username = username;
        return this;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public MailingListSearchParams contactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
        return this;
    }
}
