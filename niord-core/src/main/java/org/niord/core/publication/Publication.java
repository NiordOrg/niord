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

package org.niord.core.publication;

import org.niord.core.model.BaseEntity;
import org.niord.core.publication.vo.PublicationDescVo;
import org.niord.core.publication.vo.PublicationType;
import org.niord.core.publication.vo.PublicationVo;
import org.niord.model.DataFilter;
import org.niord.model.ILocalizable;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Defines a publication that may be associated with a message.
 */
@Entity
@NamedQueries({
        @NamedQuery(name="Publication.findByPublicationId",
                query="SELECT p FROM Publication p where p.publicationId = :publicationId"),
        @NamedQuery(name  = "Publication.searchPublications",
                query = "select distinct p from Publication p join p.descs d where p.active in (:active) "
                        + " and p.type in (:types) and (d.lang = :lang or d.lang = '*') and lower(d.title) like :term")
})
@SuppressWarnings("unused")
public class Publication extends BaseEntity<Integer> implements ILocalizable<PublicationDesc> {

    @NotNull
    String publicationId;

    PublicationType type;

    /** If the publication is currently active or not **/
    boolean active = true;

    boolean languageSpecific;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "entity", orphanRemoval = true)
    List<PublicationDesc> descs = new ArrayList<>();


    /** Constructor */
    public Publication() {
    }


    /** Constructor */
    public Publication(PublicationVo publication) {
        this.publicationId = publication.getPublicationId();
        this.type = publication.getType();
        this.active = publication.isActive();
        this.languageSpecific = publication.isLanguageSpecific();

        if (publication.getDescs() != null) {
            publication.getDescs().stream()
                    .filter(PublicationDescVo::descDefined)
                    .forEach(desc -> addDesc(new PublicationDesc(desc)));
        }
    }


    /** Updates this publication from another publication */
    public void updatePublication(Publication publication) {
        this.publicationId = publication.getPublicationId();
        this.type = publication.getType();
        this.active = publication.isActive();
        this.languageSpecific = publication.isLanguageSpecific();
        copyDescsAndRemoveBlanks(publication.getDescs());
    }


    /** Converts this entity to a value object */
    public PublicationVo toVo(DataFilter dataFilter) {
        PublicationVo publication = new PublicationVo();
        publication.setPublicationId(publicationId);
        publication.setType(type);
        publication.setActive(active);
        publication.setLanguageSpecific(languageSpecific);

        if (!descs.isEmpty()) {
            publication.setDescs(getDescs(dataFilter).stream()
                    .map(PublicationDesc::toVo)
                    .collect(Collectors.toList()));
        }
        return publication;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public PublicationDesc createDesc(String lang) {
        PublicationDesc desc = new PublicationDesc();
        desc.setLang(lang == null ? "*" : lang);
        desc.setEntity(this);
        getDescs().add(desc);
        return desc;
    }


    /** Adds a description entity to this entity */
    public void addDesc(PublicationDesc desc) {
        desc.setEntity(this);
        descs.add(desc);
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getPublicationId() {
        return publicationId;
    }

    public void setPublicationId(String publicationId) {
        this.publicationId = publicationId;
    }

    public PublicationType getType() {
        return type;
    }

    public void setType(PublicationType type) {
        this.type = type;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isLanguageSpecific() {
        return languageSpecific;
    }

    public void setLanguageSpecific(boolean languageSpecific) {
        this.languageSpecific = languageSpecific;
    }

    @Override
    public List<PublicationDesc> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<PublicationDesc> descs) {
        this.descs = descs;
    }
}
