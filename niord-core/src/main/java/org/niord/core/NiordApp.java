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
package org.niord.core;

import org.niord.core.settings.Setting;
import org.niord.core.settings.SettingsService;

import javax.ejb.Stateless;
import javax.inject.Inject;

/**
 * Common settings and functionality for the Niord app
 */
@Stateless
public class NiordApp {

    private static final Setting BASE_URI =
            new Setting("baseUri", "http://localhost:8080")
                    .description("The base application server URI")
                    .editable(true);

    private static final Setting COUNTRY =
            new Setting("country", "DK")
                    .description("The country")
                    .editable(true);

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

}
