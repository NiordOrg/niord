package org.niord.core.user;

import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.util.concurrent.IsolationLevel;
import org.niord.core.cache.BaseCache;
import org.slf4j.Logger;

import javax.enterprise.context.ApplicationScoped;
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
 */
@ApplicationScoped
public class TicketService extends BaseCache<String, String[]> {

    final static long LIFESPAN = 60 * 1000;    // 1 minute

    final static String CACHE_ID = "ticketCache";

    @Inject
    private Logger log;


    /** {@inheritDoc} */
    @Override
    public String getCacheId() {
        return CACHE_ID;
    }


    /**
     * Issue a ticket for the given roles.
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
        getCache().put(ticket, roles);

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
    public boolean validateTicket(String ticket, String... roles) {

        if (ticket == null) {
            return false;
        }

        // Since the ticket is one-time, remove it from the cache
        String[] ticketRoles = getCache().remove(ticket);

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


}
