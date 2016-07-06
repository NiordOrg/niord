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
package org.niord.core.repo;

import org.jboss.resteasy.spi.UnhandledException;
import org.slf4j.Logger;

import javax.inject.Inject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * Some uses of the repository, e.g. for serving videos displayed in message lists in Safari, will generate
 * a lot of GET's for the same file, but seemingly, the client closes the connection before the file is streamed.
 * This filter will catch the resulting "Broken piper" IOException's to avoid them filling up in the log.
 * <p>
 * NB: The usual method would be to defined a @Provider-annotated ExceptionMapper class for IOException, but
 * we do not really want to handle all IOException silently.
 */
public class RepositoryServletFilter implements Filter {

    @Inject
    Logger log;

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
        try {
            // Proceed with the request
            chain.doFilter(req, res);
        } catch (UnhandledException ex) {
            if (ex.getCause() instanceof IOException && "Broken pipe".equals(ex.getCause().getMessage())) {
                log.trace("Received Broken pipe IOException: " + ((HttpServletRequest)req).getRequestURI());
                // Do not log this error
                return;
            }
            throw ex;
        }
    }
}
