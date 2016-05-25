package org.niord.core.user;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.util.concurrent.IsolationLevel;
import org.niord.core.cache.BaseCache;
import org.niord.core.domain.Domain;
import org.niord.core.domain.DomainService;
import org.slf4j.Logger;

import javax.ejb.Singleton;
import javax.inject.Inject;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The ticket service can be used to issue a one-time ticket with a short expiry time.
 *
 * This is useful when e.g. creating a download link to a protected resource. Since
 * the download link is not an Ajax call, the usual Authorization header will not be
 * injected, and thus, the REST endpoint cannot be protected using the usual mechanism.
 * <p>
 * Instead, the download link should start by making an Ajax call to get a ticket, then
 * add the ticket to the link being opened as a ticket.
 * In the REST endpoint, the ticket can be validated programmatically.
 * <p>
 * The ticket may be associated with various types of data, known to be valid when the ticket
 * was issued, i.e. the users roles or the current domain. If in a subsequent request the
 * ticket is resolved, then the stored ticket data (roles or domain) can be trusted to be true
 * even in an un-authorized request.
 */
@Singleton
public class TicketService extends BaseCache<String, TicketService.TicketData> {

    final static long LIFESPAN = 60 * 1000;    // 1 minute

    final static String CACHE_ID = "ticketCache";

    @Inject
    private Logger log;

    @Inject
    DomainService domainService;


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
     * Issues a ticket for the given roles.
     * The ticket will expire after 1 minute and can only be validated once.
     * <p>
     * If the roles are empty, the ticket applies to any roles.
     *
     * @param roles the roles the ticket should be tied to
     * @return the ticket
     */
    public String createTicketForRoles(String... roles) {

        // Construct a unique ticket
        String ticket = UUID.randomUUID().toString();
        if (roles == null) {
            roles = new String[0];
        }
        super.getCache().put(ticket, TicketData.rolesTicket(roles));

        return ticket;
    }


    /**
     * Checks if the ticket is valid for at least one of the given roles.
     * The check invalidates the ticket, so, validation can be done only once.
     *
     * If no roles is passed along, the function will return true as long as the
     * ticket is valid.
     *
     * @param ticket the ticket
     * @param roles the roles to check validity for
     * @return if the ticket is valid for the give roles.
     */
    public boolean validateTicketForRoles(String ticket, String... roles) {

        if (ticket == null) {
            return false;
        }

        // Since the ticket is a one-time ticket, remove it from the cache
        TicketData ticketData = super.getCache().remove(ticket);
        String[] ticketRoles = ticketData != null ? ticketData.getRoles() : null;

        // Ticket not found (or expired)
        if (ticketRoles == null) {
            return false;
        }

        // If the ticket was tied to no roles, or if no roles is specified,
        // the ticket is valid
        if (ticketRoles.length == 0) {
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
     * Issues a ticket for the given domain.
     * The ticket will expire after 1 minute and can only be validated once.
     *
     * @param domain the domain the ticket should be tied to
     * @return the ticket
     */
    public String createTicketForDomain(Domain domain) {

        // Construct a unique ticket
        String ticket = UUID.randomUUID().toString();
        String domainClientId = (domain != null) ? domain.getClientId() : null;
        super.getCache().put(ticket, TicketData.domainTicket(domainClientId));

        return ticket;
    }


    /**
     * Checks if the ticket is valid and returns the domain associated with the ticket.
     * The check invalidates the ticket, so, the resolution can be done only once.
     *
     * @param ticket the ticket
     * @return the domain associated with the ticket, or null if the ticket is invalid or no domain resolved.
     */
    public Domain resolveTicketDomain(String ticket) {

        // Since the ticket is a one-time ticket, remove it from the cache
        TicketData ticketData = super.getCache().remove(ticket);

        String domainClientId = ticketData != null ? ticketData.getDomain() : null;

        if (domainClientId != null) {
            return domainService.findByClientId(domainClientId);
        }

        // No joy
        return null;
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
     * Defines the data that may be associated with the ticket
     */
    public static class TicketData {
        String[] roles;
        String domain;

        /** Prevent public instantiation **/
        private TicketData() {
        }

        /** Creates ticket data holding the given roles */
        public static TicketData rolesTicket(String[] roles) {
            TicketData ticketData = new TicketData();
            ticketData.roles = roles;
            return ticketData;
        }

        /** Creates ticket data holding the given domain */
        public static TicketData domainTicket(String domain) {
            TicketData ticketData = new TicketData();
            ticketData.domain = domain;
            return ticketData;
        }

        public String[] getRoles() {
            return roles;
        }

        public String getDomain() {
            return domain;
        }
    }
}
