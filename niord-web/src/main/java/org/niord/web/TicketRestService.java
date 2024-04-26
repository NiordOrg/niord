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

import jakarta.annotation.security.PermitAll;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.niord.core.user.TicketService;
import org.niord.core.user.UserService;
import org.slf4j.Logger;

import jakarta.ejb.SessionContext;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import java.util.Set;

/**
 * The ticket service can be used to issue a one-time ticket with a short expiry time (1 minute).
 *
 * This is useful when e.g. creating a download link to a protected resource. Since
 * the download link is not an Ajax call, the usual Authorization header will not be
 * injected, and thus, the REST endpoint cannot be protected using the usual mechanism.
 * <p>
 * Instead, the download link should start by making an Ajax call to get a ticket, then
 * add the ticket to the link being opened as a ticket.
 * In the REST endpoint, the ticket can be validated programmatically.
 * <p>
 * The ticket is associated with various types of data, known to be valid when the ticket
 * was issued, i.e. the current domain, current user and optionally, a set of users roles.<br>
 * If in a subsequent request the ticket is resolved, then the stored ticket data can be trusted to be true
 * even in an un-authorized request.
 */
@Path("/tickets")
@RequestScoped
@Transactional
public class TicketRestService {

    @Inject
    Logger log;

    @Inject
    UserService userService;

    @Inject
    TicketService ticketService;

    /**
     * Returns a ticket to be used in a subsequent call.
     *
     * @param roles optionally, specify roles for which the ticket is issued
     * @return a new ticket
     */
    @GET
    @Path("/ticket")
    @Produces("text/plain")
    @NoCache
    @PermitAll
    public String getTicket(@QueryParam("role") Set<String> roles) {

        String[] ticketRoles = null;

        if (roles != null && !roles.isEmpty()) {
            // Ensure that the current user has all the roles that the ticket is requested for
            for (String role : roles) {
                if (!userService.isCallerInRole(role)) {
                    throw new WebApplicationException(401);
                }
            }

            ticketRoles = roles.toArray(new String[roles.size()]);
        }

        // Create a ticket for the current domain, user and the given roles
        return ticketService.createTicket(ticketRoles);
    }

}
