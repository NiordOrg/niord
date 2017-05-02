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

import org.niord.core.mail.vo.MailingListDescVo;
import org.niord.core.mail.vo.MailingListVo;
import org.niord.core.model.VersionedEntity;
import org.niord.core.user.Contact;
import org.niord.core.user.User;
import org.niord.model.DataFilter;
import org.niord.model.ILocalizable;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.ManyToMany;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Defines a mailing list.
 * <p>
 * At the moment, mailing lists amounts to a named collection of users and contacts, and is
 * user directly by promulgation types to determine the recipients of promulgation mails.
 * <p>
 * In the future, mailing lists should be "auto-executable" by extending them in the following manner:
 * <ul>
 *     <li>
 *         Mailing lists could be associated with script resources (Freemarker and server-side JavaScript)
 *         that performs the fetching of messages and generating the PDF report.
 *     </li>
 *     <li>
 *         A mailing list could be scheduled as running either daily, or on a specific week-day at
 *         a certain time.
 *     </li>
 *     <li>
 *         Alternatively, it could be triggered by status changes of messages.
 *     </li>
 *     <li>
 *         In addition, it should be possible to specify a message filter, e.g. domain, message series,
 *         area, category, geometry, etc. used to compute the list of messages to include.
 *     </li>
 *     <li>
 *         Ultimately, end-users should be allowed to register as contacts (via Niord-proxy) and create their
 *         own mailing lists.
 *     </li>
 * </ul>
 */
@Entity
@NamedQueries({
        @NamedQuery(name = "MailingList.findByMailingListId",
                query = "SELECT m FROM MailingList m where m.mailingListId = :mailingListId")
})
@SuppressWarnings("unused")
public class MailingList extends VersionedEntity<Integer> implements ILocalizable<MailingListDesc> {

    public static final DataFilter LIST_DETAILS_FILTER =
            DataFilter.get().fields("MailingList.details");
    public static final DataFilter LIST_DETAILS_AND_RECIPIENTS_FILTER =
            DataFilter.get().fields("MailingList.details", "MailingList.users", "MailingList.contacts");


    @Column(unique = true, nullable = false)
    String mailingListId;

    boolean active;

    @ManyToMany
    List<User> users = new ArrayList<>();

    @ManyToMany
    List<Contact> contacts = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "entity", orphanRemoval = true)
    List<MailingListDesc> descs = new ArrayList<>();


    /** Constructor **/
    public MailingList() {
    }


    /** Constructor **/
    public MailingList(MailingListVo mailingList) {
        this.mailingListId = mailingList.getMailingListId();
        this.active = mailingList.getActive() != null && mailingList.getActive();
        if (mailingList.getUsers() != null) {
            this.users.addAll(mailingList.getUsers().stream()
                .map(User::new)
                .collect(Collectors.toList()));
        }
        if (mailingList.getContacts() != null) {
            this.contacts.addAll(mailingList.getContacts().stream()
                .map(Contact::new)
                .collect(Collectors.toList()));
        }
        if (mailingList.getDescs() != null) {
            mailingList.getDescs().stream()
                    .filter(MailingListDescVo::descDefined)
                    .forEach(desc -> createDesc(desc.getLang())
                            .copyDesc(new MailingListDesc(desc)));
        }
    }


    /** Converts this entity to a value object */
    public MailingListVo toVo(DataFilter filter) {
        DataFilter compFilter = filter.forComponent(MailingList.class);

        MailingListVo mailingList = new MailingListVo();
        mailingList.setMailingListId(mailingListId);
        mailingList.setActive(active);

        if (compFilter.includeDetails()) {
            mailingList.setDescs(getDescs(filter).stream()
                    .map(MailingListDesc::toVo)
                    .collect(Collectors.toList()));
            mailingList.setRecipientNo(users.size() + contacts.size());
        }
        if (!users.isEmpty() && compFilter.includeField("users")) {
            mailingList.setUsers(users.stream()
                    .map(User::toVo)
                    .collect(Collectors.toList()));
        }
        if (!contacts.isEmpty() && compFilter.includeField("contacts")) {
            mailingList.setContacts(contacts.stream()
                    .map(Contact::toVo)
                    .collect(Collectors.toList()));
        }

        return mailingList;
    }


    /** {@inheritDoc} */
    @Override
    public MailingListDesc createDesc(String lang) {
        MailingListDesc desc = new MailingListDesc();
        desc.setLang(lang);
        desc.setEntity(this);
        getDescs().add(desc);
        return desc;
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/


    public String getMailingListId() {
        return mailingListId;
    }

    public void setMailingListId(String mailingListId) {
        this.mailingListId = mailingListId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public List<User> getUsers() {
        return users;
    }

    public void setUsers(List<User> users) {
        this.users = users;
    }

    public List<Contact> getContacts() {
        return contacts;
    }

    public void setContacts(List<Contact> contacts) {
        this.contacts = contacts;
    }

    @Override
    public List<MailingListDesc> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<MailingListDesc> descs) {
        this.descs = descs;
    }
}

