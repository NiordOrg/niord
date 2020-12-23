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

package org.niord.web;

import org.apache.commons.lang.StringUtils;
import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.ejb3.annotation.SecurityDomain;
import org.niord.core.batch.AbstractBatchableRestService;
import org.niord.core.domain.DomainService;
import org.niord.core.user.Roles;
import org.niord.core.user.User;
import org.niord.core.user.UserService;
import org.niord.core.user.vo.GroupVo;
import org.niord.core.user.vo.UserVo;
import org.slf4j.Logger;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * REST interface for accessing users.
 */
@Path("/users")
@Stateless
@SecurityDomain("keycloak")
@PermitAll
public class UserRestService extends AbstractBatchableRestService {

    @Inject
    Logger log;

    @Inject
    UserService userService;

    @Inject
    DomainService domainService;


    /** Returns all users */
    @GET
    @Path("/all")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed(Roles.USER)
    @GZIP
    @NoCache
    public List<UserVo> all() {
        return userService.allUsers().stream()
                .map(User::toVo)
                .collect(Collectors.toList());
    }


    /** Returns all users that matches the given name */
    @GET
    @Path("/search")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed(Roles.USER)
    @GZIP
    @NoCache
    public List<UserVo> search(@QueryParam("name") String name) {
        return userService.searchUsers(name).stream()
                .map(User::toVo)
                .collect(Collectors.toList());
    }


    /** Returns email addresses whose users match the given name */
    @GET
    @Path("/search-emails")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed(Roles.USER)
    @GZIP
    @NoCache
    public List<String> searchEmails(@QueryParam("name") String name) {

        List<String> emails = new ArrayList<>();
        if (StringUtils.isNotBlank(name) && UserService.EMAIL_PATTERN.matcher(name).matches()) {
            emails.add(name);
        }

        userService.searchUsers(name).stream()
                .map(u -> u.getName() + " <" + u.getEmail() + ">")
                .filter(Objects::nonNull)
                .forEach(emails::add);

        return emails;
    }


    /** Returns all users that matches the given name */
    @GET
    @Path("/kc-users")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed(Roles.ADMIN)
    @GZIP
    @NoCache
    public List<UserVo> searchKeycloakUsers(
            @QueryParam("search") @DefaultValue("") String search,
            @QueryParam("first")  @DefaultValue("0") int first,
            @QueryParam("max")    @DefaultValue("20") int max) {
        return userService.searchKeycloakUsers(search, first, max);
    }


    /** Returns all users that matches the given name */
    @GET
    @Path("/kc-groups")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed(Roles.ADMIN)
    @GZIP
    @NoCache
    public List<GroupVo> getKeycloakGroups() {
        return userService.getKeycloakGroups(domainService.currentDomain());
    }


    /** Returns the domain roles assigned to the given group */
    @GET
    @Path("/kc-user/{userId}/kc-groups")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed(Roles.ADMIN)
    @GZIP
    @NoCache
    public List<GroupVo> getKeycloakUserGroups(@PathParam("userId") String userId) {
        return userService.getKeycloakUserGroups(domainService.currentDomain(), userId);
    }

    /**
     * Assign the user to the given group
     * @param userId the Keycloak user ID
     * @param groupId the Keycloak group ID
     */
    @PUT
    @Path("/kc-user/{userId}/kc-groups/{groupId}")
    @RolesAllowed(Roles.ADMIN)
    @NoCache
    public void joinKeycloakGroup(@PathParam("userId") String userId, @PathParam("groupId") String groupId) {
        log.info("Joining Keycloak user " + userId + " to group " + groupId);
        userService.joinKeycloakGroup(userId, groupId);
    }


    /**
     * Removes the user from the given group
     * @param userId the Keycloak user ID
     * @param groupId the Keycloak group ID
     */
    @DELETE
    @Path("/kc-user/{userId}/kc-groups/{groupId}")
    @RolesAllowed(Roles.ADMIN)
    @NoCache
    public void leaveKeycloakGroup(@PathParam("userId") String userId, @PathParam("groupId") String groupId) {
        log.info("Removing Keycloak user " + userId + " from group " + groupId);
        userService.leaveKeycloakGroup(userId, groupId);
    }

    /**
     * Adds the user to Keycloak and the local Niord DB
     * @param user the template user to add
     */
    @POST
    @Path("/kc-user/")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed(Roles.ADMIN)
    @NoCache
    public UserVo addKeycloakUser(UserVo user) throws Exception {
        log.info("Creating Keycloak user " + user.getUsername());
        return userService.addKeycloakUser(user);
    }


    /**
     * Updates the user in Keycloak and the local Niord DB
     * @param user the template user to update
     */
    @PUT
    @Path("/kc-user/{userId}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed(Roles.ADMIN)
    @NoCache
    public UserVo updateKeycloakUser(@PathParam("userId") String userId, UserVo user) throws Exception {
        if (!Objects.equals(userId, user.getKeycloakId())) {
            throw new WebApplicationException(400);
        }
        log.info("Updating Keycloak user " + user.getUsername());
        return userService.updateKeycloakUser(user);
    }

    
    /**
     * Deletes the user from Keycloak
     * @param userId the user to delete
     */
    @DELETE
    @Path("/kc-user/{userId}")
    @RolesAllowed(Roles.ADMIN)
    @NoCache
    public void deleteKeycloakUser(@PathParam("userId") String userId) throws Exception {
        log.info("Deleting Keycloak user " + userId);
        userService.deleteKeycloakUser(userId);
    }

}
