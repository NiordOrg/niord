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

import org.niord.core.settings.Setting;
import org.niord.core.settings.SettingsService;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.List;
import java.util.Locale;

/**
 * Common settings and functionality for the Niord app
 */
@Stateless
@SuppressWarnings("unused")
public class NiordApp {

    private static final Setting BASE_URI =
            new Setting("baseUri", "http://localhost:8080")
                    .description("The base application server URI")
                    .editable(true);

    private static final Setting COUNTRY =
            new Setting("country", "DK")
                    .description("The country")
                    .editable(true);

    private static final Setting LANGUAGES = new Setting("modelLanguages");

    @Inject
    SettingsService settingsService;

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


}
