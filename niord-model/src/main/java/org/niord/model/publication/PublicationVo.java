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

import org.apache.commons.lang.StringUtils;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
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
@Schema(name = "Publication", description = "Publication model")
@XmlRootElement(name = "publication")
@XmlType(propOrder = {
        "category", "type", "publishDateFrom", "publishDateTo", "descs"
})
@SuppressWarnings("unused")
public class PublicationVo implements ILocalizable<PublicationDescVo>, IJsonSerializable {

    String publicationId;
    Date created;
    Date updated;
    PublicationCategoryVo category;
    PublicationType type;
    Date publishDateFrom;
    Date publishDateTo;
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


    /** Returns a filtered copy of this entity **/
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
    /*************************/

    @XmlAttribute
    public String getPublicationId() {
        return publicationId;
    }

    public void setPublicationId(String publicationId) {
        this.publicationId = publicationId;
    }

    @XmlAttribute
    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    @XmlAttribute
    public Date getUpdated() {
        return updated;
    }

    public void setUpdated(Date updated) {
        this.updated = updated;
    }

    public PublicationCategoryVo getCategory() {
        return category;
    }

    public void setCategory(PublicationCategoryVo category) {
        this.category = category;
    }

    public PublicationType getType() {
        return type;
    }

    public void setType(PublicationType type) {
        this.type = type;
    }

    public Date getPublishDateFrom() {
        return publishDateFrom;
    }

    public void setPublishDateFrom(Date publishDateFrom) {
        this.publishDateFrom = publishDateFrom;
    }

    public Date getPublishDateTo() {
        return publishDateTo;
    }

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
