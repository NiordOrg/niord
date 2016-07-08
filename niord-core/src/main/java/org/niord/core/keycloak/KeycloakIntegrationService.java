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
package org.niord.core.keycloak;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.keycloak.KeycloakPrincipal;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.keycloak.representations.adapters.config.AdapterConfig;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.PublishedRealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.niord.core.NiordApp;
import org.niord.core.domain.Domain;
import org.niord.core.settings.SettingsService;
import org.niord.core.settings.annotation.Setting;
import org.niord.core.user.UserService;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    UserService userService;

    @Inject
    SettingsService settingsService;

    @Inject
    @Setting(value = "authServerUrl", defaultValue = "/auth", description = "The Keycloak server url")
    String authServerUrl;

    @Inject
    @Setting(value = "authServerRealmKey", description = "The public key associated with the Niord realm in Keycloak")
    String authServerRealmKey;

    @Inject
    NiordApp app;

    @Inject
    private Logger log;


    /** Computes the fully-qualified URL to the Keycloak server */
    private String resolveAuthServerUrl() {
        String url = authServerUrl;
        if (StringUtils.isBlank(url)) {
            throw new RuntimeException("No authServerUrl setting defined");
        }

        // Handle relative auth server url
        if (!url.toLowerCase().startsWith("http")) {
            String baseUri = app.getBaseUri();
            if (!url.startsWith("/") && !baseUri.endsWith("/")) {
                url = "/" + url;
            } else if (url.startsWith("/") && baseUri.endsWith("/")) {
                url = url.substring(1);
            }
            url = baseUri + url;
        }

        return url;
    }


    /**
     * Queries Keycloak for its public key.
     * Please refer to Keycloak's AdapterDeploymentContext.
     *
     * @return the Keycloak public key
     */
    public PublicKey resolveKeycloakPublicRealmKey() throws Exception {

        return executeAdminRequest(
                new HttpGet(resolveAuthServerUrl() + "/realms/" + KEYCLOAK_REALM),
                false, // Add auth header
                is -> {
                    PublishedRealmRepresentation rep = new ObjectMapper()
                            .readValue(is, PublishedRealmRepresentation.class);
                    log.debug("Read the niord realm representation");
                    return rep.getPublicKey();
                });
    }


    /**
     * Returns the Keycloak public key for the Niord realm.
     * The public key is returned in the format used by keycloak.json.
     * <p>
     * If the setting for the public key has not been defined, the public key is
     * fetched directly from Keycloak.
     *
     * @return the Keycloak public key
     */
    public String getKeycloakPublicRealmKey() throws Exception {
        if (StringUtils.isNotBlank(authServerRealmKey)) {
            return authServerRealmKey;
        }

        // Fetch the public key from Keycloak
        PublicKey publicKey = resolveKeycloakPublicRealmKey();
        authServerRealmKey = new String(Base64.getEncoder().encode(publicKey.getEncoded()), "utf-8");

        // Update the underlying setting
        settingsService.set("authServerRealmKey", authServerRealmKey);
        return authServerRealmKey;
    }


    /**
     * Creates a new Keycloak deployment for the given domain client ID.
     *
     * If the "authServerRealmKey" setting is defined, this is used as the realm public key,
     * otherwise, the public key is looked up from the Keycloak server
     *
     * @param clientId the domain client ID
     * @return the Keycloak deployment
     */
    public KeycloakDeployment createKeycloakDeploymentForDomain(String clientId) throws Exception {
        AdapterConfig cfg = new AdapterConfig();
        cfg.setRealm(KEYCLOAK_REALM);
        cfg.setRealmKey(getKeycloakPublicRealmKey());
        cfg.setBearerOnly(true);
        cfg.setAuthServerUrl(authServerUrl);
        cfg.setSslRequired("external");
        cfg.setResource(clientId);
        cfg.setUseResourceRoleMappings(true);

        return KeycloakDeploymentBuilder.build(cfg);
    }


    /**
     * Creates a new Keycloak deployment for the niord-web web application.
     *
     * If the "authServerRealmKey" setting is defined, this is used as the realm public key,
     * otherwise, the public key is looked up from the Keycloak server
     *
     * @return the Keycloak deployment
     */
    public Map<String, String> createKeycloakDeploymentForWebApp() throws Exception {
        Map<String, String> cfg = new HashMap<>();
        cfg.put("realm", KEYCLOAK_REALM);
        cfg.put("realm-public-key", getKeycloakPublicRealmKey());
        cfg.put("public-client", "true");
        cfg.put("auth-server-url", authServerUrl);
        cfg.put("ssl-required", "external");
        cfg.put("resource", KEYCLOAK_WEB_CLIENT);
        cfg.put("use-resource-role-mappings", "true");
        return cfg;
    }


    /**
     * Returns the list of Keycloak clients
     * @return the list of Keycloak clients
     */
    public List<ClientRepresentation> getKeycloakDomainClients() throws Exception {

        return executeAdminRequest(
                new HttpGet(resolveAuthServerUrl() + "/admin/realms/" + KEYCLOAK_REALM + "/clients"),
                true, // Add auth header
                is -> {
                    List<ClientRepresentation> result = new ObjectMapper().readValue(is, new TypeReference<List<ClientRepresentation>>(){});
                    log.debug("Read clients from Keycloak");
                    return result;
                });
    }


    /**
     * Returns the list of Keycloak client ids
     * @return the list of Keycloak client ids
     */
    public Set<String> getKeycloakDomainClientIds() throws Exception {

        return getKeycloakDomainClients()
                .stream()
                .map(ClientRepresentation::getClientId)
                .collect(Collectors.toSet());
    }


    /**
     * Creates a new Keycloak client based on the given domain template
     * @param domain the domain template
     * @return the list of Keycloak clients
     */
    public boolean createKeycloakDomainClient(Domain domain) throws Exception {

        // If the domain already exists, bail out
        if (getKeycloakDomainClientIds().contains(domain.getClientId())) {
            log.warn("Domain " + domain.getClientId() + " already exists");
            return false;
        }

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

        HttpPost post = new HttpPost(resolveAuthServerUrl() + "/admin/realms/" + KEYCLOAK_REALM + "/clients");
        post.setEntity(new StringEntity(mapper.writeValueAsString(client), ContentType.APPLICATION_JSON));

        // Create the client in Keycloak
        boolean success = executeAdminRequest(post, true, is -> true);

        if (!success) {
            log.error("Failed creating client " + domain.getClientId());
            return false;
        }
        log.info("Created client " + domain.getClientId());

        // Get hold of the newly created client (with a proper ID)
        client = getKeycloakDomainClients().stream()
                .filter(c -> c.getClientId().equals(domain.getClientId()))
                .findFirst()
                .orElse(null);
        String clientsUri = resolveAuthServerUrl() + "/admin/realms/" + KEYCLOAK_REALM + "/clients/" + client.getId();


        // Define the list of roles to set up for the client
        RoleRepresentation[] roleReps = new RoleRepresentation[] {
                new RoleRepresentation("editor", "Editor", false),
                new RoleRepresentation("admin", "Administrator", false),
                new RoleRepresentation("sysadmin", "System administrator", false),
        };


        // Create the roles in Keycloak
        RoleRepresentation prevRole = null;
        for (RoleRepresentation role : roleReps) {
            // Post the new role
            post = new HttpPost(clientsUri + "/roles");
            post.setEntity(new StringEntity(mapper.writeValueAsString(role), ContentType.APPLICATION_JSON));
            success &= executeAdminRequest(post, true, is -> true);
            log.info("Created role " + role.getName() + " for client " + domain.getClientId());

            // The roles are ordered, so that a roles is a composite of its previous roles
            if (prevRole != null) {
                updateCompositeRole(client.getClientId(), role, prevRole.getName());
                post = new HttpPost(clientsUri + "/roles/" + role.getName() + "/composites");
                post.setEntity(new StringEntity(mapper.writeValueAsString(role), ContentType.APPLICATION_JSON));
                // Arghh - does not work
                //success &= executeAdminRequest(post, true, is -> true);
            }
            prevRole = role;

        }

        return success;
    }


    /** Updates the composite relation of the role */
    private void updateCompositeRole(String clientId, RoleRepresentation role, String... nestedRoles) {
        Map<String, List<String>> clientRoles = new HashMap<>();
        clientRoles.put(clientId, Arrays.asList(nestedRoles));
        RoleRepresentation.Composites composites = new RoleRepresentation.Composites();
        composites.setClient(clientRoles);
        role.setComposites(composites);
    }


    /**
     * Executes a Keycloak admin request and returns the result.
     *
     * @param request the Keycloak request to execute
     * @param auth whether to add a Bearer authorization header or not
     * @param responseHandler the response handler
     * @return the result
     */
    private <R> R executeAdminRequest(HttpRequestBase request, boolean auth, KeycloakResponseHandler<R> responseHandler) throws Exception {

        if (auth) {
            KeycloakPrincipal keycloakPrincipal = userService.getCallerPrincipal();
            if (keycloakPrincipal == null) {
                throw new Exception("Unable to execute request " + request.getURI() + ". User not authenticated");
            }
            request.addHeader("Authorization", "Bearer " + keycloakPrincipal.getKeycloakSecurityContext().getTokenString());
        }

        // TODO: Check if this works with https based on self-signed certificates
        HttpClient client = HttpClients.custom().setHostnameVerifier(new AllowAllHostnameVerifier()).build();

        HttpResponse response = client.execute(request);

        int status = response.getStatusLine().getStatusCode();
        if (status != 200 && status != 201) {
            try {
                response.getEntity().getContent().close();
            } catch (IOException ignored) {
            }
            throw new Exception("Unable to execute request " + request.getURI() + ", status = " + status);
        }

        HttpEntity entity = response.getEntity();
        if (entity == null) {
            throw new Exception("Unable to execute request " + request.getURI() + ".  There was no entity.");
        }

        try (InputStream is = entity.getContent()) {
            return responseHandler.execute(is);
        }
    }


    /**
     * Interface that is passed along to the executeAdminRequest() function and handles the response
     */
    private interface KeycloakResponseHandler<R> {
        R execute(InputStream in) throws IOException;
    }
}
