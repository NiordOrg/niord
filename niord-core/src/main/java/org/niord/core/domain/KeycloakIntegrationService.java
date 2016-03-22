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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.ClientRepresentation;
import org.slf4j.Logger;

import javax.inject.Inject;
import javax.naming.NamingException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Provides an interface for Keycloak integration.
 * <p>
 * "Domains" in Niord are mapped to Keycloak bearer-only clients.
 */
public class KeycloakIntegrationService {

    public static final String KEYCLOAK_REALM       = "niord";
    public static final String KEYCLOAK_WEB_CLIENT  = "niord-web";

    @Inject
    private Logger log;

    // TODO: Inject from settings
    String adminUser = "sysadmin";
    String adminPassword = "sysadmin";


    /**
     * Returns the list of Keycloak clients
     * @return the list of Keycloak clients
     */
    public Set<String> getKeycloakDomainClients() throws Exception {
        return executeAdminOperation(keycloak -> keycloak
                .realm(KEYCLOAK_REALM)
                .clients()
                .findAll()
                .stream()
                .map(ClientRepresentation::getClientId)
                .collect(Collectors.toSet()));
    }


    /**
     * Creates a new Keycloak client based on the given domain template
     * @param domain the domain template
     * @return the list of Keycloak clients
     */
    public boolean createKeycloakDomainClient(Domain domain) throws Exception {

        // Create a template for the new client
        ObjectMapper mapper = new ObjectMapper();
        ClientRepresentation client = mapper.readValue(
                getClass().getResource("/keycloak-client-template.json"),
                ClientRepresentation.class);

        // Instantiate it from the domain
        client.setId(null);
        client.setClientId(domain.getClientId());
        client.setName(domain.getName());

        // Create the client in Keycloak
        return executeAdminOperation(keycloak -> keycloak
                .realm("niord")
                .clients()
                .create(client)
                .getStatus() == 201);
    }


    /**
     * Executes a Keycloak operation and returns the result.
     * Guarantees that the Keycloak client is closed properly.
     *
     * @param operation the Keycloak operation to execute
     * @return the result
     */
    private <R> R executeAdminOperation(KeycloakOperation<R> operation) throws Exception {
        Keycloak keycloak = null;

        try {
            keycloak = createKeycloakAdminClient();
            return operation.execute(keycloak);
        } finally {
            if (keycloak != null) {
                try {
                    keycloak.close();
                } catch (Exception ignored) {
                }
            }
        }
    }


    /**
     * Creates a new Keycloak Admin client. Must be closed after use.
     *
     * @return the Keycloak client
     */
    private Keycloak createKeycloakAdminClient() {
        return Keycloak.getInstance(
                "http://localhost:8080/auth",
                KEYCLOAK_REALM,
                adminUser,
                adminPassword,
                KEYCLOAK_WEB_CLIENT);
    }


    /**
     * Interface that represents a discrete Keycloak operation
     */
    private interface KeycloakOperation<R> {
        R execute(Keycloak keycloak) throws NamingException;
    }

}
