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

package org.niord.core.user.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.niord.core.mail.IMailable;
import org.niord.model.IJsonSerializable;

import java.util.List;

/**
 * A value object for the User entity
 */
@SuppressWarnings("unused")
public class UserVo implements IJsonSerializable, IMailable {

    // The Keycloak fields are used when creating new users in Keycloak from the Users Admin page
    String keycloakId;
    List<String> keycloakActions;
    String keycloakPassword;

    String username;
    String email;
    String firstName;
    String lastName;
    String language;

    /** Not serialized to the font-end **/
    @JsonIgnore
    @Override
    public String getName() {
        return null;
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getKeycloakId() {
        return keycloakId;
    }

    public void setKeycloakId(String keycloakId) {
        this.keycloakId = keycloakId;
    }

    public List<String> getKeycloakActions() {
        return keycloakActions;
    }

    public void setKeycloakActions(List<String> keycloakActions) {
        this.keycloakActions = keycloakActions;
    }

    public String getKeycloakPassword() {
        return keycloakPassword;
    }

    public void setKeycloakPassword(String keycloakPassword) {
        this.keycloakPassword = keycloakPassword;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }
}
