package org.niord.web.conf;

import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;

/**
 * Loads the site-config.js file and injects relevant system configuration
 */
@WebFilter(urlPatterns={"/app/conf/site-config.js"})
public class SiteConfigServletFilter extends AbstractTextResourceServletFilter {

    final static int CACHE_SECONDS = 60 * 60 * 2; // 2 hour

    /** Constructor **/
    public SiteConfigServletFilter() {
        super(CACHE_SECONDS);
    }

    /**
     * Updates the response with system properties
     */
    @Override
    String updateResponse(HttpServletRequest request, String response) {
        return response;
    }
}

