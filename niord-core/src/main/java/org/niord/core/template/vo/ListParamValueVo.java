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

package org.niord.core.template.vo;

import org.niord.model.DataFilter;
import org.niord.model.IJsonSerializable;
import org.niord.model.ILocalizable;

import java.util.List;

/**
 * Value object for the {@code ListParamValue} model entity
 */
public class ListParamValueVo implements ILocalizable<ListParamValueDescVo>, IJsonSerializable {

    Integer id;

    List<ListParamValueDescVo> descs;


    /** Returns a filtered copy of this entity **/
    public ListParamValueVo copy(DataFilter filter) {
        ListParamValueVo val = new ListParamValueVo();
        val.setId(id);
        val.setDescs(getDescs(filter));
        return val;
    }


    /** {@inheritDoc} */
    @Override
    public ListParamValueDescVo createDesc(String lang) {
        ListParamValueDescVo desc = new ListParamValueDescVo();
        desc.setLang(lang);
        checkCreateDescs().add(desc);
        return desc;
    }

    // ***********************************
    // Getters and setters
    // ***********************************

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    @Override
    public List<ListParamValueDescVo> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<ListParamValueDescVo> descs) {
        this.descs = descs;
    }
}
