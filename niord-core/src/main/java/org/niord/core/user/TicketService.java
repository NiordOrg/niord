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
import org.infinispan.Cache;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.util.concurrent.IsolationLevel;
import org.niord.core.cache.BaseCache;
import org.niord.core.domain.Domain;
import org.slf4j.Logger;

import javax.ejb.Singleton;
import javax.inject.Inject;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
@Singleton
public class TicketService extends BaseCache<String, TicketService.TicketData> {

    final static long LIFESPAN = 60 * 1000;    // 1 minute

    final static String CACHE_ID = "ticketCache";

    private final static ThreadLocal<TicketService.TicketData> THREAD_LOCAL_TICKET_DATA = new ThreadLocal<>();


    @Inject
    private Logger log;

    /** {@inheritDoc} */
    @Override
    public String getCacheId() {
        return CACHE_ID;
    }


    /** {@inheritDoc} */
    @Override
    public Cache<String, TicketData> getCache() {
        // Prevent access to the cache
        throw new RuntimeException("Illegal access to the underlying ticket cache");
    }


    /**
     * Issues a ticket tied to the current domain and user.
     * Optionally, a set of roles may be specified.
     * <p>
     * The ticket will expire after 1 minute and can only be validated once.
     *
     * @param domain the current domain
     * @param user the current user
     * @param roles the roles the ticket should be tied to
     * @return the ticket
     */
    public String createTicket(Domain domain, User user, String... roles) {

        // Construct a unique ticket
        String ticket = UUID.randomUUID().toString();

        TicketData ticketData = new TicketData(
                domain != null ? domain.getDomainId() : null,
                user != null ? user.getUsername() : null,
                roles
        );

        log.info("Ticket issued: " + ticketData);

        super.getCache().put(ticket, ticketData);

        return ticket;
    }


    /**
     * May be called, e.g. from a servlet filter to set the
     * ticket for the current thread based on a "ticket" request parameter.
     * <p>
     * Must be followed up with a call to call to removeTicketForCurrentThread()
     * @param ticket the ticket
     * @return if the ticket was resolved
     */
    public boolean resolveTicketForCurrentThread(String ticket) {
        if (StringUtils.isNotBlank(ticket)) {
            // Since the ticket is a one-time ticket, remove it from the cache
            TicketData ticketData = super.getCache().remove(ticket);

            // Register the ticket data in a thread local
            if (ticketData != null) {
                THREAD_LOCAL_TICKET_DATA.set(ticketData);
                return true;
            }
        }
        return false;
    }


    /**
     * If a ticket has been resolved for the current thread using resolveTicketForCurrentThread(),
     * this method can be used to fetch the ticket data.
     * @return the ticket data, or null if undefined.
     */
    public TicketData getTicketDataForCurrentThread() {
        return THREAD_LOCAL_TICKET_DATA.get();
    }

    /**
     * May be called, e.g. from a servlet filter to remove the
     * ticket data from the current thread
     * <p>
     * Must be preceded by a call to call to resolveTicketForCurrentThread()
     */
    public void removeTicketForCurrentThread() {
        THREAD_LOCAL_TICKET_DATA.remove();
    }

    /**
     * Checks if the ticket has been resolved for the current thread which is valid for at
     * least one of the given roles.<br>
     *
     * If no roles is passed along, the function will return true as long as the
     * ticket is valid.
     *
     * @param roles the roles to check validity for
     * @return if the ticket is valid for the give roles.
     */
    public boolean validateRolesForCurrentThread(String... roles) {

        TicketData ticketData = getTicketDataForCurrentThread();
        if (ticketData == null) {
            return false;
        }
        String[] ticketRoles = ticketData.getRoles();

        // If the ticket was tied to no roles, or if no roles is specified,
        // the ticket is valid
        if (ticketRoles == null || ticketRoles.length == 0) {
            return true;
        }

        // Check if there is any match between the set of roles
        Set<String> roleSet = Arrays.asList(roles).stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        return Arrays.asList(ticketRoles).stream()
                .map(String::toLowerCase)
                .anyMatch(roleSet::contains);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    protected Configuration createCacheConfiguration() {
        return new ConfigurationBuilder()
                .clustering().cacheMode(CacheMode.LOCAL)
                .locking().isolationLevel(IsolationLevel.REPEATABLE_READ)
                .expiration().lifespan(LIFESPAN)
                .build();
    }


    /**
     * Defines the data that is associated with a ticket
     */
    public static class TicketData {
        String domain;
        String user;
        String[] roles;

        /** Prevent public instantiation **/
        private TicketData(String domain, String user, String[] roles) {
            this.domain = domain;
            this.user = user;
            this.roles = roles;
        }

        @Override
        public String toString() {
            return "TicketData{" +
                    "domain='" + domain + '\'' +
                    ", user='" + user + '\'' +
                    ", roles=" + Arrays.toString(roles) +
                    '}';
        }

        public String getDomain() {
            return domain;
        }

        public String getUser() {
            return user;
        }

        public String[] getRoles() {
            return roles;
        }

    }
}
