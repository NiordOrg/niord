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
package org.niord.core.keycloak;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.util.internal.StringUtil;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.*;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.keycloak.representations.adapters.config.AdapterConfig;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.PublishedRealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.niord.core.NiordApp;
import org.niord.core.domain.Domain;
import org.niord.core.settings.SettingsService;
import org.niord.core.settings.annotation.Setting;
import org.niord.core.user.Roles;
import org.niord.core.user.UserService;
import org.niord.core.user.vo.GroupVo;
import org.niord.core.user.vo.UserVo;
import org.niord.core.util.WebUtils;
import org.slf4j.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;
import java.security.PublicKey;
import java.util.*;
import java.util.stream.Collectors;


/**
 * Provides an interface for Keycloak integration.
 * <p>
 * "Domains" in Niord are mapped to Keycloak bearer-only clients.
 */
@ApplicationScoped
public class KeycloakIntegrationService {

    public static final String KEYCLOAK_REALM       = "niord";
    public static final String KEYCLOAK_WEB_CLIENT  = "niord-web";

    @Inject
    UserService userService;

    @Inject
    SettingsService settingsService;

    @Inject
    KeycloakAdminClient keycloakAdminClient;

    @Inject
    @Setting(value = "authServerUrl", defaultValue = "/auth", description = "The Keycloak server url")
    String authServerUrl;

    /**
     * When we use docker compose with the app and keycloak in the same stack. The frontend url and backend url are
     * different. Because the user will access keycloak in the browser typically via localhost:8080/auth. While the app will
     * call the keycloak server in the docker network.
     */
    @Inject
    @Setting(value = "frontEndAuthServerUrl", description = "The Keycloak frontend server url, if different from the keycloak backend url")
    String frontendAuthServerUrl;
    
    @Inject
    @Setting(value = "authServerRealmKey", description = "The public key associated with the Niord realm in Keycloak")
    String authServerRealmKey;

    @Inject
    @Setting(value = "authServerSslRequired", defaultValue = "external", description = "Either 'external', 'none' or 'all'")
    String authServerSslRequired;

    @Inject
    NiordApp app;

    @Inject
    private Logger log;


    /******************************/
    /** Keycloak configuration   **/
    /******************************/

    /** Computes the fully-qualified URL to the Keycloak server */
    private String resolveAuthServerUrl() {
        String url = authServerUrl;
        if (StringUtils.isBlank(url)) {
            throw new RuntimeException("No authServerUrl setting defined");
        }

        // Handle relative auth server url
        if (!url.toLowerCase().startsWith("http")) {
            String baseUri = app.getServerNameForCurrentThreadOrBaseUri();
            if (!url.startsWith("/") && !baseUri.endsWith("/")) {
                url = "/" + url;
            } else if (url.startsWith("/") && baseUri.endsWith("/")) {
                url = url.substring(1);
            }
            url = baseUri + url;
        }

        return url;
    }


    /** Computes the fully-qualified URL to the Niord realm of the Keycloak server **/
    private String resolveAuthServerRealmUrl() {
        return resolveAuthServerUrl() + "/admin/realms/" + KEYCLOAK_REALM;
    }


    /**
     * Queries Keycloak for its public key.
     * Please refer to Keycloak's AdapterDeploymentContext.
     *
     * @return the Keycloak public key
     */
    private PublicKey resolveKeycloakPublicRealmKey() throws Exception {

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
    private String getKeycloakPublicRealmKey() throws Exception {
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
     * Creates a new Keycloak deployment for the given domain domain ID.
     *
     * If the "authServerRealmKey" setting is defined, this is used as the realm public key,
     * otherwise, the public key is looked up from the Keycloak server
     *
     * @param domainId the domain ID
     * @return the Keycloak deployment
     */
    public KeycloakDeployment createKeycloakDeploymentForDomain(String domainId) throws Exception {
        AdapterConfig cfg = new AdapterConfig();
        cfg.setRealm(KEYCLOAK_REALM);
        cfg.setRealmKey(getKeycloakPublicRealmKey());
        cfg.setBearerOnly(true);
        cfg.setAuthServerUrl(authServerUrl);
        cfg.setSslRequired(authServerSslRequired);
        cfg.setResource(domainId);
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
    public Map<String, Object> createKeycloakDeploymentForWebApp() throws Exception {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("realm", KEYCLOAK_REALM);
        cfg.put("realm-public-key", getKeycloakPublicRealmKey());
        cfg.put("public-client", true);
        if (StringUtil.isNullOrEmpty(frontendAuthServerUrl)) {
            cfg.put("auth-server-url", authServerUrl);  
        } else {
            cfg.put("auth-server-url", frontendAuthServerUrl);
        }
        cfg.put("ssl-required", authServerSslRequired);
        cfg.put("resource", KEYCLOAK_WEB_CLIENT);
        cfg.put("use-resource-role-mappings", true);
        return cfg;
    }


    /********************************/
    /** Keycloak clients (domains) **/
    /********************************/

    /**
     * Returns the list of Keycloak clients
     * @return the list of Keycloak clients
     */
    private List<ClientRepresentation> getKeycloakDomainClients() throws Exception {
        return this.keycloakAdminClient.getRealmResource().clients().findAll();
    }


    /**
     * Returns the keycloak client for the given domain
     * @param domainId the domain
     * @return the keycloak client for the given domain, or null if not found
     */
    private ClientRepresentation getKeycloakDomainClient(String domainId) throws Exception {
        // Get hold of the newly created client (with a proper ID)
        return getKeycloakDomainClients().stream()
                .filter(c -> c.getClientId().equals(domainId))
                .findFirst()
                .orElse(null);
    }


    /**
     * Returns the list of Keycloak domain IDs
     * @return the list of Keycloak domain IDs
     */
    public Set<String> getKeycloakDomainIds() throws Exception {

        return getKeycloakDomainClients()
                .stream()
                .map(ClientRepresentation::getClientId)
                .collect(Collectors.toSet());
    }


    /**
     * Creates a new Keycloak client based on the given domain template
     * @param domain the domain template
     * @return if the domain was successfully created
     */
    public boolean createKeycloakDomain(Domain domain) throws Exception {

        // If the domain already exists, bail out
        if (getKeycloakDomainIds().contains(domain.getDomainId())) {
            log.warn("Domain " + domain.getDomainId() + " already exists");
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
        client.setClientId(domain.getDomainId());
        client.setName(domain.getName());

        HttpPost post = new HttpPost(resolveAuthServerRealmUrl() + "/clients");
        post.setEntity(new StringEntity(mapper.writeValueAsString(client), ContentType.APPLICATION_JSON));

        // Create the client in Keycloak
        boolean success = executeAdminRequest(post, true, is -> true);

        if (!success) {
            log.error("Failed creating Keycloak domain client " + domain.getDomainId());
            return false;
        }
        log.info("Created Keycloak domain client " + domain.getDomainId());

        // Get hold of the newly created client (with a proper ID)
        client = getKeycloakDomainClient(domain.getDomainId());
        String clientsUri = resolveAuthServerRealmUrl() + "/clients/" + client.getId();


        // Define the list of roles to set up for the client
        RoleRepresentation[] roleReps = new RoleRepresentation[] {
                new RoleRepresentation(Roles.USER, "User", false),
                new RoleRepresentation(Roles.EDITOR, "Editor", false),
                new RoleRepresentation(Roles.ADMIN, "Administrator", false),
                new RoleRepresentation(Roles.SYSADMIN, "System administrator", false),
        };


        // Create the roles in Keycloak.
        // All roles, bar the first, are composite roles that include all previously defined roles
        List<RoleRepresentation> prevRoles = new ArrayList<>();
        for (RoleRepresentation role : roleReps) {
            // Post the new role
            post = new HttpPost(clientsUri + "/roles");
            post.setEntity(new StringEntity(mapper.writeValueAsString(role), ContentType.APPLICATION_JSON));
            success &= executeAdminRequest(post, true, is -> true);
            log.info("Created role " + role.getName() + " for client " + domain.getDomainId());

            // Fetch the newly created role, in order to retrieve its ID
            role = executeAdminRequest(
                    new HttpGet(clientsUri + "/roles/" + role.getName()),
                    true, // Add auth header
                    is -> new ObjectMapper().readValue(is, RoleRepresentation.class)
            );

            // The roles are ordered, so that a roles is a composite of its previous roles
            if (!prevRoles.isEmpty()) {
                post = new HttpPost(clientsUri + "/roles/" + role.getName() + "/composites");
                post.setEntity(new StringEntity(mapper.writeValueAsString(prevRoles), ContentType.APPLICATION_JSON));
                success &= executeAdminRequest(post, true, is -> true);
            }
            prevRoles.add(role);

        }

        return success;
    }

    /******************************/
    /** Keycloak users & groups  **/
    /******************************/

    /**
     * Adds the user to Keycloak and the local Niord DB
     * @param user the template user to add
     */
    public void addKeycloakUser(UserVo user) throws Exception {

        UserRepresentation userRep = new UserRepresentation();
        userRep.setUsername(user.getUsername());
        userRep.setEmail(user.getEmail());
        userRep.setFirstName(user.getFirstName());
        userRep.setLastName(user.getLastName());
        userRep.setEnabled(true);
        if (user.getKeycloakActions() != null) {
            userRep.setRequiredActions(user.getKeycloakActions());
        }

        HttpPost post = new HttpPost(resolveAuthServerRealmUrl() + "/users");
        post.setEntity(new StringEntity(new ObjectMapper().writeValueAsString(userRep), ContentType.APPLICATION_JSON));

        executeAdminRequest(post, true, is -> true);

        // Check if we need to reset the use password
        resetKeycloakPassword(user);
    }


    /**
     * Updates the user in Keycloak and the local Niord DB
     * @param user the template user to update
     */
    public void updateKeycloakUser(UserVo user) throws Exception {

        String userUrl = resolveAuthServerRealmUrl() + "/users/" + user.getKeycloakId();

        // Look up the original user
        UserRepresentation origUser = executeAdminRequest(
                new HttpGet(userUrl),
                true,
                is -> new ObjectMapper().readValue(is, UserRepresentation.class));

        // Update the user
        origUser.setUsername(user.getUsername());
        origUser.setEmail(user.getEmail());
        origUser.setFirstName(user.getFirstName());
        origUser.setLastName(user.getLastName());
        origUser.setEnabled(true);
        if (user.getKeycloakActions() != null) {
            origUser.setRequiredActions(user.getKeycloakActions());
        }

        HttpPut put = new HttpPut(userUrl);
        put.setEntity(new StringEntity(new ObjectMapper().writeValueAsString(origUser), ContentType.APPLICATION_JSON));
        executeAdminRequest(put, true, is -> true);

        // Check if we need to reset the use password
        resetKeycloakPassword(user);
    }


    /** Resets the user password **/
    private void resetKeycloakPassword(UserVo user) throws Exception {

        if (StringUtils.isNotBlank(user.getKeycloakPassword())) {

            String userId = user.getKeycloakId();
            if (StringUtils.isBlank(userId)) {
                List<UserVo> users = searchKeycloakUsers(Collections.singletonMap("username", user.getUsername()), 0, 1);
                if (users.isEmpty()) {
                    throw new Exception("No user with username " + user.getUsername());
                }
                userId = users.get(0).getKeycloakId();
            }

            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(user.getKeycloakPassword());
            credential.setTemporary(false);

            HttpPut put = new HttpPut(resolveAuthServerRealmUrl() + "/users/" + userId + "/reset-password");
            put.setEntity(new StringEntity(new ObjectMapper().writeValueAsString(credential), ContentType.APPLICATION_JSON));
            executeAdminRequest(put, true, is -> true);
        }
    }

    /**
     * Deletes the user from Keycloak
     * @param userId the user to delete
     */
    public void deleteKeycloakUser(String userId) throws Exception {
        executeAdminRequest(new HttpDelete(resolveAuthServerRealmUrl() + "/users/" + WebUtils.encodeURIComponent(userId)),
                true,
                is -> true);
    }


    /**
     * Searches the users from Keycloak matching the given search criteria
     * @return the users from Keycloak
     */
    public List<UserVo> searchKeycloakUsers(String search, int first, int max) throws Exception {
        return searchKeycloakUsers(Collections.singletonMap("search", search), first, max);
    }


    /**
     * Searches the users from Keycloak matching the given search criteria
     * @return the users from Keycloak
     */
    private List<UserVo> searchKeycloakUsers(Map<String, String> paramMap, int first, int max) throws Exception {

        String params = paramMap.entrySet().stream()
                .map(e -> e.getKey() + "=" + WebUtils.encodeURIComponent(e.getValue()))
                .collect(Collectors.joining("&")) + "&first=" + first + "&max=" + max;

        return executeAdminRequest(
                new HttpGet(resolveAuthServerRealmUrl() + "/users?" + params),
                true, // Add auth header
                is -> {
                    List<UserRepresentation> result = new ObjectMapper()
                            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                            .readValue(is, new TypeReference<List<UserRepresentation>>(){});
                    log.debug("Read users from Keycloak");
                    return result.stream()
                            .map(this::readUser)
                            .collect(Collectors.toList());
                });
    }


    /** Converts a Keycloak GroupRepresentation to a UserVo **/
    private UserVo readUser(UserRepresentation u) {
        UserVo user = new UserVo();
        user.setKeycloakId(u.getId());
        user.setUsername(u.getUsername());
        user.setEmail(u.getEmail());
        user.setFirstName(u.getFirstName());
        user.setLastName(u.getLastName());
        return user;
    }


    /**
     * Loads the group tree from Keycloak
     * @return the group tree from Keycloak
     */
    public List<GroupVo> getKeycloakGroups() throws Exception {
        return executeAdminRequest(
                new HttpGet(resolveAuthServerRealmUrl() + "/groups"),
                true, // Add auth header
                is -> {
                    List<GroupRepresentation> result = new ObjectMapper()
                            .readValue(is, new TypeReference<List<GroupRepresentation>>(){});
                    log.debug("Read groups from Keycloak");
                    return result.stream()
                            .map(this::readGroup)
                            .collect(Collectors.toList());
                });
    }


    /**
     * Returns the groups associated with the given user from Keycloak
     * @return the groups associated with the given user from Keycloak
     */
    public List<GroupVo> getKeycloakUserGroups(String userId) throws Exception {
        return executeAdminRequest(
                new HttpGet(resolveAuthServerRealmUrl()
                        + "/users/" + WebUtils.encodeURIComponent(userId) + "/groups"),
                true, // Add auth header
                is -> {
                    List<GroupRepresentation> result = new ObjectMapper()
                            .readValue(is, new TypeReference<List<GroupRepresentation>>(){});
                    log.debug("Read user groups from Keycloak");
                    return result.stream()
                            .map(this::readGroup)
                            .collect(Collectors.toList());
                });
    }


    /** Converts a Keycloak GroupRepresentation to a GroupVo **/
    private GroupVo readGroup(GroupRepresentation g) {
        GroupVo group = new GroupVo();
        group.setId(g.getId());
        group.checkCreateDesc("en").setName(g.getName());
        group.setPath(g.getPath());
        if (g.getSubGroups() != null && !g.getSubGroups().isEmpty()) {
            group.setChildren(g.getSubGroups().stream()
                .map(this::readGroup)
                .collect(Collectors.toList()));
        }
        return group;
    }


    /**
     * Assign the user to the given group
     * @param userId the Keycloak user ID
     * @param groupId the Keycloak group ID
     */
    public void joinKeycloakGroup(String userId, String groupId) throws Exception {
        HttpPut put = new HttpPut(resolveAuthServerRealmUrl()
                + "/users/" + WebUtils.encodeURIComponent(userId)
                + "/groups/" + WebUtils.encodeURIComponent(groupId));
        executeAdminRequest(put, true, is -> true);
    }


    /**
     * Remove the user from the given group
     * @param userId the Keycloak user ID
     * @param groupId the Keycloak group ID
     */
    public void leaveKeycloakGroup(String userId, String groupId) throws Exception {
        HttpDelete del = new HttpDelete(resolveAuthServerRealmUrl()
                + "/users/" + WebUtils.encodeURIComponent(userId)
                + "/groups/" + WebUtils.encodeURIComponent(groupId));
        executeAdminRequest(del, true, is -> true);
    }


    /** Returns the Keycloak client (domain) roles for the given group */
    public List<String> getKeycloakRoles(Domain domain, String groupId) throws Exception {

        ClientRepresentation client = getKeycloakDomainClient(domain.getDomainId());

        return executeAdminRequest(
                new HttpGet(resolveAuthServerRealmUrl() + "/groups/"
                            + groupId + "/role-mappings/clients/" + client.getId()),
                true, // Add auth header
                is -> {
                    List<RoleRepresentation> result = new ObjectMapper()
                            .readValue(is, new TypeReference<List<RoleRepresentation>>(){});
                    log.debug("Read roles from Keycloak");
                    return result.stream()
                            .map(RoleRepresentation::getName)
                            .collect(Collectors.toList());
                });
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
            Principal keycloakPrincipal = userService.getCallerPrincipal();
            if (keycloakPrincipal == null) {
                throw new Exception("Unable to execute request " + request.getURI() + ". User not authenticated");
            }
            request.addHeader("Authorization", "Bearer " + userService.getKeycloakAccessToken());
        }

        // For e.g. "*.e-navigation.net", with no intermediate certificates specified, you will get an
        // "unable to find valid certification path to requested target" exception.
        // Code around this.
        // See https://stackoverflow.com/questions/19517538/ignoring-ssl-certificate-in-apache-httpclient-4-3
        SSLContextBuilder builder = new SSLContextBuilder();
        builder.loadTrustMaterial(null, (chain, authType) -> true);

        SSLConnectionSocketFactory sslSF = new SSLConnectionSocketFactory(builder.build(),
                SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

        CloseableHttpClient client = HttpClients.custom()
                .setSSLSocketFactory(sslSF)
                .setHostnameVerifier(new AllowAllHostnameVerifier())
                .build();

        try (CloseableHttpResponse response = client.execute(request)) {
            int status = response.getStatusLine().getStatusCode();
            if (status < 200 || status > 299) {
                try {
                    response.getEntity().getContent().close();
                } catch (Exception ignored) {
                }
                throw new Exception("Unable to execute request " + request.getURI() + ", status = " + status);
            }

            HttpEntity entity = response.getEntity();
            if (entity == null) {
                return responseHandler.execute(null);
            }

            try (InputStream is = entity.getContent()) {
                return responseHandler.execute(is);
            }
        }
    }

    /**
     * Interface that is passed along to the executeAdminRequest() function and handles the response
     */
    private interface KeycloakResponseHandler<R> {
        R execute(InputStream in) throws IOException;
    }
}
