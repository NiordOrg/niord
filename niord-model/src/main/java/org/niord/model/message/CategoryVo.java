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
package org.niord.model.message;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.niord.model.DataFilter;
import org.niord.model.IJsonSerializable;
import org.niord.model.ILocalizable;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import java.util.List;

/**
 * Value object for the {@code Category} model entity
 */
@Schema(name = "Category", description = "Hierarchical category model")
@XmlRootElement(name = "category")
@XmlType(propOrder = {
        "mrn", "active", "parent", "descs"
})
@SuppressWarnings("unused")
public class CategoryVo implements ILocalizable<CategoryDescVo>, IJsonSerializable {
    Integer id;
    String mrn;
    boolean active = true;
    CategoryVo parent;
    List<CategoryDescVo> descs;


    /** Returns a filtered copy of this entity **/
    public CategoryVo copy(DataFilter filter) {

        DataFilter compFilter = filter.forComponent("Category");

        CategoryVo category = new CategoryVo();
        category.setId(id);
        category.setMrn(mrn);
        category.setActive(active);
        category.copyDescs(getDescs(compFilter));

        if (compFilter.includeParent() && parent != null) {
            category.setParent(parent.copy(compFilter));
        }

        return category;
    }


    /** {@inheritDoc} */
    @Override
    public CategoryDescVo createDesc(String lang) {
        CategoryDescVo desc = new CategoryDescVo();
        desc.setLang(lang);
        checkCreateDescs().add(desc);
        return desc;
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    @XmlAttribute
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getMrn() {
        return mrn;
    }

    public void setMrn(String mrn) {
        this.mrn = mrn;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public CategoryVo getParent() {
        return parent;
    }

    public void setParent(CategoryVo parent) {
        this.parent = parent;
    }

    @Override
    public List<CategoryDescVo> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<CategoryDescVo> descs) {
        this.descs = descs;
    }

}
