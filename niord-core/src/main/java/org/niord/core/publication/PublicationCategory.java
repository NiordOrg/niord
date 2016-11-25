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
import org.niord.model.publication.PublicationCategoryDescVo;
import org.niord.model.publication.PublicationCategoryVo;
import org.niord.model.DataFilter;
import org.niord.model.ILocalizable;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Defines a publication category that must be associated with a message.
 */
@Entity
@NamedQueries({
        @NamedQuery(name= "PublicationCategory.findByCategoryId",
                query="SELECT t FROM PublicationCategory t where t.categoryId = :categoryId")
})
@SuppressWarnings("unused")
public class PublicationCategory extends BaseEntity<Integer> implements ILocalizable<PublicationCategoryDesc> {

    @NotNull
    @Column(unique = true)
    String categoryId;

    int priority = 0;

    /** Whether to publish publications of this category or not **/
    boolean publish = false;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "entity", orphanRemoval = true)
    List<PublicationCategoryDesc> descs = new ArrayList<>();


    /** Constructor */
    public PublicationCategory() {
    }


    /** Constructor */
    public PublicationCategory(PublicationCategoryVo category) {
        this.categoryId = category.getCategoryId();
        this.priority = category.getPriority() != null ? category.getPriority() : 0;
        this.publish = category.getPublish() != null ? category.getPublish() : false;

        if (category.getDescs() != null) {
            category.getDescs().stream()
                    .filter(PublicationCategoryDescVo::descDefined)
                    .forEach(desc -> addDesc(new PublicationCategoryDesc(desc)));
        }
    }


    /** Updates this publication from another publication category */
    public void updatePublicationCategory(PublicationCategory publication) {
        this.categoryId = publication.getCategoryId();
        this.priority = publication.getPriority();
        this.publish = publication.isPublish();
        descs.clear();
        copyDescsAndRemoveBlanks(publication.getDescs());
    }


    /** Converts this entity to a value object */
    public PublicationCategoryVo toVo(DataFilter dataFilter) {
        PublicationCategoryVo category = new PublicationCategoryVo();
        category.setCategoryId(categoryId);
        category.setPriority(priority);
        category.setPublish(publish);

        if (!descs.isEmpty()) {
            category.setDescs(getDescs(dataFilter).stream()
                    .map(PublicationCategoryDesc::toVo)
                    .collect(Collectors.toList()));
        }
        return category;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public PublicationCategoryDesc createDesc(String lang) {
        PublicationCategoryDesc desc = new PublicationCategoryDesc();
        desc.setLang(lang);
        desc.setEntity(this);
        getDescs().add(desc);
        return desc;
    }


    /** Adds a description entity to this entity */
    public void addDesc(PublicationCategoryDesc desc) {
        desc.setEntity(this);
        descs.add(desc);
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
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
    public List<PublicationCategoryDesc> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<PublicationCategoryDesc> descs) {
        this.descs = descs;
    }
}
