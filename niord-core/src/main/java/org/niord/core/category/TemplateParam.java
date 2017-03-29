/*
 * Copyright 2017 Danish Maritime Authority.
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

package org.niord.core.category;

import org.niord.core.category.vo.TemplateParamVo;
import org.niord.core.model.BaseEntity;
import org.niord.core.model.IndexedEntity;
import org.niord.model.DataFilter;
import org.niord.model.ILocalizable;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.OneToMany;
import javax.persistence.Transient;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Entity class for the message template parameters
 */
@Entity
@SuppressWarnings("unused")
public class TemplateParam extends BaseEntity<Integer> implements ILocalizable<TemplateParamDesc>, IndexedEntity {

    int indexNo;

    @NotNull
    String paramId;

    /**
     * The type is a "loose" reference to a named parameter type.
     * The parameter type may be one of:
     * <ul>
     *     <li>A base parameter type, either "text", "number", "boolean" or "date".</li>
     *     <li>A list parameter type as defined by the {@code ListParamType} entity.</li>
     *     <li>A composite parameter type as defined by the {@code CompositeParamType} entity.</li>
     * </ul>
     */
    @NotNull
    String type;

    boolean mandatory;

    boolean positionList;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "entity", orphanRemoval = true)
    List<TemplateParamDesc> descs = new ArrayList<>();


    /** Constructor **/
    public TemplateParam() {
    }


    /** Constructor **/
    public TemplateParam(TemplateParamVo param) {
        this.paramId = param.getParamId();
        this.type = param.getType();
        this.mandatory = param.isMandatory();
        this.positionList = param.isPositionList();
        if (param.getDescs() != null) {
            param.getDescs()
                    .forEach(desc -> createDesc(desc.getLang()).setName(desc.getName()));
        }
    }


    /** {@inheritDoc} **/
    public TemplateParamVo toVo(DataFilter filter) {
        TemplateParamVo param = new TemplateParamVo();
        param.setParamId(paramId);
        param.setType(type);
        param.setMandatory(mandatory);
        param.setPositionList(positionList);
        if (!descs.isEmpty()) {
            param.setDescs(getDescs(filter).stream()
                    .map(TemplateParamDesc::toVo)
                    .collect(Collectors.toList()));
        }
        return param;
    }


    /** {@inheritDoc} */
    @Override
    public TemplateParamDesc createDesc(String lang) {
        TemplateParamDesc desc = new TemplateParamDesc();
        desc.setLang(lang);
        desc.setEntity(this);
        getDescs().add(desc);
        return desc;
    }


    /**
     * Checks if the template param has changed
     * @param param the template param to compare with
     * @return if the template param has changed
     */
    @Transient
    public boolean hasChanged(TemplateParam param) {
        return !Objects.equals(paramId, param.getParamId()) ||
                !Objects.equals(type, param.getType()) ||
                !Objects.equals(mandatory, param.isMandatory()) ||
                !Objects.equals(positionList, param.isPositionList()) ||
                descsChanged(param);
    }


    /** Checks if the description has changed */
    private boolean descsChanged(TemplateParam param) {
        return descs.size() != param.getDescs().size() ||
                descs.stream()
                        .anyMatch(d -> param.getDesc(d.getLang()) == null ||
                                !Objects.equals(d.getName(), param.getDesc(d.getLang()).getName()));
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    @Override
    public int getIndexNo() {
        return indexNo;
    }

    @Override
    public void setIndexNo(int indexNo) {
        this.indexNo = indexNo;
    }

    public String getParamId() {
        return paramId;
    }

    public void setParamId(String paramId) {
        this.paramId = paramId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public void setMandatory(boolean mandatory) {
        this.mandatory = mandatory;
    }

    public boolean isPositionList() {
        return positionList;
    }

    public void setPositionList(boolean positionList) {
        this.positionList = positionList;
    }

    @Override
    public List<TemplateParamDesc> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<TemplateParamDesc> descs) {
        this.descs = descs;
    }
}
