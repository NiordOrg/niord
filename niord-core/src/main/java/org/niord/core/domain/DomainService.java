/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.niord.core.domain;

import org.niord.core.area.Area;
import org.niord.core.category.Category;
import org.niord.core.keycloak.KeycloakIntegrationService;
import org.niord.core.message.MessageService;
import org.niord.core.service.BaseService;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.util.List;
import java.util.Set;

/**
 * Provides an interface for managing application domains
 */
@SuppressWarnings("unused")
public class DomainService extends BaseService {

    @Inject
    private Logger log;

    @Inject
    private KeycloakIntegrationService keycloakService;

    @Inject
    MessageService messageService;

    /**
     * Returns the domain with the given clientId
     * @param clientId the clientId
     * @return the domain with the given clientId
     */
    public Domain findByClientId(String clientId) {
        try {
            return em
                    .createNamedQuery("Domain.findByClientId", Domain.class)
                    .setParameter("clientId", clientId)
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
                Set<String> keycloakClients = keycloakService.getKeycloakDomainClientIds();
                domains.stream()
                        .forEach(d -> d.setInKeycloak(keycloakClients.contains(d.getClientId())));
            } catch (Exception e) {
                log.error("Failed loading Keycloak states for domains" + e);
            }

        }
        return domains;
    }


    /**
     * Updates the domain data from the domain template
     * @param domain the domain to update
     * @return the updated domain
     */
    public Domain updateDomain(Domain domain) {
        Domain original = findByClientId(domain.getClientId());
        if (original == null) {
            throw new IllegalArgumentException("Cannot update non-existing domain "
                    + domain.getClientId());
        }

        // Copy the domain data
        original.setName(domain.getName());

        // Substitute the areas with the persisted ones
        original.setAreas(persistedList(Area.class, domain.getAreas()));

        // Substitute the categories with the persisted ones
        original.setCategories(persistedList(Category.class, domain.getCategories()));

        // Substitute the message series with the persisted ones
        original.setMessageSeries(messageService.persistedMessageSeries(domain.getMessageSeries()));

        return saveEntity(original);
    }


    /**
     * Creates a new domain based on the domain template
     * @param domain the domain to create
     * @return the created domain
     */
    public Domain createDomain(Domain domain, boolean createInKeycloak) {
        Domain original = findByClientId(domain.getClientId());
        if (original != null) {
            throw new IllegalArgumentException("Cannot create domain with duplicate client ID "
                    + domain.getClientId());
        }

        // Substitute the areas with the persisted ones
        domain.setAreas(persistedList(Area.class, domain.getAreas()));

        // Substitute the categories with the persisted ones
        domain.setCategories(persistedList(Category.class, domain.getCategories()));

        // Substitute the message series with the persisted ones
        domain.setMessageSeries(messageService.persistedMessageSeries(domain.getMessageSeries()));

        domain = saveEntity(domain);

        // If request, create the domain in Keycloak - but do not throw an error in case of an error
        if (createInKeycloak) {
            try {
                keycloakService.createKeycloakDomainClient(domain);
            } catch (Exception e) {
                log.error("Error creating new domain in Keycloak", e);
            }
        }

        return domain;
    }


    /**
     * Deletes the domain
     * @param clientId the client id of the domain to delete
     */
    public boolean deleteDomain(String clientId) {

        Domain domain = findByClientId(clientId);
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

        boolean result = keycloakService.createKeycloakDomainClient(domain);

        log.info(String.format("Created client %s in Keycloak in %d ms. Result: %s",
                domain.getClientId(), System.currentTimeMillis() - t0, result));

    }
}
