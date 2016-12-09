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
package org.niord.core.domain;

import org.apache.commons.lang.StringUtils;
import org.niord.core.area.Area;
import org.niord.core.category.Category;
import org.niord.core.keycloak.KeycloakIntegrationService;
import org.niord.core.message.MessageSeriesService;
import org.niord.core.service.BaseService;
import org.niord.core.user.TicketService;
import org.niord.core.user.UserService;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Provides an interface for managing application domains
 */
@Stateless
@SuppressWarnings("unused")
public class DomainService extends BaseService {

    private final static ThreadLocal<String> THREAD_LOCAL_DOMAIN = new ThreadLocal<>();

    @Inject
    Logger log;

    @Inject
    KeycloakIntegrationService keycloakService;

    @Inject
    MessageSeriesService messageSeriesService;

    @Inject
    UserService userService;

    @Inject
    TicketService ticketService;


    /** Returns the current domain or null if none are set */
    public Domain currentDomain() {

        Domain currentDomain = null;

        String domainId = THREAD_LOCAL_DOMAIN.get();
        if (StringUtils.isNotBlank(domainId)) {
            currentDomain = findByDomainId(domainId);
        }

        if (currentDomain == null) {
            // Check if the ticket service has resolved a ticket for the current thread
            TicketService.TicketData ticketData = ticketService.getTicketDataForCurrentThread();
            if (ticketData != null) {
                domainId = ticketData.getDomain();
                currentDomain = findByDomainId(domainId);
            }
        }

        return currentDomain;
    }



    /**
     * May be called, e.g. from a servlet filter to set the
     * ticket for the current thread based on a request header or DNS name.
     * <p>
     * Must be followed up with a call to call to removeDomainForCurrentThread()
     * @param domainId the domain ID to set
     */
    public void setDomainForCurrentThread(String domainId) {
        if (StringUtils.isNotBlank(domainId)) {
            THREAD_LOCAL_DOMAIN.set(domainId);
        }
    }


    /**
     * May be called, e.g. from a servlet filter to remove the
     * current domain from the current thread
     * <p>
     * Must be preceded by a call to call to setDomainForCurrentThread()
     */
    public void removeDomainForCurrentThread() {
        THREAD_LOCAL_DOMAIN.remove();
    }


    /**
     * Returns the domain with the given domainId
     * @param domainId the domainId
     * @return the domain with the given domainId
     */
    public Domain findByDomainId(String domainId) {
        try {
            return em
                    .createNamedQuery("Domain.findByDomainId", Domain.class)
                    .setParameter("domainId", domainId)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Returns all domains
     * @return all domains
     */
    public List<Domain> getDomains() {
        return getDomains(false);
    }


    /**
     * Returns all domains, optionally with the current Keycloak state
     * @param keycloakState whether to load the state of the domains in Keycloak
     * @return all domains
     */
    public List<Domain> getDomains(boolean keycloakState) {
        List<Domain> domains = getAll(Domain.class);
        if (keycloakState) {
            try {
                Set<String> keycloakClients = keycloakService.getKeycloakDomainIds();
                domains.forEach(d -> d.setInKeycloak(keycloakClients.contains(d.getDomainId())));
            } catch (Exception e) {
                log.error("Failed loading Keycloak states for domains" + e);
            }

        }
        return domains;
    }


    /**
     * Returns the domains where the "publish" flag has been set. These are the domains
     * that would by default be returned by default in public API's for fetching messages
     * @return the published domains
     */
    public List<Domain> getPublishedDomains() {
        return em.createNamedQuery("Domain.getPublishedDomains", Domain.class)
                .getResultList();
    }


    /**
     * Returns all domains for which the current user has the given role
     * @param role the role that the current user must have
     * @return all domains for which the current user has the given role
     */
    public List<Domain> domainsWithUserRole(String role) {
        Set<String> domainIds = userService.getKeycloakDomainIdsForRole(role);
        return getDomains().stream()
                .filter(d -> domainIds.contains(d.getDomainId()))
                .collect(Collectors.toList());
    }


    /**
     * Updates the domain data from the domain template
     * @param domain the domain to update
     * @return the updated domain
     */
    public Domain updateDomain(Domain domain) {
        Domain original = findByDomainId(domain.getDomainId());
        if (original == null) {
            throw new IllegalArgumentException("Cannot update non-existing domain "
                    + domain.getDomainId());
        }

        // Copy the domain data
        original.setName(domain.getName());
        original.setTimeZone(domain.getTimeZone());
        original.setLatitude(domain.getLatitude());
        original.setLongitude(domain.getLongitude());
        original.setZoomLevel(domain.getZoomLevel());
        original.setMessageSortOrder(domain.getMessageSortOrder());
        original.setPublish(domain.getPublish());
        original.setAtons(domain.getAtons());
        original.setSchedule(domain.getSchedule());

        // Substitute the areas with the persisted ones
        original.setAreas(persistedList(Area.class, domain.getAreas()));

        // Substitute the categories with the persisted ones
        original.setCategories(persistedList(Category.class, domain.getCategories()));

        // Substitute the message series with the persisted ones
        original.setMessageSeries(messageSeriesService.persistedMessageSeries(domain.getMessageSeries()));

        return saveEntity(original);
    }


    /**
     * Creates a new domain based on the domain template
     * @param domain the domain to create
     * @return the created domain
     */
    public Domain createDomain(Domain domain, boolean createInKeycloak) {
        Domain original = findByDomainId(domain.getDomainId());
        if (original != null) {
            throw new IllegalArgumentException("Cannot create domain with duplicate domain ID "
                    + domain.getDomainId());
        }

        // Substitute the areas with the persisted ones
        domain.setAreas(persistedList(Area.class, domain.getAreas()));

        // Substitute the categories with the persisted ones
        domain.setCategories(persistedList(Category.class, domain.getCategories()));

        // Substitute the message series with the persisted ones
        domain.setMessageSeries(messageSeriesService.persistedMessageSeries(domain.getMessageSeries()));

        domain = saveEntity(domain);

        // If request, create the domain in Keycloak - but do not throw an error in case of an error
        if (createInKeycloak) {
            try {
                keycloakService.createKeycloakDomain(domain);
            } catch (Exception e) {
                log.error("Error creating new domain in Keycloak", e);
            }
        }

        return domain;
    }


    /**
     * Deletes the domain
     * @param domainId the ID of the domain to delete
     */
    public boolean deleteDomain(String domainId) {

        Domain domain = findByDomainId(domainId);
        if (domain != null) {
            remove(domain);
            return true;
        }
        return false;
    }


    /**
     * Creates the domain as a client in Keycloak
     * @param domain the domain to create a Keycloak client for
     */
    public void createDomainInKeycloak(Domain domain) throws Exception {

        long t0 = System.currentTimeMillis();

        boolean result = keycloakService.createKeycloakDomain(domain);

        log.info(String.format("Created domain %s in Keycloak in %d ms. Result: %s",
                domain.getDomainId(), System.currentTimeMillis() - t0, result));

    }
}
