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
import org.niord.model.DataFilter;
import org.niord.model.IJsonSerializable;
import org.niord.model.ILocalizable;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.util.List;

/**
 * Publication Category
 */
@ApiModel(value = "PublicationCategory", description = "Publication category model")
@XmlRootElement(name = "publicationCategory")
@XmlType(propOrder = {
        "categoryId", "priority", "publish", "descs"
})
public class PublicationCategoryVo implements ILocalizable<PublicationCategoryDescVo>, IJsonSerializable {

    /**
     * The Category id.
     */
    String categoryId;
    /**
     * The Priority.
     */
    Integer priority;
    /**
     * The Publish.
     */
    Boolean publish;
    /**
     * The Descs.
     */
    List<PublicationCategoryDescVo> descs;


    /** {@inheritDoc} */
    @Override
    public PublicationCategoryDescVo createDesc(String lang) {
        PublicationCategoryDescVo desc = new PublicationCategoryDescVo();
        desc.setLang(lang);
        checkCreateDescs().add(desc);
        return desc;
    }


    /**
     * Returns a filtered copy of this entity
     * @param filter the filter
     *
     * @return the publication category vo
     */
    public PublicationCategoryVo copy(DataFilter filter) {

        PublicationCategoryVo category = new PublicationCategoryVo();
        category.setCategoryId(categoryId);
        category.setPriority(priority);
        category.setPublish(publish);
        category.copyDescs(getDescs(filter));
        return category;
    }


    /*************************/
    /** Getters and Setters **/
    /**
     * Gets category id.
     *
     * @return the category id
     */

    public String getCategoryId() {
        return categoryId;
    }

    /**
     * Sets category id.
     *
     * @param categoryId the category id
     */
    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    /**
     * Gets priority.
     *
     * @return the priority
     */
    public Integer getPriority() {
        return priority;
    }

    /**
     * Sets priority.
     *
     * @param priority the priority
     */
    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    /**
     * Gets publish.
     *
     * @return the publish
     */
    public Boolean getPublish() {
        return publish;
    }

    /**
     * Sets publish.
     *
     * @param publish the publish
     */
    public void setPublish(Boolean publish) {
        this.publish = publish;
    }

    @Override
    public List<PublicationCategoryDescVo> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<PublicationCategoryDescVo> descs) {
        this.descs = descs;
    }
}
