package org.niord.core.user;

import org.jboss.resteasy.spi.HttpRequest;
import org.keycloak.KeycloakPrincipal;
import org.keycloak.KeycloakSecurityContext;
import org.keycloak.representations.AccessToken;
import org.niord.core.service.BaseService;
import org.slf4j.Logger;

import javax.annotation.Resource;
import javax.ejb.SessionContext;
import javax.ejb.Stateless;
import javax.inject.Inject;
import java.security.Principal;
import java.util.Collections;
import java.util.Set;

/**
 * Wraps access to the current user
 */
@Stateless
@SuppressWarnings("unused")
public class UserService extends BaseService {

    @Inject
    private Logger log;

    @Resource
    SessionContext ctx;

    /**
     * Returns the currently authenticated user.
     * If necessary the user is created and updated in the database
     * @return the currently authenticated user
     */
    public User currentUser() {
        Principal principal = ctx.getCallerPrincipal();

        // Handle un-authenticated case
        if (principal == null || !(principal instanceof KeycloakPrincipal)) {
            return null;
        }

        // Get the current Keycloak principal
        KeycloakPrincipal keycloakPrincipal = (KeycloakPrincipal) principal;
        AccessToken token = keycloakPrincipal.getKeycloakSecurityContext().getToken();

        User user = findByUsername(token.getPreferredUsername());

        if (user == null) {
            // New user
            user = new User(token);
            user = saveEntity(user);
            log.info("Created new user " + user);

        } else if (user.userChanged(token)) {
            // User data updated
            user.copyToken(token);
            user = saveEntity(user);
            log.info("Updated user " + user);
        }
        return user;
    }

    /**
     * Looks up the {@code User} with the given username
     *
     * @param username the email
     * @return the user or null
     */
    public User findByUsername(String username) {
        try {
            return em.createNamedQuery("User.findByUsername", User.class)
                    .setParameter("username", username)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Returns the Keycloak clients for which the user has at least one role
     * @param request the servlet request
     * @return the Keycloak clients for which the user has at least one role
     */
    public Set<String> getUserKeycloakClientIds(HttpRequest request) {
        try {
            KeycloakSecurityContext securityContext = (KeycloakSecurityContext) request
                    .getAttribute(KeycloakSecurityContext.class.getName());

            AccessToken accessToken = securityContext.getToken();
            if (accessToken != null) {
                return accessToken.getResourceAccess().keySet();
            }
        } catch (Exception ignored) {
        }
        return Collections.emptySet();
    }

}
