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

import org.apache.commons.lang.StringUtils;
import org.keycloak.KeycloakPrincipal;
import org.keycloak.KeycloakSecurityContext;
import org.keycloak.representations.AccessToken;
import org.niord.core.service.BaseService;
import org.slf4j.Logger;

import javax.annotation.Resource;
import javax.ejb.SessionContext;
import javax.ejb.Stateless;
import javax.inject.Inject;
import java.security.Principal;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Wraps access to the current user
 */
@Stateless
@SuppressWarnings("unused")
public class UserService extends BaseService {

    @Inject
    private Logger log;

    @Resource
    SessionContext ctx;

    @Inject
    TicketService ticketService;


    /** Returns the current Keycloak principal */
    public KeycloakPrincipal getCallerPrincipal() {
        Principal principal = ctx.getCallerPrincipal();

        // Handle un-authenticated case
        if (principal == null || !(principal instanceof KeycloakPrincipal)) {
            return null;
        }

        return (KeycloakPrincipal) principal;
    }


    /**
     * Returns all the Keycloak domain IDs where the current user has the given role
     * @param role the role to check for
     * @return all the Keycloak domain IDs where the current user has the given role
     */
    public Set<String> getKeycloakDomainIdsForRole(String role) {
        KeycloakPrincipal keycloakPrincipal = getCallerPrincipal();
        if (keycloakPrincipal != null) {
            KeycloakSecurityContext ctx = keycloakPrincipal.getKeycloakSecurityContext();
            AccessToken accessToken = ctx.getToken();
            Map<String, AccessToken.Access> accessMap = accessToken.getResourceAccess();
            return accessMap.entrySet().stream()
                    .filter(kv -> kv.getValue().isUserInRole(role))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
        }
        return new HashSet<>();
    }


    /**
     * Returns the currently authenticated user.
     * If necessary the user is created and updated in the database
     * @return the currently authenticated user
     */
    public User currentUser() {

        // Get the current Keycloak principal
        KeycloakPrincipal keycloakPrincipal = getCallerPrincipal();
        if (keycloakPrincipal == null) {

            // Check if the ticket service has resolved a ticket for the current thread
            TicketService.TicketData ticketData = ticketService.getTicketDataForCurrentThread();
            if (ticketData != null && StringUtils.isNotBlank(ticketData.getUser())) {
                return findByUsername(ticketData.getUser());
            } else {
                return null;
            }
        }

        @SuppressWarnings("all")
        AccessToken token = keycloakPrincipal.getKeycloakSecurityContext().getToken();

        User user = findByUsername(token.getPreferredUsername());

        if (user == null) {
            // New user
            user = new User(token);
            user = saveEntity(user);
            log.info("Created new user " + user);

        } else if (user.userChanged(token)) {
            // User data updated
            user.copyToken(token);
            user = saveEntity(user);
            log.info("Updated user " + user);
        }
        return user;
    }


    /**
     * Test if the caller has a given security role.
     *
     * @param role The name of the security role.
     *
     * @return True if the caller has the specified role.
     */
    public boolean isCallerInRole(String role) {
        return  ctx.isCallerInRole(role) ||
                ticketService.validateRolesForCurrentThread(role);
    }


    /**
     * Looks up the {@code User} with the given username
     *
     * @param username the email
     * @return the user or null
     */
    public User findByUsername(String username) {
        try {
            return em.createNamedQuery("User.findByUsername", User.class)
                    .setParameter("username", username)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Searches for users whose name or e-mail matches the given name
     * @param name the name to match
     * @return the matching users
     */
    public List<User> searchUsers(String name) {
        if (StringUtils.isBlank(name)) {
            return Collections.emptyList();
        }
        return em.createNamedQuery("User.searchUsers", User.class)
                .setParameter("term", "%" + name.toLowerCase() + "%")
                .getResultList();
    }
}
