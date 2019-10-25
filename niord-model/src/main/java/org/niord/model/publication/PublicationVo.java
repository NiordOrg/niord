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

package org.niord.model.publication;

import io.swagger.annotations.ApiModel;
import org.apache.commons.lang.StringUtils;
import org.niord.model.DataFilter;
import org.niord.model.IJsonSerializable;
import org.niord.model.ILocalizable;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.util.Date;
import java.util.List;

/**
 * Value object for the {@code Publication} model entity
 */
@ApiModel(value = "Publication", description = "Publication model")
@XmlRootElement(name = "publication")
@XmlType(propOrder = {
        "category", "type", "publishDateFrom", "publishDateTo", "descs"
})
@SuppressWarnings("unused")
public class PublicationVo implements ILocalizable<PublicationDescVo>, IJsonSerializable {

    /**
     * The Publication id.
     */
    String publicationId;
    /**
     * The Created.
     */
    Date created;
    /**
     * The Updated.
     */
    Date updated;
    /**
     * The Category.
     */
    PublicationCategoryVo category;
    /**
     * The Type.
     */
    PublicationType type;
    /**
     * The Publish date from.
     */
    Date publishDateFrom;
    /**
     * The Publish date to.
     */
    Date publishDateTo;
    /**
     * The Descs.
     */
    List<PublicationDescVo> descs;


    /**
     * {@inheritDoc}
     */
    @Override
    public PublicationDescVo createDesc(String lang) {
        PublicationDescVo desc = new PublicationDescVo();
        desc.setLang(lang);
        checkCreateDescs().add(desc);
        return desc;
    }


    /**
     * Returns a filtered copy of this entity
     * @param filter the filter
     *
     * @return the publication vo
     */
    public PublicationVo copy(DataFilter filter) {

        PublicationVo publication = new PublicationVo();
        publication.setPublicationId(publicationId);
        publication.setCreated(created);
        publication.setUpdated(updated);
        if (category != null) {
            publication.setCategory(category.copy(filter));
        }
        publication.setType(type);
        publication.setPublishDateFrom(publishDateFrom);
        publication.setPublishDateTo(publishDateTo);
        publication.copyDescs(getDescs(filter));
        return publication;
    }


    /**
     * Rewrites the publication links from one repository path to another.
     *
     * @param repoPath1 the repo path 1
     * @param repoPath2 the repo path 2
     */
    public void rewriteRepoPath(String repoPath1, String repoPath2) {
        if (StringUtils.isNotBlank(repoPath1) && StringUtils.isNotBlank(repoPath2)) {
            if (getDescs() != null) {
                getDescs().forEach(desc -> {
                    if (desc.getLink() != null && desc.getLink().contains(repoPath1)) {
                        desc.setLink(desc.getLink().replace(repoPath1, repoPath2));
                    }
                });
            }
        }
    }


    /*************************/
    /** Getters and Setters **/
    /**
     * Gets publication id.
     *
     * @return the publication id
     */

    @XmlAttribute
    public String getPublicationId() {
        return publicationId;
    }

    /**
     * Sets publication id.
     *
     * @param publicationId the publication id
     */
    public void setPublicationId(String publicationId) {
        this.publicationId = publicationId;
    }

    /**
     * Gets created.
     *
     * @return the created
     */
    @XmlAttribute
    public Date getCreated() {
        return created;
    }

    /**
     * Sets created.
     *
     * @param created the created
     */
    public void setCreated(Date created) {
        this.created = created;
    }

    /**
     * Gets updated.
     *
     * @return the updated
     */
    @XmlAttribute
    public Date getUpdated() {
        return updated;
    }

    /**
     * Sets updated.
     *
     * @param updated the updated
     */
    public void setUpdated(Date updated) {
        this.updated = updated;
    }

    /**
     * Gets category.
     *
     * @return the category
     */
    public PublicationCategoryVo getCategory() {
        return category;
    }

    /**
     * Sets category.
     *
     * @param category the category
     */
    public void setCategory(PublicationCategoryVo category) {
        this.category = category;
    }

    /**
     * Gets type.
     *
     * @return the type
     */
    public PublicationType getType() {
        return type;
    }

    /**
     * Sets type.
     *
     * @param type the type
     */
    public void setType(PublicationType type) {
        this.type = type;
    }

    /**
     * Gets publish date from.
     *
     * @return the publish date from
     */
    public Date getPublishDateFrom() {
        return publishDateFrom;
    }

    /**
     * Sets publish date from.
     *
     * @param publishDateFrom the publish date from
     */
    public void setPublishDateFrom(Date publishDateFrom) {
        this.publishDateFrom = publishDateFrom;
    }

    /**
     * Gets publish date to.
     *
     * @return the publish date to
     */
    public Date getPublishDateTo() {
        return publishDateTo;
    }

    /**
     * Sets publish date to.
     *
     * @param publishDateTo the publish date to
     */
    public void setPublishDateTo(Date publishDateTo) {
        this.publishDateTo = publishDateTo;
    }

    @Override
    public List<PublicationDescVo> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<PublicationDescVo> descs) {
        this.descs = descs;
    }
}
