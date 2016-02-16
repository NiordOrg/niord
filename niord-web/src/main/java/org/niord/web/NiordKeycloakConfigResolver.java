package org.niord.web;

import org.apache.commons.lang.StringUtils;
import org.keycloak.adapters.KeycloakConfigResolver;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.keycloak.adapters.spi.HttpFacade;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the keycloak.json to use for a given request
 *
 * Currently, "/keycloak.json" will be loaded from class path.
 */
public class NiordKeycloakConfigResolver implements KeycloakConfigResolver {

    private final Map<String, KeycloakDeployment> cache = new ConcurrentHashMap<>();

    /** {@inheritDoc} */
    @Override
    public KeycloakDeployment resolve(HttpFacade.Request request) {

        String client = resolveClient(request);

        KeycloakDeployment deployment = cache.get(client);
        if (null == deployment) {

            // not found on the simple cache, try to load it from the file system
            String kcFile = StringUtils.isBlank(client) ? "keycloak.json" : client + "-keycloak.json";
            InputStream is = getClass().getResourceAsStream("/" + kcFile);
            if (is == null) {
                throw new IllegalStateException("Not able to find the file /" + kcFile);
            }
            deployment = KeycloakDeploymentBuilder.build(is);
            cache.put(client, deployment);
        }

        return deployment;
    }

    /** Resolves the client ID from the request */
    private String resolveClient(HttpFacade.Request request) {
        // TODO: Implement
        return "";
    }
}
