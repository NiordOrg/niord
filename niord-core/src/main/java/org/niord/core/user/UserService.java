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
import org.niord.core.domain.Domain;
import org.niord.core.keycloak.KeycloakIntegrationService;
import org.niord.core.service.BaseService;
import org.niord.core.user.vo.GroupVo;
import org.niord.core.user.vo.UserVo;
import org.niord.model.DataFilter.UserResolver;
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
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Defines the API for accessing users either from the database or from Keycloak
 */
@Stateless
@SuppressWarnings("unused")
public class UserService extends BaseService {

    public static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@"
                    + "[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$");

    @Inject
    private Logger log;

    @Resource
    SessionContext ctx;

    @Inject
    TicketService ticketService;

    @Inject
    KeycloakIntegrationService keycloakIntegrationService;


    /************************/
    /** Current User       **/
    /************************/


    /**
     * Returns the current Keycloak principal
     */
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
     *
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
     *
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
     * Returns a user resolver that, for the duration of the current transaction only,
     * may be used by a DataFilter to check the current user.
     * @return a user resolver
     */
    public UserResolver userResolver() {
        return new CurrentTransactionUserResolver(this);
    }

    /**
     * Test if the caller has a given security role.
     *
     * @param role The name of the security role.
     * @return True if the caller has the specified role.
     */
    public boolean isCallerInRole(String role) {
        return ctx.isCallerInRole(role) ||
                ticketService.validateRolesForCurrentThread(role);
    }


    /*************************/
    /** Database Users      **/
    /*************************/


    /**
     * Looks up the {@code User} with the given username
     *
     * @param username the user name
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
     * Looks up the {@code User} with the email
     *
     * @param email the user email
     * @return the user or null
     */
    public User findByEmail(String email) {
        try {
            return em.createNamedQuery("User.findByEmail", User.class)
                    .setParameter("email", email)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Looks up the {@code User}s with the given usernames
     *
     * @param usernames the user names
     * @return the user or null
     */
    public List<User> findByUsernames(Set<String> usernames) {
        if (usernames != null && !usernames.isEmpty()) {
            usernames = usernames.stream()
                    .map(String::toUpperCase)
                    .collect(Collectors.toSet());
            try {
                return em.createNamedQuery("User.findByUsernames", User.class)
                        .setParameter("usernames", usernames)
                        .getResultList();
            } catch (Exception ignored) {
            }
        }
        return Collections.emptyList();
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


    /**
     * Returns a list of persisted users based on a list of template users
     * @param users the list of users to look up persisted users for
     * @return the list of corresponding persisted users
     */
    public List<User> persistedUsers(List<User> users) {
        return users.stream()
                .map(c -> findByUsername(c.getUsername()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }


    /**
     * Returns all users
     * @return all users
     */
    public List<User> allUsers() {
        return getAll(User.class);
    }


    /*************************/
    /** Keycloak Users      **/
    /*************************/


    /**
     * Adds the user to Keycloak and the local Niord DB
     * @param user the template user to add
     */
    public UserVo addKeycloakUser(UserVo user) throws Exception {
        keycloakIntegrationService.addKeycloakUser(user);
        return syncKeycloakUserWithNiord(user).toVo();
    }


    /**
     * Updates the user in Keycloak and the local Niord DB
     * @param user the template user to update
     */
    public UserVo updateKeycloakUser(UserVo user) throws Exception {
        keycloakIntegrationService.updateKeycloakUser(user);
        return syncKeycloakUserWithNiord(user).toVo();
    }


    /**
     * Synchronize the Keycloak user to the local Niord DB
     * @param userVo the Keycloak user
     * @return the updated user
     */
    private User syncKeycloakUserWithNiord(UserVo userVo) {

        User user = findByUsername(userVo.getUsername());

        if (user == null) {
            // New user
            user = new User(userVo);
            user = saveEntity(user);
            log.info("Created new user from Keycloak " + user.getUsername());

        } else {
            // User data updated
            user.copyUser(userVo);
            user = saveEntity(user);
            log.info("Updated user from Keycloak " + user.getUsername());
        }

        return user;
    }


    /**
     * Deletes the user from Keycloak
     * @param userId the user to delete
     */
    public void deleteKeycloakUser(String userId) throws Exception {
        keycloakIntegrationService.deleteKeycloakUser(userId);
    }


    /**
     * Returns the users from Keycloak
     * @return the users from Keycloak
     */
    public List<UserVo> searchKeycloakUsers(String search, int first, int max) {
        try {
            List<UserVo> users = keycloakIntegrationService.searchKeycloakUsers(search, first, max);

            // Merge the user data from Keycloak with local user data that is not stored in Keycloak
            // For now, this is only the "language" attribute
            Set<String> usernames = users.stream()
                    .map(UserVo::getUsername)
                    .collect(Collectors.toSet());
            Map<String, User> userMap = findByUsernames(usernames).stream()
                    .collect(Collectors.toMap(User::getUsername, Function.identity()));

            users.stream()
                .filter(u -> userMap.containsKey(u.getUsername()))
                .forEach(u -> u.setLanguage(userMap.get(u.getUsername()).getLanguage()));

            return users;
        } catch (Exception e) {
            log.error("Error reading Keycloak users: " + e.getMessage());
            return Collections.emptyList();
        }
    }


    /**
     * Returns the domain groups from Keycloak
     * @return the domain groups
     */
    public List<GroupVo> getKeycloakGroups(Domain domain) {
        try {
            List<GroupVo> groups = keycloakIntegrationService.getKeycloakGroups();
            groups.forEach(g -> resolveKeycloakGroupAccess(domain, g));
            return groups;
        } catch (Exception e) {
            log.error("Error reading Keycloak groups: " + e.getMessage(), e);
            return Collections.emptyList();
        }
    }


    /** Determine which of the groups (and sub-groups) the user has access to **/
    private void resolveKeycloakGroupAccess(Domain domain, GroupVo group) {
        try {
            List<String> roles = keycloakIntegrationService.getKeycloakRoles(domain, group.getId());
            group.setActive(roles.stream().anyMatch(this::isCallerInRole));
        } catch (Exception e) {
            group.setActive(false);
            log.debug("Error reading Keycloak group access : " + e.getMessage());
        }
        if (group.getChildren() != null) {
            group.getChildren().forEach(g -> resolveKeycloakGroupAccess(domain, g));
        }
    }


    /**
     * Returns the user groups from Keycloak
     * @return the user groups
     */
    public List<GroupVo> getKeycloakUserGroups(Domain domain, String userId) {
        try {
            List<GroupVo> groups =  keycloakIntegrationService.getKeycloakUserGroups(userId);
            groups.forEach(g -> resolveKeycloakGroupAccess(domain, g));
            return groups;
        } catch (Exception e) {
            log.debug("Error reading Keycloak user groups: " + e.getMessage());
            return Collections.emptyList();
        }
    }


    /**
     * Assign the user to the given group
     * @param userId the Keycloak user ID
     * @param groupId the Keycloak group ID
     */
    public void joinKeycloakGroup(String userId, String groupId) {
        try {
            keycloakIntegrationService.joinKeycloakGroup(userId, groupId);
        } catch (Exception e) {
            log.error("Error joining Keycloak groups: " + e.getMessage());
        }
    }


    /**
     * Removes the user from the given group
     * @param userId the Keycloak user ID
     * @param groupId the Keycloak group ID
     */
    public void leaveKeycloakGroup(String userId, String groupId) {
        try {
            keycloakIntegrationService.leaveKeycloakGroup(userId, groupId);
        } catch (Exception e) {
            log.error("Error leaving Keycloak groups: " + e.getMessage());
        }
    }
}
