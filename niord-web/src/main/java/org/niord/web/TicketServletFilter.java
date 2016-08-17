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
