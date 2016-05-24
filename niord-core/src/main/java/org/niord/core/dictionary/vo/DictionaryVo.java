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
import java.util.Properties;

/**
 * Models a named dictionary
 */
public class DictionaryVo implements IJsonSerializable {

    String name;
    Map<String, DictionaryEntryVo> entries = new HashMap<>();


    /**
     * Creates a Properties object from the dictionary, containing the values for the given language
     * where defined, and otherwise, any value.
     * @param lang the language
     * @return the Properties object for the dictionary
     */
    public Properties toProperties(String lang) {
        Properties props = new Properties();
        getEntries().values().stream()
                .forEach(entry -> {
                    DictionaryEntryDescVo desc = entry.getDesc(lang);
                    // If the value descriptor does not exist for the given language - select the default instead
                    if (desc == null && !entry.getDescs().isEmpty()) {
                        desc = entry.getDescs().get(0);
                    }
                    if (desc != null && desc.getValue() != null) {
                        props.setProperty(entry.getKey(), desc.getValue());
                    }
                });
        return props;
    }


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
