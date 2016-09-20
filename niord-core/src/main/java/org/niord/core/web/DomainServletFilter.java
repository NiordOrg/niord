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

import org.apache.commons.lang.StringUtils;
import org.niord.core.domain.DomainResolver;
import org.niord.core.domain.DomainService;
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
 * Resolves the domain from the request and stores it in the current thread
 */
@WebFilter(urlPatterns={"/*"})
public class DomainServletFilter implements Filter {

    @Inject
    Logger log;

    @Inject
    DomainService domainService;

    DomainResolver domainResolver = DomainResolver.newInstance();


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

        String domainId = domainResolver.resolveDomain((HttpServletRequest) req);
        if (StringUtils.isNotBlank(domainId)) {
            domainService.setDomainForCurrentThread(domainId);
        }

        try {
            // Proceed with the request
            chain.doFilter(req, res);
        } finally {
            domainService.removeDomainForCurrentThread();
        }
    }
}
