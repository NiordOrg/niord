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
import org.niord.core.model.BaseEntity;
import org.niord.model.DataFilter;
import org.niord.model.ILocalizable;

import javax.persistence.Cacheable;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Models a named dictionary entry
 */
@Entity
@Cacheable
@NamedQueries({
        @NamedQuery(name  = "DictionaryEntry.loadWithDescs",
                query = "select distinct de from DictionaryEntry de left join fetch de.descs")
})
@SuppressWarnings("unused")
public class DictionaryEntry extends BaseEntity<Integer> implements ILocalizable<DictionaryEntryDesc> {

    @ManyToOne
    @NotNull
    Dictionary dictionary;

    @Column(name = "dict_key", nullable = false)
    String key;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "entity", orphanRemoval = true)
    List<DictionaryEntryDesc> descs = new ArrayList<>();


    /** Constructor */
    public DictionaryEntry() {
    }

    /** Constructor */
    public DictionaryEntry(DictionaryEntryVo entry) {
        this(entry, DataFilter.get());
    }


    /** Constructor */
    public DictionaryEntry(DictionaryEntryVo entry, DataFilter filter) {
        updateDictionaryEntry(entry, filter);
    }


    /** Updates this entry from the given entry */
    public void updateDictionaryEntry(DictionaryEntryVo entry, DataFilter filter) {

        DataFilter compFilter = filter.forComponent(DictionaryEntry.class);

        this.key = entry.getKey();
        if (entry.getDescs() != null) {
            entry.getDescs()
                    .forEach(desc -> createDesc(desc.getLang()).setValue(desc.getValue()));
        }
    }


    /** Converts this entity to a value object */
    public DictionaryEntryVo toVo(DataFilter filter) {

        DataFilter compFilter = filter.forComponent(DictionaryEntry.class);

        DictionaryEntryVo entry = new DictionaryEntryVo();
        entry.setKey(key);
        if (!descs.isEmpty()) {
            entry.setDescs(getDescs(filter).stream()
                    .map(DictionaryEntryDesc::toVo)
                    .collect(Collectors.toList()));
        }
        return entry;
    }


    /** {@inheritDoc} */
    @Override
    public DictionaryEntryDesc createDesc(String lang) {
        DictionaryEntryDesc desc = new DictionaryEntryDesc();
        desc.setLang(lang);
        desc.setEntity(this);
        getDescs().add(desc);
        return desc;
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public Dictionary getDictionary() {
        return dictionary;
    }

    public void setDictionary(Dictionary dictionary) {
        this.dictionary = dictionary;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    @Override
    public List<DictionaryEntryDesc> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<DictionaryEntryDesc> descs) {
        this.descs = descs;
    }


}
