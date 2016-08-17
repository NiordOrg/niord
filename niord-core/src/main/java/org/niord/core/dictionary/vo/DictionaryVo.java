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
