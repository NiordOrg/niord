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
package org.niord.core.domain;

import org.keycloak.adapters.spi.HttpFacade;

import javax.servlet.http.HttpServletRequest;
import io.vertx.core.http.HttpServerRequest;
import java.util.ServiceLoader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Domain resolver - will determine the current domain from the request.
 * <p>
 * The request may either be a Keycloak request or a servlet request
 */
@SuppressWarnings("unused")
public interface DomainResolver {

    /**
     * Returns a new domain resolver
     * @return a new domain resolver
     */
    static DomainResolver newInstance() {
        ServiceLoader<DomainResolver> resolverLoader = ServiceLoader.load(DomainResolver.class);
        return resolverLoader.iterator().next();
    }

    /**
     * Resolves the current domain, i.e. Keycloak Client, from the request
     * @param request the request to resolve the domain from
     * @return the domain or null if un-resolved
     */
    String resolveDomain(HttpFacade.Request request);

    /**
     * Resolves the current domain, i.e. Keycloak Client, from the request
     * @param request the request to resolve the domain from
     * @return the domain or null if un-resolved
     */
    String resolveDomain(HttpServletRequest request);

    /**
     * Resolves the current domain, i.e. Keycloak Client, from the request
     * @param request the request to resolve the domain from
     * @return the domain or null if un-resolved
     */
    String resolveDomain(HttpServerRequest request);

    /**
     * Implementation of the DomainResolver interface, that uses the "NiordDomain" request header
     * to resolve the domain
     */
    class RequestHeaderDomainResolver implements DomainResolver {

        static final String DOMAIN_HEADER = "NiordDomain";

        /** {@inheritDoc} */
        @Override
        public String resolveDomain(HttpFacade.Request request) {
            return request.getHeader(DOMAIN_HEADER);
        }

        /** {@inheritDoc} */
        @Override
        public String resolveDomain(HttpServletRequest request) {
            return request.getHeader(DOMAIN_HEADER);
        }

        @Override
        public String resolveDomain(HttpServerRequest request) {
            return request.getHeader(DOMAIN_HEADER);
        }
    }

    /**
     * Implementation of the DomainResolver interface, that uses the sub-domain of the request to
     * resolve the Keycloak domain
     */
    class RequestSubDomainResolver implements DomainResolver {

        Pattern serverPattern = Pattern.compile("^https?://(?<server>[^/]+)/.*$", Pattern.CASE_INSENSITIVE);
        Pattern subDomainPattern = Pattern.compile("^(?<subdomain>[^\\.]+)\\..*$", Pattern.CASE_INSENSITIVE);

        private String resolveDomain(String uri) {
            Matcher sm  = serverPattern.matcher(uri);
            if (sm.matches()) {
                String server = sm.group("server");
                Matcher sdm = subDomainPattern.matcher(server);
                if (sdm.matches()) {
                    return sdm.group("subdomain");
                }
            }
            return null;
        }


        /** {@inheritDoc} */
        @Override
        public String resolveDomain(HttpFacade.Request request) {
            return resolveDomain(request.getURI());
        }

        /** {@inheritDoc} */
        @Override
        public String resolveDomain(HttpServletRequest request) {
            return resolveDomain(request.getRequestURI());
        }

        /** {@inheritDoc} */
        @Override
        public String resolveDomain(HttpServerRequest request) {
            return resolveDomain(request.uri());
        }
    }
}
