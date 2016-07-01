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
package org.niord.web;

import org.niord.core.user.TicketService;
import org.slf4j.Logger;

import javax.inject.Inject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * If a request contains a "ticket" parameter, attempt to set the current ticket data (domain, user, roles)
 * in a tread local
 */
@WebFilter(urlPatterns={"/rest/*"})
public class TicketServletFilter implements Filter {

    private final static String TICKET_PARAM = "ticket";

    @Inject
    Logger log;

    @Inject
    TicketService ticketService;


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

        boolean ticketResolved = ticketService.resolveTicketForCurrentThread(req.getParameter(TICKET_PARAM));
        if (ticketResolved) {
            HttpServletRequest request = (HttpServletRequest)req;
            log.info("Ticket resolved for request " + request.getRequestURI());
        }

        try {
            // Proceed with the request
            chain.doFilter(req, res);
        } finally {
            if (ticketResolved) {
                ticketService.removeTicketForCurrentThread();
            }
        }
    }
}
