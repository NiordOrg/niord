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
@WebFilter(urlPatterns={"/app/conf/keycloak.json"})
public class KeycloakJsonServletFilter extends AbstractTextResourceServletFilter {

    final static int CACHE_SECONDS = 10 * 60; // 10 minutes

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
            Map<String, String> cfg = keycloakIntegrationService.createKeycloakDeploymentForWebApp();

            return new ObjectMapper()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(cfg);
        } catch (Exception e) {
            log.error("Failed generating proper Keycloak Deployment Configuration", e);
            return response;
        }
    }
}
