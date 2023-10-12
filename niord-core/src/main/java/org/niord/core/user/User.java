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
package org.niord.core.user;

import io.quarkus.oidc.runtime.OidcJwtCallerPrincipal;
import org.apache.commons.lang.StringUtils;
import org.niord.core.mail.IMailable;
import org.niord.core.model.VersionedEntity;
import org.niord.core.user.vo.UserVo;

import jakarta.persistence.*;

/**
 * Implementation of a user entity
 */
@Entity
@Cacheable
@Table(name = "user_account", indexes = {
        @Index(name = "user_username", columnList="username", unique = true)
})
@NamedQueries({
        @NamedQuery(name="User.findByUsername",
                query="SELECT u FROM User u where lower(u.username) = lower(:username)"),
        @NamedQuery(name="User.findByUsernames",
                query="SELECT u FROM User u where lower(u.username) in (:usernames)"),
        @NamedQuery(name="User.findByEmail",
                query="SELECT u FROM User u where lower(u.email) = lower(:email)"),
        @NamedQuery(name="User.searchUsers",
                query="SELECT u FROM User u where lower(u.username) like :term or lower(u.email) like :term " +
                      " or lower(u.firstName) like :term or lower(u.lastName) like :term")
})
@SuppressWarnings("unused")
public class User extends VersionedEntity<Integer> implements IMailable {

    @Column(nullable = false, unique = true)
    private String username;

    private String email;

    String firstName;

    String lastName;

    String language;

    /** Constructor **/
    public User() {
    }


    /** Constructor **/
    public User(OidcJwtCallerPrincipal principal) {
        copyToken(principal);
    }


    /** Constructor **/
    public User(UserVo user) {
        copyUser(user);
    }


    /** Copies the access token user values into this entity */
    public void copyToken(OidcJwtCallerPrincipal principal) {
        setUsername(principal.getName());
        setFirstName(principal.getClaim("given_name"));
        setLastName(principal.getClaim("family_name"));
        setEmail(principal.getClaim("email"));
        //setLanguage(token.getLocale()); // NB: Keycloak locale not currently used
    }


    /** Returns if the user data has changed **/
    public boolean userChanged(OidcJwtCallerPrincipal principal) {
        return !StringUtils.equals(username, principal.getName()) ||
                !StringUtils.equals(firstName, principal.getClaim("given_name")) ||
                !StringUtils.equals(lastName, principal.getClaim("family_name")) ||
                !StringUtils.equals(email, principal.getClaim("email"));
                //!StringUtils.equals(language, token.getLocale()); // NB: Keycloak locale not currently used
    }


    /** Copies the user values to this entity **/
    public void copyUser(UserVo user) {
        setUsername(user.getUsername());
        setEmail(user.getEmail());
        setFirstName(user.getFirstName());
        setLastName(user.getLastName());
        setLanguage(user.getLanguage());
    }


    /** Converts this entity to a value object */
    public UserVo toVo() {
        UserVo user = new UserVo();
        user.setUsername(username);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setLanguage(language);
        return user;
    }


    /** Composes a user name from the user details */
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
        if (name.length() == 0) {
            name.append(username);
        }
        return name.toString();
    }


    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "User{" +
                "username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", language='" + language + '\'' +
                '}';
    }


    /*************************/
    /** Getters and Setters **/
    /***/

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

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
