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

import org.niord.core.mail.IMailable;
import org.niord.core.model.VersionedEntity;
import org.niord.core.user.vo.ContactVo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;

/**
 * Implementation of a contact entity which may e.g. be used in mailing lists, etc.
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
                      " or lower(c.name) like :term")
})
@SuppressWarnings("unused")
public class Contact extends VersionedEntity<Integer> implements IMailable {

    @Column(nullable = false)
    String email;

    String name;

    String language;


    /** Constructor **/
    public Contact() {
    }


    /** Constructor **/
    public Contact(ContactVo contact) {
        setId(contact.getId());
        setEmail(contact.getEmail());
        setName(contact.getName());
        setLanguage(contact.getLanguage());
    }


    /** Converts this entity to a value object */
    public ContactVo toVo() {
        ContactVo contact = new ContactVo();
        contact.setId(this.getId());
        contact.setEmail(email);
        contact.setName(name);
        contact.setLanguage(language);
        return contact;
    }


    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "Contact{" +
                "id=" + id +
                ", email='" + email + '\'' +
                ", name='" + name + '\'' +
                ", language='" + language + '\'' +
                '}';
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
