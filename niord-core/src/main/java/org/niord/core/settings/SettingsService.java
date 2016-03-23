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
package org.niord.core.settings;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Dummy implementation for now
 */
public class SettingsService {

    static Map<String, Object> cache = new HashMap<>();

    public Date getDate(String key) {
        return (Date)cache.get(key);
    }

    public void setDate(String key, Date date) {
        cache.put(key, date);
    }
}
