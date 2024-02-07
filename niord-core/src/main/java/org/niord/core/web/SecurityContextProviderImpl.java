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

package org.niord.core.web;

import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.core.SecurityContext;

/**
 * The SecurityContextProviderImpl Class
 *
 * This class implements the SecurityContextProvider and is able to supply the
 * current user based on the request information.
 *
 * @author Nikolaos Vastardis (email: Nikolaos.Vastardis@gla-rad.org)
 */
@RequestScoped
public class SecurityContextProviderImpl implements SecurityContextProvider {

    // The Class Variables
    private SecurityContext securityContext;

    /**
     * Retrieves the authentication security context.
     *
     * @return The current security context
     */
    @Override
    public SecurityContext getSecurityContext() {
        return securityContext;
    }

    /**
     * Updates the authentication security context with the current user
     * information.
     *
     * @param securityContext The current security context
     */
    public void setSecurityContext(SecurityContext securityContext) {
        this.securityContext = securityContext;
    }

}
