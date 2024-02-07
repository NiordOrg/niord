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

package org.niord.web.conf;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.niord.core.settings.SettingsService;
import org.niord.core.domain.vo.DomainVo;
import org.niord.web.DomainRestService;
import org.slf4j.Logger;

import jakarta.inject.Inject;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * Loads the site-config.js file and injects relevant system configuration and domains
 */
@WebFilter(urlPatterns={"/conf/site-config.js"})
public class SiteConfigServletFilter extends AbstractTextResourceServletFilter {

    final static int CACHE_SECONDS = 0; // Do not cache

    final static String SETTINGS_START  = "/** SETTINGS START **/";
    final static String SETTINGS_END    = "/** SETTINGS END **/";

    @Inject
    Logger log;

    @Inject
    SettingsService settingsService;

    @Inject
    DomainRestService domainRestService;

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
                        .append(escapeKey(s.getKey()))
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
     * Returns all domains formatted sa JSON.
     */
    private String getDomains() {
        List<DomainVo> domains = domainRestService.getAllDomains(null, false, false);

        StringBuilder js = new StringBuilder()
                .append("    $rootScope.domains = ");
        try {
            ObjectMapper mapper = new ObjectMapper();
            js.append(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(domains));
        } catch (JsonProcessingException e) {
            js.append("[]");
        }

        js.append(";\n\n");
        return js.toString();

    }

    /** Escape naughty characters in the key */
    private String escapeKey(String key) {
        return key.replace('.', '_').replace(' ', '_');
    }


    /**
     * Updates the response with system properties
     */
    @Override
    String updateResponse(HttpServletRequest request, String response) {

        int startIndex = response.indexOf(SETTINGS_START);
        int endIndex = response.indexOf(SETTINGS_END);

        if (startIndex != -1 && endIndex != -1) {
            endIndex += SETTINGS_END.length();
            return response.substring(0, startIndex)
                    + getWebSettings()
                    + getDomains()
                    + response.substring(endIndex);
        }

        return response;
    }
}
