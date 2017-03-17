/*
 * Copyright 2017 Danish Maritime Authority.
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

import java.util.ArrayList;
import java.util.List;

/**
 * Models a named dictionary.
 *
 * As opposed to the DictionaryVo class, this version models the entries as a list, which provides
 * for a better export format
 */
@SuppressWarnings("unused")
public class ExportedDictionaryVo implements IJsonSerializable {

    String name;
    List<DictionaryEntryVo> entries = new ArrayList<>();


    /** Constructor **/
    public ExportedDictionaryVo() {
    }

    /** Constructor **/
    public ExportedDictionaryVo(DictionaryVo dictionary) {
        this.name = dictionary.getName();
        this.entries.addAll(dictionary.getEntries().values());
    }


    /** Converts this dictionary to a DictionaryVo **/
    public DictionaryVo toDictionaryVo() {
        DictionaryVo dictionary = new DictionaryVo();
        dictionary.setName(name);
        entries.forEach(e -> dictionary.getEntries().put(e.getKey(), e));
        return dictionary;
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

    public List<DictionaryEntryVo> getEntries() {
        return entries;
    }

    public void setEntries(List<DictionaryEntryVo> entries) {
        this.entries = entries;
    }
}
