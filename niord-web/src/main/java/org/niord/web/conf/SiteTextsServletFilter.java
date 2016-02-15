package org.niord.web.conf;

import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;

/**
 * Loads the site-config.js file and injects relevant system configuration
 */
@WebFilter(urlPatterns={"/app/conf/site-texts.js"})
public class SiteTextsServletFilter extends AbstractTextResourceServletFilter {

    final static int CACHE_SECONDS = 60 * 60 * 2; // 2 hour

    public SiteTextsServletFilter() {
        super(CACHE_SECONDS);
    }

    /**
     * Updates the response with relevant translations
     */
    @Override
    String updateResponse(HttpServletRequest request, String response) {
        return response;
    }
}

