package org.niord.web;

import org.jboss.security.SimplePrincipal;
import org.jboss.security.auth.spi.UsersRolesLoginModule;

import javax.security.auth.login.LoginException;
import java.security.Principal;

/**
 * Created by carolus on 23/11/15.
 */
public class TestLoginModule extends UsersRolesLoginModule {
    private CustomPrincipal principal;

    @Override
    public boolean login() throws LoginException {
        System.out.println("LOGIN **********");
        boolean login = super.login();
        if (login) {
            principal = new CustomPrincipal(getUsername(), "An user description!");
        }
        return login;
    }

    @Override
    protected Principal getIdentity() {
        return principal != null ? principal : super.getIdentity();
    }

    public static class CustomPrincipal extends SimplePrincipal {
        private String description;

        public CustomPrincipal(String name, String description) {
            super(name);
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}
