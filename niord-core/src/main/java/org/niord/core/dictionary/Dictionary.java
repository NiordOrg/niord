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
package org.niord.core.dictionary;

import org.niord.core.dictionary.vo.DictionaryEntryVo;
import org.niord.core.dictionary.vo.DictionaryVo;
import org.niord.core.model.BaseEntity;
import org.niord.model.DataFilter;

import javax.persistence.Cacheable;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.MapKey;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
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
    /*************************/

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
