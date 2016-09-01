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

package org.niord.web;

import org.niord.core.NiordApp;

import javax.inject.Inject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import java.io.IOException;

/**
 * Will store the host name used for accessing Niord for the duration of the request.
 * <p>
 * If Niord is running behind, say, an Apache SSL proxy, ensure that the original server name
 * is passed on.
 * <p>
 * Example configuration:
 * <pre>
 * &lt;VirtualHost *:443&gt;
 *     ServerName niord.host.name
 *     Include /path/to/ssl.conf
 *
 *     ProxyPass           /robots.txt !
 *     ProxyPass           /  http://localhost:8080/
 *     ProxyPassReverse    /  http://localhost:8080/
 *
 *     RequestHeader set originalScheme "https"
 *     ProxyRequests Off
 *     ProxyPreserveHost On
 *     RequestHeader set X-Forwarded-Proto "https"
 *     RequestHeader set X-Forwarded-Port "443"
 * &lt;/VirtualHost&gt;
 * </pre>
 */
@WebFilter(urlPatterns={"/*"})
public class ServerNameServletFilter implements Filter {

    @Inject
    NiordApp app;


    /** {@inheritDoc} */
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    /** {@inheritDoc} */
    @Override
    public void destroy() {
    }


    /**
     * Main filter method
     * @param req the request
     * @param res the response
     * @param chain the filter chain
     */
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {

        app.registerServerNameForCurrentThread(req);

        try {
            // Proceed with the request
            chain.doFilter(req, res);
        } finally {
            app.removeServerNameForCurrentThread();
        }
    }
}
