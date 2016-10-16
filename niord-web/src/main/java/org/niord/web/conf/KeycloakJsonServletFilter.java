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

package org.niord.web.conf;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.niord.core.keycloak.KeycloakIntegrationService;
import org.slf4j.Logger;

import javax.inject.Inject;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * Loads the keycloak.json file and updates it with one constructed by the KeycloakIntegrationService
 */
@WebFilter(urlPatterns={"/conf/keycloak.json"})
public class KeycloakJsonServletFilter extends AbstractTextResourceServletFilter {

    final static int CACHE_SECONDS = 0; // Do not cache

    Map<String, String> keycloakDeployment;

    @Inject
    Logger log;

    @Inject
    KeycloakIntegrationService keycloakIntegrationService;

    /** Constructor **/
    public KeycloakJsonServletFilter() {
        super(CACHE_SECONDS);
    }


    /**
     * Updates the response with system properties
     */
    @Override
    String updateResponse(HttpServletRequest request, String response) {

        try {
            if (keycloakDeployment == null) {
                // If there are concurrent requests, only instantiate once
                synchronized (this) {
                    if (keycloakDeployment == null) {
                        keycloakDeployment = keycloakIntegrationService.createKeycloakDeploymentForWebApp();
                        log.info("Instantiated keycloak deployment for web application");
                    }
                }
            }

            return new ObjectMapper()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(keycloakDeployment);
        } catch (Exception e) {
            log.error("Failed generating proper Keycloak Deployment Configuration", e);
            return response;
        }
    }
}
