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
import org.niord.model.publication.PublicationTypeDescVo;
import org.niord.model.publication.PublicationTypeVo;
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
 * Defines a publication type that must be associated with a message.
 */
@Entity
@NamedQueries({
        @NamedQuery(name="PublicationType.findByTypeId",
                query="SELECT t FROM PublicationType t where t.typeId = :typeId")
})
@SuppressWarnings("unused")
public class PublicationType extends BaseEntity<Integer> implements ILocalizable<PublicationTypeDesc> {

    @NotNull
    String typeId;

    int priority = 0;

    /** Whether to publish publications of this type or not **/
    boolean publish = false;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "entity", orphanRemoval = true)
    List<PublicationTypeDesc> descs = new ArrayList<>();


    /** Constructor */
    public PublicationType() {
    }


    /** Constructor */
    public PublicationType(PublicationTypeVo type) {
        this.typeId = type.getTypeId();
        this.priority = type.getPriority() != null ? type.getPriority() : 0;
        this.publish = type.getPublish() != null ? type.getPublish() : false;

        if (type.getDescs() != null) {
            type.getDescs().stream()
                    .filter(PublicationTypeDescVo::descDefined)
                    .forEach(desc -> addDesc(new PublicationTypeDesc(desc)));
        }
    }


    /** Updates this publication from another publication type */
    public void updatePublicationType(PublicationType publication) {
        this.typeId = publication.getTypeId();
        this.priority = publication.getPriority();
        this.publish = publication.isPublish();
        descs.clear();
        copyDescsAndRemoveBlanks(publication.getDescs());
    }


    /** Converts this entity to a value object */
    public PublicationTypeVo toVo(DataFilter dataFilter) {
        PublicationTypeVo type = new PublicationTypeVo();
        type.setTypeId(typeId);
        type.setPriority(priority);
        type.setPublish(publish);

        if (!descs.isEmpty()) {
            type.setDescs(getDescs(dataFilter).stream()
                    .map(PublicationTypeDesc::toVo)
                    .collect(Collectors.toList()));
        }
        return type;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public PublicationTypeDesc createDesc(String lang) {
        PublicationTypeDesc desc = new PublicationTypeDesc();
        desc.setLang(lang);
        desc.setEntity(this);
        getDescs().add(desc);
        return desc;
    }


    /** Adds a description entity to this entity */
    public void addDesc(PublicationTypeDesc desc) {
        desc.setEntity(this);
        descs.add(desc);
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getTypeId() {
        return typeId;
    }

    public void setTypeId(String typeId) {
        this.typeId = typeId;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public boolean isPublish() {
        return publish;
    }

    public void setPublish(boolean publish) {
        this.publish = publish;
    }

    @Override
    public List<PublicationTypeDesc> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<PublicationTypeDesc> descs) {
        this.descs = descs;
    }
}
