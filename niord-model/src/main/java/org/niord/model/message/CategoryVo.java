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

import io.swagger.annotations.ApiModel;
import org.niord.model.IJsonSerializable;
import org.niord.model.ILocalizable;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.util.ArrayList;
import java.util.List;

/**
 * Value object for the {@code Category} model entity
 */
@ApiModel(value = "Category", description = "Hierarchical category model")
@XmlRootElement(name = "category")
@XmlType(propOrder = {
        "mrn", "active", "parent", "children", "descs", "editorFields"
})
@SuppressWarnings("unused")
public class CategoryVo implements ILocalizable<CategoryDescVo>, IJsonSerializable {
    Integer id;
    String mrn;
    boolean active = true;
    CategoryVo parent;
    List<CategoryVo> children;
    List<CategoryDescVo> descs;
    List<String> editorFields;


    /** {@inheritDoc} */
    @Override
    public CategoryDescVo createDesc(String lang) {
        CategoryDescVo desc = new CategoryDescVo();
        desc.setLang(lang);
        checkCreateDescs().add(desc);
        return desc;
    }


    /** Returns the list of child categories, and creates an empty list if needed */
    public List<CategoryVo> checkCreateChildren() {
        if (children == null) {
            children = new ArrayList<>();
        }
        return children;
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

    public List<CategoryVo> getChildren() {
        return children;
    }

    public void setChildren(List<CategoryVo> children) {
        this.children = children;
    }

    @Override
    public List<CategoryDescVo> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<CategoryDescVo> descs) {
        this.descs = descs;
    }

    public List<String> getEditorFields() {
        return editorFields;
    }

    public void setEditorFields(List<String> editorFields) {
        this.editorFields = editorFields;
    }
}
