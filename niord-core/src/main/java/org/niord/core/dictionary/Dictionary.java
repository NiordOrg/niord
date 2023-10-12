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
package org.niord.core.dictionary;

import org.niord.core.dictionary.vo.DictionaryEntryVo;
import org.niord.core.dictionary.vo.DictionaryVo;
import org.niord.core.model.BaseEntity;
import org.niord.model.DataFilter;

import jakarta.persistence.Cacheable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.MapKey;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Models a named dictionary
 */
@Entity
@Cacheable
@NamedQueries({
        @NamedQuery(name  = "Dictionary.findByName",
                query = "select distinct d from Dictionary d where d.name = :name")
})
@SuppressWarnings("unused")
public class Dictionary extends BaseEntity<Integer> {

    @Column(nullable = false, unique = true)
    String name;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "dictionary", orphanRemoval = true)
    @MapKey(name = "key")
    Map<String, DictionaryEntry> entries = new HashMap<>();


    /** Constructor */
    public Dictionary() {
    }


    /** Constructor */
    public Dictionary(DictionaryVo dict) {
        this.name = dict.getName();
        dict.getEntries().values().forEach(entry ->
                createEntry(entry.getKey()).updateDictionaryEntry(new DictionaryEntry(entry)));
    }


    /** Converts this dictionary to a value object */
    public DictionaryVo toVo(DataFilter filter) {

        DataFilter compFilter = filter.forComponent(Dictionary.class);

        DictionaryVo dict = new DictionaryVo();
        dict.setName(name);
        dict.setEntries(entries.values().stream()
                .map(de -> de.toVo(compFilter))
                .collect(Collectors.toMap(DictionaryEntryVo::getKey, Function.identity()))
        );

        return dict;
    }

    /** Creates a new dictionary entry */
    public DictionaryEntry createEntry(String key) {
        DictionaryEntry entry = new DictionaryEntry();
        entry.setKey(key);
        entry.setDictionary(this);
        getEntries().put(key, entry);
        return entry;
    }

    /*************************/
    /** Getters and Setters **/
    /***/

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, DictionaryEntry> getEntries() {
        return entries;
    }

    public void setEntries(Map<String, DictionaryEntry> entries) {
        this.entries = entries;
    }
}
