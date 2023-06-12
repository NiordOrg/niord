package org.niord.core.web;

import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.Objects;

/**
 * The Security Filter Class.
 *
 * This class implements the request filter that assign the currently
 * authenticated used into the authentication context.
 *
 * @author Nikolaos Vastardis (email: Nikolaos.Vastardis@gla-rad.org)
 */
@Provider
@PreMatching
public class SecurityFilter implements ContainerRequestFilter {

    @Inject
    SecurityContextProviderImpl securityContextProvider;

    /**
     * Inject the security context of the request into then provider for all
     * the components to be able to access.
     *
     * @param requestContext    The request context
     * @throws IOException
     */
    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if(Objects.nonNull(requestContext.getSecurityContext())) {
            securityContextProvider.setSecurityContext(requestContext.getSecurityContext());
        }
    }
}
