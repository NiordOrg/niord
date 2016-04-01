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
package org.niord.core;

import org.junit.Test;
import org.keycloak.adapters.spi.HttpFacade;
import org.niord.core.domain.DomainResolver;

import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

/**
 * Test the domain resolvers
 */
public class DomainResolverTest {

    @Test
    public void testDomainResolver() {

        // Test loading of default domain resolver
        DomainResolver resolver = DomainResolver.newInstance();
        assertNotNull(resolver);
        assertTrue(resolver instanceof DomainResolver.RequestHeaderDomainResolver);

        assertEquals(resolver.resolveDomain(mockRequest(null, "ged")), "ged");

        resolver = new DomainResolver.RequestSubDomainResolver();
        assertEquals(resolver.resolveDomain(mockRequest("http://ged.niord.org/hello/mum",null)), "ged");
        assertEquals(resolver.resolveDomain(mockRequest("https://ged.niord.org/hello/mum",null)), "ged");
        assertNull(resolver.resolveDomain(mockRequest("http://localhost/hello/mum",null)));

    }

    private HttpFacade.Request mockRequest(String uri, String header) {
        return  (HttpFacade.Request) Proxy.newProxyInstance(
                HttpFacade.Request.class.getClassLoader(),
                new Class[]{HttpFacade.Request.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getURI":
                            return uri;
                        case "getHeader":
                            return header;
                        default:
                            return null;
                    }
                });
    }

}
