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

import org.apache.commons.lang.StringUtils;
import org.keycloak.adapters.KeycloakConfigResolver;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.spi.HttpFacade;
import org.niord.core.domain.DomainResolver;
import org.niord.core.util.CdiUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the KeycloakDeployment to use for a given request.
 * <p>
 * The deployment will be specific to - and cached by - the current domain.
 */
public class NiordKeycloakConfigResolver implements KeycloakConfigResolver {

    final Map<String, KeycloakDeployment> cache = new ConcurrentHashMap<>();

    final Logger log = LoggerFactory.getLogger(NiordKeycloakConfigResolver.class);

    KeycloakDeployment noDeployment = new KeycloakDeployment();

    DomainResolver domainResolver = DomainResolver.newInstance();

    KeycloakUrlResolver urlResolver = KeycloakUrlResolver.newInstance();


    /** {@inheritDoc} */
    @Override
    public KeycloakDeployment resolve(HttpFacade.Request request) {

        // Resolves the domain, i.e. the Keycloak client ID, from the request
        String clientId = domainResolver.resolveDomain(request);

        if (StringUtils.isBlank(clientId)) {
            return noDeployment;
        }

        // Look up, or create, cached Keycloak deployment for the client ID
        KeycloakDeployment deployment = cache.get(clientId);
        if (deployment == null) {
            // If there are concurrent requests, only instantiate once
            synchronized (cache) {
                deployment = cache.get(clientId);
                if (deployment == null) {
                    deployment = instantiateDeployment(clientId);
                }
            }
        }

        if (deployment.getAuthServerBaseUrl() == null) {
            return deployment;
        }

       return urlResolver.resolveUrls(deployment, request);
    }


    /** Instantiates and caches a keycloak deployment for the given client ID */
    private KeycloakDeployment instantiateDeployment(String clientId) {

        try {
            KeycloakIntegrationService keycloak = CdiUtils.getBean(KeycloakIntegrationService.class);

            // Instantiate the Keycloak deployment
            KeycloakDeployment deployment = keycloak.createKeycloakDeploymentForDomain(clientId);

            log.info("Instantiated keycloak deployment for client " + clientId);

            // Cache the deployment
            cache.put(clientId, deployment);
            return deployment;

        } catch (Exception e) {
            throw new IllegalStateException("Not able to find the file /keycloak.json");
        }
    }

}
