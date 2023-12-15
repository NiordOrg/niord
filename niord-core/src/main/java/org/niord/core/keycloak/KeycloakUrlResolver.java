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

import org.apache.http.client.HttpClient;
import org.keycloak.adapters.KeycloakDeployment;
//import org.keycloak.adapters.authentication.ClientCredentialsProvider;
import org.keycloak.protocol.oidc.client.authentication.ClientCredentialsProvider;

import org.keycloak.adapters.rotation.PublicKeyLocator;
import org.keycloak.adapters.spi.HttpFacade;
import org.keycloak.common.enums.RelativeUrlsUsed;
import org.keycloak.common.enums.SslRequired;
import org.keycloak.common.util.KeycloakUriBuilder;
import org.keycloak.enums.TokenStore;

import java.net.URI;
import java.util.Map;

/**
 *
 * Originally, Niord would use a Keycloak overlay in the Niord Wildfly.
 * This caused A LOT of problems in the early versions of Keycloak.
 *
 * In order to fix some of these problems, a custom KeycloakUrlResolver was developed.
 * However, as Keycloak now flags the overlay as "not for production", we have stopped
 * using overlays, and instead run Keycloak as a separate server.
 *
 * Hence, the bug-fixing code of the custom KeycloakUrlResolver is not needed anymore.
 *
 * ==========================================
 *
 * IMPORTANT: In order to handle relative auth-server-url's, various functions and helper classes
 * has been copied from Keycloak AdapterDeploymentContext class.
 * <p>
 * This class must thus be kept up-to-date with future versions of Keycloak.
 */
@Deprecated
public class KeycloakUrlResolver {


    private KeycloakUrlResolver() {
    }


    /**
     * Factory method for creating a new URL resolver
     * @return a new URL resolver
     */
    public static KeycloakUrlResolver newInstance() {
        return new KeycloakUrlResolver();
    }


    /**
     * This function has been copied (and modified) from the Keycloak AdapterDeploymentContext class.
     * It should be kept up-to-date with future versions of Keycloak.
     */
    public KeycloakDeployment resolveUrls(KeycloakDeployment deployment, HttpFacade.Request facadeRequest) {
        if (deployment.getRelativeUrls() == RelativeUrlsUsed.NEVER) {
            // Absolute URI are already set to everything
            return deployment;
        } else {
            DeploymentDelegate delegate = new DeploymentDelegate(deployment);
            delegate.setAuthServerBaseUrl(getBaseBuilder(deployment, facadeRequest).build().toString());
            return delegate;
        }
    }


    /**
     * This function has been copied (and modified) from the Keycloak AdapterDeploymentContext class.
     * It should be kept up-to-date with future versions of Keycloak.
     */
    private KeycloakUriBuilder getBaseBuilder(KeycloakDeployment deployment, HttpFacade.Request facadeRequest) {
        String base = deployment.getAuthServerBaseUrl();
        KeycloakUriBuilder builder = KeycloakUriBuilder.fromUri(base);
        URI request = URI.create(facadeRequest.getURI());
        String scheme = request.getScheme();
        if (deployment.getSslRequired().isRequired(facadeRequest.getRemoteAddr())) {
            scheme = "https";
            if (!request.getScheme().equals(scheme) && request.getPort() != -1) {
                throw new RuntimeException("Can't resolve relative url from adapter config.");
            }
        }
        builder.scheme(scheme);
        builder.host(request.getHost());
        if (request.getPort() != -1) {
            builder.port(request.getPort());
        }
        return builder;
    }


    /**
     * IMPORTANT: This class has been copied from the Keycloak AdapterDeploymentContext class.
     * It should be kept up-to-date with future versions of Keycloak.
     *
     *
     * This delegate is used to store temporary, per-request metadata like request resolved URLs.
     * Ever method is delegated except URL get methods and isConfigured()
     *
     */
    private static class DeploymentDelegate extends KeycloakDeployment {
        protected KeycloakDeployment delegate;

        DeploymentDelegate(KeycloakDeployment delegate) {
            this.delegate = delegate;
        }

        void setAuthServerBaseUrl(String authServerBaseUrl) {
            this.authServerBaseUrl = authServerBaseUrl;
            KeycloakUriBuilder serverBuilder = KeycloakUriBuilder.fromUri(authServerBaseUrl);
            resolveUrls(serverBuilder);
        }

        @Override
        public RelativeUrlsUsed getRelativeUrls() {
            return delegate.getRelativeUrls();
        }

        @Override
        public String getRealmInfoUrl() {
            return (this.realmInfoUrl != null) ? this.realmInfoUrl : delegate.getRealmInfoUrl();
        }

        @Override
        public String getTokenUrl() {
            return (this.tokenUrl != null) ? this.tokenUrl : delegate.getTokenUrl();
        }

        @Override
        public KeycloakUriBuilder getLogoutUrl() {
            return (this.logoutUrl != null) ? this.logoutUrl : delegate.getLogoutUrl();
        }

        @Override
        public String getAccountUrl() {
            return (this.accountUrl != null) ? this.accountUrl : delegate.getAccountUrl();
        }

        @Override
        public String getRegisterNodeUrl() {
            return (this.registerNodeUrl != null) ? this.registerNodeUrl : delegate.getRegisterNodeUrl();
        }

        @Override
        public String getUnregisterNodeUrl() {
            return (this.unregisterNodeUrl != null) ? this.unregisterNodeUrl : delegate.getUnregisterNodeUrl();
        }

        @Override
        public String getResourceName() {
            return delegate.getResourceName();
        }

        @Override
        public String getRealm() {
            return delegate.getRealm();
        }

        @Override
        public void setRealm(String realm) {
            delegate.setRealm(realm);
        }

        @Override
        public void setPublicKeyLocator(PublicKeyLocator publicKeyLocator) {
            delegate.setPublicKeyLocator(publicKeyLocator);
        }

        @Override
        public PublicKeyLocator getPublicKeyLocator() {
            return delegate.getPublicKeyLocator();
        }
        @Override
        public void setResourceName(String resourceName) {
            delegate.setResourceName(resourceName);
        }

        @Override
        public boolean isBearerOnly() {
            return delegate.isBearerOnly();
        }

        @Override
        public void setBearerOnly(boolean bearerOnly) {
            delegate.setBearerOnly(bearerOnly);
        }

        @Override
        public boolean isEnableBasicAuth() {
            return delegate.isEnableBasicAuth();
        }

        @Override
        public void setEnableBasicAuth(boolean enableBasicAuth) {
            delegate.setEnableBasicAuth(enableBasicAuth);
        }

        @Override
        public boolean isPublicClient() {
            return delegate.isPublicClient();
        }

        @Override
        public void setPublicClient(boolean publicClient) {
            delegate.setPublicClient(publicClient);
        }

        @Override
        public Map<String, Object> getResourceCredentials() {
            return delegate.getResourceCredentials();
        }

        @Override
        public void setResourceCredentials(Map<String, Object> resourceCredentials) {
            delegate.setResourceCredentials(resourceCredentials);
        }

        @Override
        public void setClientAuthenticator(ClientCredentialsProvider clientAuthenticator) {
            delegate.setClientAuthenticator(clientAuthenticator);
        }

        @Override
        public ClientCredentialsProvider getClientAuthenticator() {
            return delegate.getClientAuthenticator();
        }

        @Override
        public HttpClient getClient() {
            return delegate.getClient();
        }

        @Override
        public void setClient(HttpClient client) {
            delegate.setClient(client);
        }

        @Override
        public String getScope() {
            return delegate.getScope();
        }

        @Override
        public void setScope(String scope) {
            delegate.setScope(scope);
        }

        @Override
        public SslRequired getSslRequired() {
            return delegate.getSslRequired();
        }

        @Override
        public void setSslRequired(SslRequired sslRequired) {
            delegate.setSslRequired(sslRequired);
        }

        @Override
        public TokenStore getTokenStore() {
            return delegate.getTokenStore();
        }

        @Override
        public void setTokenStore(TokenStore tokenStore) {
            delegate.setTokenStore(tokenStore);
        }

        @Override
        public String getStateCookieName() {
            return delegate.getStateCookieName();
        }

        @Override
        public void setStateCookieName(String stateCookieName) {
            delegate.setStateCookieName(stateCookieName);
        }

        @Override
        public boolean isUseResourceRoleMappings() {
            return delegate.isUseResourceRoleMappings();
        }

        @Override
        public void setUseResourceRoleMappings(boolean useResourceRoleMappings) {
            delegate.setUseResourceRoleMappings(useResourceRoleMappings);
        }

        @Override
        public boolean isCors() {
            return delegate.isCors();
        }

        @Override
        public void setCors(boolean cors) {
            delegate.setCors(cors);
        }

        @Override
        public int getCorsMaxAge() {
            return delegate.getCorsMaxAge();
        }

        @Override
        public void setCorsMaxAge(int corsMaxAge) {
            delegate.setCorsMaxAge(corsMaxAge);
        }

        @Override
        public String getCorsAllowedHeaders() {
            return delegate.getCorsAllowedHeaders();
        }

        @Override
        public void setNotBefore(int notBefore) {
            delegate.setNotBefore(notBefore);
        }

        @Override
        public int getNotBefore() {
            return delegate.getNotBefore();
        }

        @Override
        public void setExposeToken(boolean exposeToken) {
            delegate.setExposeToken(exposeToken);
        }

        @Override
        public boolean isExposeToken() {
            return delegate.isExposeToken();
        }

        @Override
        public void setCorsAllowedMethods(String corsAllowedMethods) {
            delegate.setCorsAllowedMethods(corsAllowedMethods);
        }

        @Override
        public String getCorsAllowedMethods() {
            return delegate.getCorsAllowedMethods();
        }

        @Override
        public void setCorsAllowedHeaders(String corsAllowedHeaders) {
            delegate.setCorsAllowedHeaders(corsAllowedHeaders);
        }

        @Override
        public boolean isAlwaysRefreshToken() {
            return delegate.isAlwaysRefreshToken();
        }

        @Override
        public void setAlwaysRefreshToken(boolean alwaysRefreshToken) {
            delegate.setAlwaysRefreshToken(alwaysRefreshToken);
        }

        @Override
        public int getRegisterNodePeriod() {
            return delegate.getRegisterNodePeriod();
        }

        @Override
        public void setRegisterNodePeriod(int registerNodePeriod) {
            delegate.setRegisterNodePeriod(registerNodePeriod);
        }

        @Override
        public void setRegisterNodeAtStartup(boolean registerNodeAtStartup) {
            delegate.setRegisterNodeAtStartup(registerNodeAtStartup);
        }

        @Override
        public boolean isRegisterNodeAtStartup() {
            return delegate.isRegisterNodeAtStartup();
        }

        @Override
        public String getPrincipalAttribute() {
            return delegate.getPrincipalAttribute();
        }

        @Override
        public void setPrincipalAttribute(String principalAttribute) {
            delegate.setPrincipalAttribute(principalAttribute);
        }

        @Override
        public boolean isTurnOffChangeSessionIdOnLogin() {
            return delegate.isTurnOffChangeSessionIdOnLogin();
        }

        @Override
        public void setTurnOffChangeSessionIdOnLogin(boolean turnOffChangeSessionIdOnLogin) {
            delegate.setTurnOffChangeSessionIdOnLogin(turnOffChangeSessionIdOnLogin);
        }
    }


}
