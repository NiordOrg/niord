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

/**
 * Defines the user roles used throughout the Niord system.
 * <p>
 * The roles are configured per domain in Keycloak and defined as composite roles, such that
 * "sysadmin" implies "admin", which implies "editor", which implies "user", but not vice versa.
 * <p>
 * NB: They have been defined as string constants rather than enums, because it then
 * allows us to use the roles in, say, @RolesAllowed annotations.
 */
public interface Roles {

    String USER = "user";
    String EDITOR = "editor";
    String ADMIN = "admin";
    String SYSADMIN = "sysadmin";
}
