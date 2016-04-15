package org.niord.core.user;

import org.keycloak.KeycloakPrincipal;
import org.keycloak.KeycloakSecurityContext;
import org.keycloak.adapters.RefreshableKeycloakSecurityContext;
import org.keycloak.representations.AccessToken;
import org.niord.core.service.BaseService;
import org.slf4j.Logger;

import javax.annotation.Resource;
import javax.ejb.SessionContext;
import javax.ejb.Stateless;
import javax.inject.Inject;
import java.security.Principal;

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


    /** Returns the current Keycloak principal */
    public KeycloakPrincipal getCalledPrincipal() {
        Principal principal = ctx.getCallerPrincipal();

        // Handle un-authenticated case
        if (principal == null || !(principal instanceof KeycloakPrincipal)) {
            return null;
        }

        return (KeycloakPrincipal) principal;
    }


    /** Returns the Keycloak resource (client) associated with the current user */
    public String getCurrentResourceName() {

        // Get the current Keycloak principal
        KeycloakPrincipal keycloakPrincipal = getCalledPrincipal();
        if (keycloakPrincipal == null) {
            return null;
        }

        // Hmmm, is this really the best way to get the current resource name
        KeycloakSecurityContext ctx = keycloakPrincipal.getKeycloakSecurityContext();
        if (ctx instanceof RefreshableKeycloakSecurityContext) {
            RefreshableKeycloakSecurityContext rctx = (RefreshableKeycloakSecurityContext)ctx;
            return rctx.getDeployment().getResourceName();
        }

        return null;
    }

    /**
     * Returns the currently authenticated user.
     * If necessary the user is created and updated in the database
     * @return the currently authenticated user
     */
    public User currentUser() {

        // Get the current Keycloak principal
        KeycloakPrincipal keycloakPrincipal = getCalledPrincipal();
        if (keycloakPrincipal == null) {
            return null;
        }

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
}
