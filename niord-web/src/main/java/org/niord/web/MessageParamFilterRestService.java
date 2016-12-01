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

import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.security.annotation.SecurityDomain;
import org.niord.core.message.MessageParamFilter;
import org.niord.core.message.MessageParamFilterService;
import org.niord.core.user.User;
import org.niord.core.user.UserService;
import org.niord.core.message.vo.MessageParamFilterVo;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Allows an authenticated user to fetch and save message list filters.
 */
@Path("/filters")
@Stateless
@SecurityDomain("keycloak")
public class MessageParamFilterRestService {

    @Inject
    Logger log;

    @Inject
    MessageParamFilterService messageParamFilterService;

    @Inject
    UserService userService;


    /** Validates that the user is logged in, but does not check any specific roles **/
    private void checkUserLoggedIn() {
        User user = userService.currentUser();
        if (user == null) {
            throw new WebApplicationException(403);
        }
    }


    /** Returns all message list filters */
    @GET
    @Path("/all")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<MessageParamFilterVo> getMessageFilters() {
        // Must be logged in
        checkUserLoggedIn();

        return messageParamFilterService.getMessageFiltersForUser().stream()
                .map(MessageParamFilter::toVo)
                .collect(Collectors.toList());
    }


    /** Returns the filter with the given ID */
    @GET
    @Path("/filter/{filterId}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public MessageParamFilterVo getMessageFilter(@PathParam("filterId") Integer filterId) {
        // Must be logged in
        checkUserLoggedIn();

        MessageParamFilter filter = messageParamFilterService.findById(filterId);
        return filter == null ? null : filter.toVo();
    }


    /** Creates a new filter from the given template */
    @POST
    @Path("/filter/")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public MessageParamFilterVo createMessageFilter(MessageParamFilterVo filter) {
        // Must be logged in
        checkUserLoggedIn();

        log.info("Creating new message filter " + filter);
        return messageParamFilterService.createOrUpdateMessageFilter(new MessageParamFilter(filter)).toVo();
    }


    /** Deletes the filter with the given ID */
    @DELETE
    @Path("/filter/{filterId}")
    @GZIP
    @NoCache
    public void deleteMessageFilter(@PathParam("filterId") Integer filterId) {
        // Must be logged in
        checkUserLoggedIn();

        log.info("Deleting message filter " + filterId);
        messageParamFilterService.deleteMessageFilter(filterId);
    }
}

