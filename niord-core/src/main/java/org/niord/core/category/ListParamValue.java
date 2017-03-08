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

import org.niord.core.model.BaseEntity;
import org.niord.core.model.IndexedEntity;
import org.niord.core.category.vo.ListParamValueDescVo;
import org.niord.core.category.vo.ListParamValueVo;
import org.niord.model.ILocalizable;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity class for the list parameter type values
 */
@Entity
@SuppressWarnings("unused")
public class ListParamValue extends BaseEntity<Integer> implements ILocalizable<ListParamValueDesc>, IndexedEntity {

    @ManyToOne
    @NotNull
    ListParamType listParamType;

    int indexNo;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "entity", orphanRemoval = true)
    List<ListParamValueDesc> descs = new ArrayList<>();


    /** Constructor **/
    public ListParamValue() {
    }


    /** Constructor **/
    public ListParamValue(ListParamValueVo value) {
        this.id = value.getId();
        if (value.getDescs() != null) {
            value.getDescs().stream()
                    .filter(ListParamValueDescVo::descDefined)
                    .forEach(desc -> addDesc(new ListParamValueDesc(desc)));
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public ListParamValueDesc createDesc(String lang) {
        ListParamValueDesc desc = new ListParamValueDesc();
        desc.setLang(lang);
        desc.setEntity(this);
        getDescs().add(desc);
        return desc;
    }


    /** Adds a description entity to this entity */
    public ListParamValueDesc addDesc(ListParamValueDesc desc) {
        desc.setEntity(this);
        descs.add(desc);
        return desc;
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    public ListParamType getListParamType() {
        return listParamType;
    }

    public void setListParamType(ListParamType listParamType) {
        this.listParamType = listParamType;
    }

    @Override
    public int getIndexNo() {
        return indexNo;
    }

    @Override
    public void setIndexNo(int indexNo) {
        this.indexNo = indexNo;
    }

    @Override
    public List<ListParamValueDesc> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<ListParamValueDesc> descs) {
        this.descs = descs;
    }

}
