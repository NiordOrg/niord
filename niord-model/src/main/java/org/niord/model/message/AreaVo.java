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
import org.niord.model.DataFilter;
import org.niord.model.IJsonSerializable;
import org.niord.model.ILocalizable;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.util.List;

/**
 * Value object for the {@code Area} model entity
 */
@ApiModel(value = "Area", description = "Hierarchical area model")
@XmlRootElement(name = "area")
@XmlType(propOrder = {
        "mrn", "active", "parent", "descs"
})
@SuppressWarnings("unused")
public class AreaVo implements ILocalizable<AreaDescVo>, IJsonSerializable {

    /**
     * The Id.
     */
    Integer id;
    /**
     * The Mrn.
     */
    String mrn;
    /**
     * The Active.
     */
    boolean active = true;
    /**
     * The Parent.
     */
    AreaVo parent;
    /**
     * The Descs.
     */
    List<AreaDescVo> descs;


    /**
     * Returns a filtered copy of this entity  @param filter the filter
     *
     * @return the area vo
     */
    public AreaVo copy(DataFilter filter) {
        DataFilter compFilter = filter.forComponent("Area");

        AreaVo area = new AreaVo();
        area.setId(id);
        area.setMrn(mrn);
        area.setActive(active);
        area.copyDescs(getDescs(compFilter));

        if (compFilter.includeParent() && parent != null) {
            area.setParent(parent.copy(filter));
        }

        return area;
    }


    /** {@inheritDoc} */
    @Override
    public AreaDescVo createDesc(String lang) {
        AreaDescVo desc = new AreaDescVo();
        desc.setLang(lang);
        checkCreateDescs().add(desc);
        return desc;
    }


    /*************************/
    /** Getters and Setters **/
    /**
     * Gets id.
     *
     * @return the id
     */

    @XmlAttribute
    public Integer getId() {
        return id;
    }

    /**
     * Sets id.
     *
     * @param id the id
     */
    public void setId(Integer id) {
        this.id = id;
    }

    /**
     * Gets mrn.
     *
     * @return the mrn
     */
    public String getMrn() {
        return mrn;
    }

    /**
     * Sets mrn.
     *
     * @param mrn the mrn
     */
    public void setMrn(String mrn) {
        this.mrn = mrn;
    }

    /**
     * Is active boolean.
     *
     * @return the boolean
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Sets active.
     *
     * @param active the active
     */
    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Gets parent.
     *
     * @return the parent
     */
    public AreaVo getParent() {
        return parent;
    }

    /**
     * Sets parent.
     *
     * @param parent the parent
     */
    public void setParent(AreaVo parent) {
        this.parent = parent;
    }

    @Override
    public List<AreaDescVo> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<AreaDescVo> descs) {
        this.descs = descs;
    }

}
