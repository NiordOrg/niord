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
package org.niord.core.mail;

import org.niord.core.mail.ScheduledMail.Status;
import org.niord.model.search.PagedSearchParamsVo;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.commons.lang.StringUtils.isNotBlank;

/**
 * Defines the scheduled mail search parameters
 */
@SuppressWarnings("unused")
public class ScheduledMailSearchParams extends PagedSearchParamsVo {

    String recipient;
    Status status;
    Date from;
    Date to;

    /**
     * Returns a string representation of the search criteria
     * @return a string representation of the search criteria
     */
    @Override
    public String toString() {
        List<String> desc = new ArrayList<>();
        if (isNotBlank(recipient)) { desc.add(String.format("Recipient: '%s'", recipient)); }
        if (status != null) { desc.add(String.format("Status: %s", status)); }
        if (from != null) { desc.add("From: " + from); }
        if (to != null) { desc.add("To: to"); }

        return desc.stream().collect(Collectors.joining(", "));
    }

    /*******************************************/
    /** Method chaining Getters and Setters   **/
    /*******************************************/

    public String getRecipient() {
        return recipient;
    }

    public ScheduledMailSearchParams recipient(String recipient) {
        this.recipient = recipient;
        return this;
    }

    public Status getStatus() {
        return status;
    }

    public ScheduledMailSearchParams status(Status status) {
        this.status = status;
        return this;
    }

    public Date getFrom() {
        return from;
    }

    public ScheduledMailSearchParams from(Date from) {
        this.from = from;
        return this;
    }

    public ScheduledMailSearchParams from(Long from) {
        this.from = from != null ? new Date(from) : null;
        return this;
    }

    public Date getTo() {
        return to;
    }

    public ScheduledMailSearchParams to(Date to) {
        this.to = to;
        return this;
    }

    public ScheduledMailSearchParams to(Long to) {
        this.to = to != null ? new Date(to) : null;
        return this;
    }
}
