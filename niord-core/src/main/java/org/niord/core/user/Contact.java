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
package org.niord.core.user;

import org.apache.commons.lang.StringUtils;
import org.niord.core.mail.IMailable;
import org.niord.core.model.VersionedEntity;
import org.niord.core.user.vo.ContactVo;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.Transient;

/**
 * Implementation of a contact entity which may e.g. be used in mailing lists, etc.
 * <p>
 * Note to self: User ought to inherit from Contact, or rather, User and Contact should
 * have a common super class with the email, firstName, lastName, and language attributes.
 * The common-ancestor solution would allow us to search using JPA in Contact without including results from User.
 */
@Entity
@Table(indexes = {
        @Index(name = "contact_email", columnList="email", unique = true)
})
@NamedQueries({
        @NamedQuery(name="Contact.findAll",
                query="SELECT c FROM Contact c"),
        @NamedQuery(name="Contact.findByEmail",
                query="SELECT c FROM Contact c where lower(c.email) = :email"),
        @NamedQuery(name="Contact.searchContacts",
                query="SELECT c FROM Contact c where lower(c.email) like :term " +
                      " or lower(c.firstName) like :term or lower(c.lastName) like :term")
})
@SuppressWarnings("unused")
public class Contact extends VersionedEntity<Integer> implements IMailable {

    @Column(nullable = false)
    String email;

    String firstName;

    String lastName;

    String language;


    /** Constructor **/
    public Contact() {
    }


    /** Constructor **/
    public Contact(ContactVo contact) {
        setId(contact.getId());
        setEmail(contact.getEmail());
        setFirstName(contact.getFirstName());
        setLastName(contact.getLastName());
        setLanguage(contact.getLanguage());
    }


    /** Converts this entity to a value object */
    public ContactVo toVo() {
        ContactVo contact = new ContactVo();
        contact.setId(id);
        contact.setEmail(email);
        contact.setFirstName(firstName);
        contact.setLastName(lastName);
        contact.setLanguage(language);
        return contact;
    }


    /** Composes a full name from the contact details */
    @Transient
    @Override
    public String getName() {
        StringBuilder name = new StringBuilder();
        if (StringUtils.isNotBlank(firstName)) {
            name.append(firstName);
        }
        if (StringUtils.isNotBlank(lastName)) {
            if (name.length() > 0) {
                name.append(" ");
            }
            name.append(lastName);
        }
        return name.toString();
    }


    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "Contact{" +
                "id=" + id +
                ", email='" + email + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", language='" + language + '\'' +
                '}';
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }
}
