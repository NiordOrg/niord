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
