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
package org.niord.core.dictionary.vo;

import org.niord.model.IJsonSerializable;

import java.util.HashMap;
import java.util.Map;

/**
 * Models a named dictionary
 */
public class DictionaryVo implements IJsonSerializable {

    String name;
    Map<String, DictionaryEntryVo> entries = new HashMap<>();

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, DictionaryEntryVo> getEntries() {
        return entries;
    }

    public void setEntries(Map<String, DictionaryEntryVo> entries) {
        this.entries = entries;
    }
}
