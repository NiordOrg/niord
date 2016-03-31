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

import org.niord.core.settings.annotation.Setting;

import javax.ejb.Stateless;
import javax.inject.Inject;

/**
 * Common settings and functionality for the Niord app
 */
@Stateless
public class NiordApp {

    @Inject
    @Setting(value = "baseUri", defaultValue = "http://localhost:8080", description = "The base application server URI")
    String baseUri;


    /**
     * Returns the base URI used to access this application
     * @return the base URI used to access this application
     */
    public String getBaseUri() {
        return baseUri;
    }



}
