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

    // KeycloakUrlResolver urlResolver = KeycloakUrlResolver.newInstance();


    /** {@inheritDoc} */
    @Override
    public KeycloakDeployment resolve(HttpFacade.Request request) {

        // Resolves the domain, i.e. the Keycloak client ID, from the request
        String domainId = domainResolver.resolveDomain(request);

        if (StringUtils.isBlank(domainId)) {
            return noDeployment;
        }

        // Look up, or create, cached Keycloak deployment for the domain ID
        KeycloakDeployment deployment = cache.get(domainId);
        if (deployment == null) {
            // If there are concurrent requests, only instantiate once
            synchronized (cache) {
                deployment = cache.get(domainId);
                if (deployment == null) {
                    deployment = instantiateDeployment(domainId);
                }
            }
        }

        /**
         * Originally, Niord would use a Keycloak overlay in the Niord Wildfly.
         * This caused A LOT of problems in the early versions of Keycloak.
         *
         * In order to fix some of these problems, a custom KeycloakUrlResolver was developed.
         * However, as Keycloak now flags the overlay as "not for production", we have stopped
         * using overlays, and instead run Keycloak as a separate server.
         *
         * Hence, the bug-fixing code of the custom KeycloakUrlResolver is not needed anymore.
         *
        if (deployment.getAuthServerBaseUrl() == null) {
            return deployment;
        }

       return urlResolver.resolveUrls(deployment, request);
         */

        return deployment;
    }


    /** Instantiates and caches a keycloak deployment for the given domain ID */
    private KeycloakDeployment instantiateDeployment(String domainId) {

        try {
            KeycloakIntegrationService keycloak = CdiUtils.getBean(KeycloakIntegrationService.class);

            // Instantiate the Keycloak deployment
            KeycloakDeployment deployment = keycloak.createKeycloakDeploymentForDomain(domainId);

            log.info("Instantiated keycloak deployment for domain " + domainId);

            // Cache the deployment
            cache.put(domainId, deployment);
            return deployment;

        } catch (Exception e) {
            throw new IllegalStateException("Not able to find the file /keycloak.json");
        }
    }

}
