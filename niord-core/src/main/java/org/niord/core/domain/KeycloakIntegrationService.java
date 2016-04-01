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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.PublishedRealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.niord.core.NiordApp;
import org.niord.core.settings.annotation.Setting;
import org.slf4j.Logger;

import javax.inject.Inject;
import javax.naming.NamingException;
import java.io.IOException;
import java.io.InputStream;
import java.security.PublicKey;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.niord.core.settings.Setting.Type.Password;


/**
 * Provides an interface for Keycloak integration.
 * <p>
 * "Domains" in Niord are mapped to Keycloak bearer-only clients.
 */
public class KeycloakIntegrationService {

    public static final String KEYCLOAK_REALM       = "niord";
    public static final String KEYCLOAK_WEB_CLIENT  = "niord-web";


    @Inject
    @Setting(value = "authServerUrl", defaultValue = "/auth", description = "The Keycloak server url")
    String authServerUrl;

    @Inject
    @Setting(value = "authServerAdminUser", description = "The Keycloak user to use for creating Keycloak clients")
    String authServerAdminUser;

    @Inject
    @Setting(value = "authServerAdminPassword", type = Password, description = "The Keycloak password to use for creating Keycloak clients")
    String authServerAdminPassword;

    @Inject
    NiordApp app;

    @Inject
    private Logger log;


    /**
     * Queries Keycloak for its public key
     * @return the Keycloak public key
     */
    public PublicKey resolveKeycloakPublicKey() throws Exception {
        String url = authServerUrl;
        if (StringUtils.isBlank(url)) {
            throw new Exception("No authServerUrl setting defined");
        }

        // Handle relative auth server url
        if (!url.toLowerCase().startsWith("http")) {
            String baseUri = app.getBaseUri();
            if (!url.startsWith("/") && !baseUri.startsWith("/")) {
                url = "/" + url;
            } else if (url.startsWith("/") && baseUri.startsWith("/")) {
                url = url.substring(1);
            }
            url = baseUri + url;
        }

        // TODO: Handle HTTPS ... look at org.keycloak.adapters.AdapterDeploymentContext
        HttpClient client = HttpClientBuilder.create().build();
        HttpGet getRealmInfo = new HttpGet(url + "/realms/" + KEYCLOAK_REALM);

        HttpResponse response = client.execute(getRealmInfo);

        int status = response.getStatusLine().getStatusCode();
        if (status != 200) {
            try {
                response.getEntity().getContent().close();
            } catch (IOException ignored) {
            }
            throw new Exception("Unable to resolve realm public key remotely, status = " + status);
        }

        HttpEntity entity = response.getEntity();
        if (entity == null) {
            throw new Exception("Unable to resolve realm public key remotely.  There was no entity.");
        }

        try (InputStream is = entity.getContent()) {
            PublishedRealmRepresentation rep = new ObjectMapper()
                    .readValue(is, PublishedRealmRepresentation.class);
            return rep.getPublicKey();
        }
    }


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
        ClientRepresentation client = mapper
                .readValue(
                    getClass().getResource("/keycloak-client-template.json"),
                    ClientRepresentation.class);

        // Instantiate it from the domain
        client.setId(null);
        client.setClientId(domain.getClientId());
        client.setName(domain.getName());

        // Create the client in Keycloak
        boolean success = executeAdminOperation(keycloak -> keycloak
                .realm("niord")
                .clients()
                .create(client)
                .getStatus() == 201);

        if (!success) {
            log.error("Failed creating client " + domain.getClientId());
            return false;
        }
        log.info("Created client " + domain.getClientId());


        // Create a template for the client roles
        List<RoleRepresentation> roles =  mapper
                .readValue(
                        getClass().getResource("/keycloak-client-roles-template.json"),
                        new TypeReference<List<RoleRepresentation>>(){});

        // Instantiate the roles from the domain
        roles.forEach(r -> {
            r.setId(null);
            // For composite roles, replace the template client id with the proper client id
            if (r.isComposite() && r.getComposites().getClient().size() == 1) {
                String templateClientId = r.getComposites().getClient().keySet().iterator().next();
                List<String> compositeRoles = r.getComposites().getClient().remove(templateClientId);
                r.getComposites().getClient().put(domain.getClientId(), compositeRoles);
            }
        });

        /**
         * TODO: The code below will fail because the .create() function will yield a 404 error
         * Guessing it's an error in Keycloak, but need to investigate further.

        // Create the client roles in Keycloak
        for (RoleRepresentation role : roles) {
            success = executeAdminOperation(keycloak -> {
                keycloak.realm("niord")
                        .clients()
                        .get(domain.getClientId())
                        .roles()
                        .create(role);
                // Sadly, no error handling when creating roles
                return true;
            });
            log.info("Created role " + role + " for client " + domain.getClientId());
        }
         **/

        return true;
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
    private Keycloak createKeycloakAdminClient() throws Exception {

        if (StringUtils.isBlank(authServerAdminUser) || StringUtils.isBlank(authServerAdminPassword)) {
            throw new Exception("Keycloak admin user/password not properly defined");
        }

        return Keycloak.getInstance(
                "http://localhost:8080/auth",
                KEYCLOAK_REALM,
                authServerAdminUser,
                authServerAdminPassword,
                KEYCLOAK_WEB_CLIENT);
    }


    /**
     * Interface that represents a discrete Keycloak operation
     */
    private interface KeycloakOperation<R> {
        R execute(Keycloak keycloak) throws NamingException;
    }

}
