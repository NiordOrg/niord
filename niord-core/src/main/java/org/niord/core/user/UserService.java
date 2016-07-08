package org.niord.core.user;

import org.apache.commons.lang.StringUtils;
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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    @Inject
    TicketService ticketService;


    /** Returns the current Keycloak principal */
    public KeycloakPrincipal getCallerPrincipal() {
        Principal principal = ctx.getCallerPrincipal();

        // Handle un-authenticated case
        if (principal == null || !(principal instanceof KeycloakPrincipal)) {
            return null;
        }

        return (KeycloakPrincipal) principal;
    }


    /**
     * Returns all the resource names (domain client ID) where the current user has the given role
     * @param role the role to check for
     * @return all the resource names (domain client ID) where the current user has the given role
     */
    public Set<String> getResourcesNamesWithRoles(String role) {
        KeycloakPrincipal keycloakPrincipal = getCallerPrincipal();
        if (keycloakPrincipal != null) {
            KeycloakSecurityContext ctx = keycloakPrincipal.getKeycloakSecurityContext();
            AccessToken accessToken = ctx.getToken();
            Map<String, AccessToken.Access> accessMap = accessToken.getResourceAccess();
            return accessMap.entrySet().stream()
                    .filter(kv -> kv.getValue().isUserInRole(role))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
        }
        return new HashSet<>();
    }


    /** Returns the Keycloak resource (client) associated with the current user */
    public String getCurrentResourceName() {

        // Get the current Keycloak principal
        KeycloakPrincipal keycloakPrincipal = getCallerPrincipal();
        if (keycloakPrincipal != null) {

            // Hmmm, is this really the best way to get the current resource name
            KeycloakSecurityContext ctx = keycloakPrincipal.getKeycloakSecurityContext();
            if (ctx instanceof RefreshableKeycloakSecurityContext) {
                RefreshableKeycloakSecurityContext rctx = (RefreshableKeycloakSecurityContext)ctx;
                return rctx.getDeployment().getResourceName();
            }
        }

        // Check if the ticket service has resolved a ticket for the current thread
        TicketService.TicketData ticketData = ticketService.getTicketDataForCurrentThread();
        if (ticketData != null) {
            return ticketData.getDomain();
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
        KeycloakPrincipal keycloakPrincipal = getCallerPrincipal();
        if (keycloakPrincipal == null) {

            // Check if the ticket service has resolved a ticket for the current thread
            TicketService.TicketData ticketData = ticketService.getTicketDataForCurrentThread();
            if (ticketData != null && StringUtils.isNotBlank(ticketData.getUser())) {
                return findByUsername(ticketData.getUser());
            } else {
                return null;
            }
        }

        @SuppressWarnings("all")
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
     * Test if the caller has a given security role.
     *
     * @param role The name of the security role.
     *
     * @return True if the caller has the specified role.
     */
    public boolean isCallerInRole(String role) {
        return  ctx.isCallerInRole(role) ||
                ticketService.validateRolesForCurrentThread(role);
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
