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
import org.niord.core.publication.vo.PublicationVo;
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
 * Defines a publication that may be associated with a message.
 */
@Entity
@NamedQueries({
        @NamedQuery(name  = "Publication.searchPublications",
                query = "select distinct p from Publication p join p.descs d where p.active in (:active) and "
                        + " d.lang = :lang and lower(d.name) like :term"),
        @NamedQuery(name  = "Publication.findByName",
                query = "select distinct p from Publication p join p.descs d where "
                        + " d.lang = :lang and lower(d.name) = :name")
})
@SuppressWarnings("unused")
public class Publication extends BaseEntity<Integer> implements ILocalizable<PublicationDesc> {

    /** If the publication is currently active or not **/
    boolean active = true;

    /**
     * If Message.autoPublication is true, the MessageDesc.publication will automatically be computed from
     * all associated MessagePublication's where the publication internal flag is not set.
     */
    boolean internal;

    /** If the messagePublicationLink is set, each message publication will have its own link **/
    boolean messagePublicationLink;


    @OneToMany(cascade = CascadeType.ALL, mappedBy = "entity", orphanRemoval = true)
    List<PublicationDesc> descs = new ArrayList<>();


    /** Constructor */
    public Publication() {
    }


    /** Constructor */
    public Publication(PublicationVo publication) {
        this.id = publication.getId();
        this.active = publication.isActive();
        this.internal = publication.isInternal();
        this.messagePublicationLink = publication.isMessagePublicationLink();

        if (publication.getDescs() != null) {
            publication.getDescs().stream()
                    .filter(PublicationDescVo::descDefined)
                    .forEach(desc -> addDesc(new PublicationDesc(desc)));
        }
    }


    /** Updates this publication from another publication */
    public void updatePublication(Publication publication) {
        this.internal = publication.isInternal();
        this.active = publication.isActive();
        this.messagePublicationLink = publication.isMessagePublicationLink();
        copyDescsAndRemoveBlanks(publication.getDescs());
    }


    /** Converts this entity to a value object */
    public PublicationVo toVo(DataFilter dataFilter) {
        PublicationVo publication = new PublicationVo();
        publication.setId(id);
        publication.setInternal(internal);
        publication.setActive(active);
        publication.setMessagePublicationLink(messagePublicationLink);

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
        desc.setLang(lang);
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

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isInternal() {
        return internal;
    }

    public void setInternal(boolean internal) {
        this.internal = internal;
    }

    public boolean isMessagePublicationLink() {
        return messagePublicationLink;
    }

    public void setMessagePublicationLink(boolean messagePublicationLink) {
        this.messagePublicationLink = messagePublicationLink;
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
