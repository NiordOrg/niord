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

import io.quarkus.oidc.TenantResolver;
import io.vertx.ext.web.RoutingContext;
import org.niord.core.domain.DomainResolver;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

/**
 * Resolves the KeycloakDeployment to use for a given request.
 * <p>
 * The deployment will be specific to - and cached by - the current domain.
 */
@ApplicationScoped
public class NiordKeycloakConfigResolver implements TenantResolver {

    /**
     *  Instantiate a domain resolver
     */
    DomainResolver domainResolver = DomainResolver.newInstance();

    /** {@inheritDoc} */
    public String resolve(RoutingContext routingContext) {
        // Resolves the domain, i.e. the Keycloak client ID, from the request
        return Optional.of(routingContext)
                .map(RoutingContext::request)
                .map(domainResolver::resolveDomain)
                .orElse(null);
    }

}
