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
package org.niord.core;

import org.apache.commons.lang.StringUtils;
import org.niord.core.settings.Setting;
import org.niord.core.settings.SettingsService;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.servlet.ServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Common settings and functionality for the Niord app
 */
@Stateless
@SuppressWarnings("unused")
public class NiordApp {

    private final static ThreadLocal<String> THREAD_LOCAL_SERVER_NAME = new ThreadLocal<>();

    // The possible execution modes of Niord
    public enum ExecutionMode { DEVELOPMENT, TEST, PRODUCTION }

    private static final Setting BASE_URI =
            new Setting("baseUri", "http://localhost:8080")
                    .description("The base application server URI")
                    .editable(true);

    private static final Setting COUNTRY =
            new Setting("country", "DK")
                    .description("The country")
                    .editable(true);

    private static final Setting LANGUAGES = new Setting("modelLanguages");

    private static final Setting EXECUTION_MODE =
            new Setting("mode", "development")
                    .description("The Niord execution mode, either 'development', 'test' or 'production'.")
                    .editable(true)
                    .web(true);

    private static final Setting ANALYTICS_TRACKING_ID =
            new Setting("analyticsTrackingId", "")
                    .description("The google analytics tracking ID.")
                    .editable(true)
                    .web(true);

    @Inject
    SettingsService settingsService;


    /** Returns the build version **/
    public String getBuildVersion() {
        // The "version.properties" file is updated by maven with the build timestamp.
        try (InputStream in = getClass().getResourceAsStream("/version.properties")) {
            Properties versionProperties = new Properties();
            versionProperties.load(in);
            return versionProperties.getProperty("niord.version");
        } catch (IOException ignored) {
        }
        return "undefined";
    }


    /**
     * Returns the base URI used to access this application
     * @return the base URI used to access this application
     */
    public String getBaseUri() {
        return settingsService.getString(BASE_URI);
    }


    /**
     * Returns the country
     * @return the country
     */
    public String getCountry() {
        return settingsService.getString(COUNTRY);
    }


    /**
     * Returns the list of languages supported by the MSI-NM application
     * @return the list of languages supported by the MSI-NM application
     */
    public String[] getLanguages() {
        @SuppressWarnings("unchecked")
        List<String> languages = (List<String>)settingsService.get(LANGUAGES);
        return (languages == null || languages.size() == 0)
                ? new String[]{"en"}
                : languages.toArray(new String[languages.size()]);
    }


    /**
     * Returns the list of languages supported by the MSI-NM application with the given language first
     * @return the list of languages supported by the MSI-NM application the given language first
     */
    public String[] getLanguages(String lang) {
        lang = getLanguage(lang);
        String[] languages = getLanguages();
        for (int x = 1; x < languages.length; x++) {
            if (languages[x].equals(lang)) {
                String temp = languages[0];
                languages[0] = languages[x];
                languages[x] = temp;
            }
        }
        return languages;
    }


    /**
     * Returns the default language
     * @return the default language
     */
    public String getDefaultLanguage() {
        return getLanguages()[0];
    }


    /**
     * Ensures that the given language is a supported language and
     * returns the default language if not
     * @param lang the language to check
     * @return the language if supported, otherwise the default language
     */
    public String getLanguage(String lang) {
        for (String l : getLanguages()) {
            if (l.equalsIgnoreCase(lang)) {
                return l;
            }
        }
        return getDefaultLanguage();
    }


    /**
     * Ensures that the given language is a supported language and
     * returns the default locale if not
     * @param lang the language to check
     * @return the associated locale if supported, otherwise the default locale
     */
    public Locale getLocale(String lang) {
        return new Locale(getLanguage(lang));
    }


    /**
     * Returns the execution mode of the Niord system
     * @return the execution mode of the Niord system
     */
    public ExecutionMode getExecutionMode() {
        String mode = settingsService.getString(EXECUTION_MODE);
        try {
            return ExecutionMode.valueOf(mode.toUpperCase());
        } catch (Exception ignored) {
        }
        // Default to least-harm mode...
        return ExecutionMode.DEVELOPMENT;
    }


    /**
     * Returns the Google Analytics tracking ID, or null if undefined
     * @return the Google Analytics tracking ID, or null if undefined
     */
    public String getAnalyticsTrackingId() {
        String tracingId = settingsService.getString(ANALYTICS_TRACKING_ID);
        return StringUtils.isNotBlank(tracingId) ? tracingId : null;
    }


    /**
     * Registers the server name associated with the current thread (i.e. servlet request)
     * @param req the servlet request
     */
    public void registerServerNameForCurrentThread(ServletRequest req) {
        String scheme = StringUtils.defaultIfBlank(req.getScheme(), "http");
        String serverName = StringUtils.defaultIfBlank(req.getServerName(), "localhost");
        String port = (scheme.equalsIgnoreCase("https"))
                        ? (req.getServerPort() == 443 ? "" : ":" + req.getServerPort())
                        : (req.getServerPort() == 80 ? "" : ":" + req.getServerPort());
        if (StringUtils.isNotBlank(serverName)) {
            THREAD_LOCAL_SERVER_NAME.set(scheme + "://" + serverName + port);
        }
    }


    /**
     * Removes the server name associated with the current thread (i.e. servlet request)
     */
    public void removeServerNameForCurrentThread() {
        THREAD_LOCAL_SERVER_NAME.remove();
    }


    /**
     * Returns the server name associated with the current thread (i.e. servlet request)
     * @return the server name associated with the current thread
     */
    public String getServerNameForCurrentThread() {
        return THREAD_LOCAL_SERVER_NAME.get();
    }


    /**
     * Returns the server name associated with the current thread (i.e. servlet request)
     * or the base URI if the server name is undefined.
     * @return the server name associated with the current thread or the base URI if the server name is undefined
     */
    public String getServerNameForCurrentThreadOrBaseUri() {
        return StringUtils.defaultIfBlank(getServerNameForCurrentThread(), getBaseUri());
    }
}
