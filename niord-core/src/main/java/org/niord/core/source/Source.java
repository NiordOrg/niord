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

package org.niord.core.source;

import org.niord.core.model.BaseEntity;
import org.niord.core.source.vo.SourceDescVo;
import org.niord.core.source.vo.SourceVo;
import org.niord.model.DataFilter;
import org.niord.model.ILocalizable;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Defines a source that may be associated with a message.
 */
@Entity
@NamedQueries({
        @NamedQuery(name  = "Source.searchSources",
                query = "select distinct c from Source c join c.descs d where c.active in (:active) and "
                        + " d.lang = :lang and (lower(d.name) like :term or lower(d.abbreviation) like :term)"),
        @NamedQuery(name  = "Source.findByIds",
                query = "select distinct c from Source c where c.id in (:ids)"),
        @NamedQuery(name  = "Source.findByName",
                query = "select distinct c from Source c join c.descs d where "
                        + " d.lang = :lang and lower(d.name) = :name")
})
@SuppressWarnings("unused")
public class Source extends BaseEntity<Integer> implements ILocalizable<SourceDesc> {

    /** If the source is currently active or not **/
    boolean active = true;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "entity", orphanRemoval = true)
    List<SourceDesc> descs = new ArrayList<>();


    /** Constructor */
    public Source() {
    }


    /** Constructor */
    public Source(SourceVo source) {
        this.id = source.getId();
        this.active = source.isActive();
        if (source.getDescs() != null) {
            source.getDescs().stream()
                    .filter(SourceDescVo::descDefined)
                    .forEach(desc -> addDesc(new SourceDesc(desc)));
        }
    }


    /** Updates this source from another source */
    public void updateSource(Source source) {
        this.active = source.isActive();
        copyDescsAndRemoveBlanks(source.getDescs());
    }


    /** Converts this entity to a value object */
    public SourceVo toVo(DataFilter dataFilter) {
        SourceVo source = new SourceVo();
        source.setId(id);
        source.setActive(active);

        if (!descs.isEmpty()) {
            source.setDescs(getDescs(dataFilter).stream()
                    .map(SourceDesc::toVo)
                    .collect(Collectors.toList()));
        }
        return source;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public SourceDesc createDesc(String lang) {
        SourceDesc desc = new SourceDesc();
        desc.setLang(lang);
        desc.setEntity(this);
        getDescs().add(desc);
        return desc;
    }


    /** Adds a description entity to this entity */
    public void addDesc(SourceDesc desc) {
        desc.setEntity(this);
        descs.add(desc);
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public List<SourceDesc> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<SourceDesc> descs) {
        this.descs = descs;
    }

}
