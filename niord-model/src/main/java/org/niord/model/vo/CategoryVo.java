/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.niord.model.vo;

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
        "mrn", "parent", "children", "descs"
})
@SuppressWarnings("unused")
public class CategoryVo implements ILocalizable<CategoryDescVo>, IJsonSerializable {
    Integer id;
    String mrn;
    CategoryVo parent;
    List<CategoryVo> children;
    List<CategoryDescVo> descs;


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
}
