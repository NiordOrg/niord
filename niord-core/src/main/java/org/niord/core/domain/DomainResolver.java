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
package org.niord.core.domain;

import org.keycloak.adapters.spi.HttpFacade;

import java.util.ServiceLoader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Domain resolver - will determine the current domain from the request.
 */
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
    }

    /**
     * Implementation of the DomainResolver interface, that uses the sub-domain of the request to
     * resolve the Keycloak domain
     */
    class RequestSubDomainResolver implements DomainResolver {

        Pattern serverPattern = Pattern.compile("^https?://(?<server>[^/]+)/.*$", Pattern.CASE_INSENSITIVE);
        Pattern subDomainPattern = Pattern.compile("^(?<subdomain>[^\\.]+)\\..*$", Pattern.CASE_INSENSITIVE);

        /** {@inheritDoc} */
        @Override
        public String resolveDomain(HttpFacade.Request request) {
            Matcher sm  = serverPattern.matcher(request.getURI());
            if (sm.matches()) {
                String server = sm.group("server");
                Matcher sdm = subDomainPattern.matcher(server);
                if (sdm.matches()) {
                    return sdm.group("subdomain");
                }
            }
            return null;
        }
    }
}
