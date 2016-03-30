package org.niord.web.conf;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.niord.core.settings.SettingsService;
import org.slf4j.Logger;

import javax.inject.Inject;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;

/**
 * Loads the site-config.js file and injects relevant system configuration
 */
@WebFilter(urlPatterns={"/app/conf/site-config.js"})
public class SiteConfigServletFilter extends AbstractTextResourceServletFilter {

    final static int CACHE_SECONDS = 60 * 60 * 2; // 2 hour

    @Inject
    Logger log;

    @Inject
    SettingsService settingsService;

    /** Constructor **/
    public SiteConfigServletFilter() {
        super(CACHE_SECONDS);
    }


    /**
     * Returns the web settings as a javascript snippet sets the settings as $rootScope variables.
     */
    private String getWebSettings() {

        ObjectMapper mapper = new ObjectMapper();
        StringBuilder str = new StringBuilder("\n");

        settingsService.getAllForWeb().forEach(s -> {

            // Add setting description
            str.append("    /** ")
                    .append(s.getDescription())
                    .append(" **/\n");

            try {
                // Add setting value as a $rootScope variable
                str.append("    $rootScope.")
                        .append(s.getKey())
                        .append(" = ")
                        .append(mapper.writeValueAsString(s.getValue()))
                        .append(";\n\n");

            } catch (Exception e) {
                log.error("Error writing site-config property " + s.getKey(), e);
            }
        });
        return str.toString();
    }


    /**
     * Updates the response with system properties
     */
    @Override
    String updateResponse(HttpServletRequest request, String response) {

        response = response.replace("/** SETTINGS **/", getWebSettings());
        return response;
    }
}
