/*
 * Copyright (c) 2008 Kasper Nielsen.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.niord.core.keycloak;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import io.quarkus.oidc.runtime.OidcUtils;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.vertx.http.runtime.security.HttpSecurityUtils;
import io.smallrye.mutiny.Uni;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;

/**
 *
 */
@ApplicationScoped
public class NiordDomainSecurityIdentityAugmentor implements SecurityIdentityAugmentor {

    /** {@inheritDoc} */
    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context, Map<String, Object> attributes) {
        System.out.println("Being Augmented");
        RoutingContext routingContext = HttpSecurityUtils.getRoutingContextAttribute(attributes);
        if (routingContext != null) {
            MultiMap headers = routingContext.request().headers();
            String domain = headers.get("NiordDomain");
            if (domain != null) {
//                System.out.println("Looking in domain " + domain);
//                System.out.println("Headers " + headers.names());
                String authorization = headers.get("Authorization");
//                System.out.println(authorization);
                List<String> roles = getRoles(authorization, domain);
                identity = QuarkusSecurityIdentity.builder(identity).addRoles(new HashSet<>(roles)).build();
            }
        } else {
            System.out.println("No RoutingContext");
        }
        return augment(identity, context);
    }

    /** {@inheritDoc} */
    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext arg1) {
        return Uni.createFrom().item(identity);
    }

    private static List<String> getRoles(String authorization, String domain) {
        JsonObject js = OidcUtils.decodeJwtContent(authorization);
        JsonObject ra = js.getJsonObject("resource_access");
        if (ra != null) {
            JsonObject dom = ra.getJsonObject(domain);
            if (dom != null) {
                JsonArray a = dom.getJsonArray("roles");
                if (a != null) {
                    return a.stream().map(Object::toString).toList();
                }
            }
        }
        return List.of();
    }

}
