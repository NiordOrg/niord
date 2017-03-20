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

package org.niord.core.category.vo;

import org.niord.model.IJsonSerializable;
import org.niord.model.ILocalizable;

import java.util.List;

/**
 * Value object for the TemplateParam entity
 */
public class TemplateParamVo implements ILocalizable<TemplateParamDescVo>, IJsonSerializable {

    String paramId;
    String type;
    boolean mandatory;
    boolean list;
    List<TemplateParamDescVo> descs;


    /** {@inheritDoc} */
    @Override
    public TemplateParamDescVo createDesc(String lang) {
        TemplateParamDescVo desc = new TemplateParamDescVo();
        desc.setLang(lang);
        checkCreateDescs().add(desc);
        return desc;
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

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

    public boolean isList() {
        return list;
    }

    public void setList(boolean list) {
        this.list = list;
    }

    @Override
    public List<TemplateParamDescVo> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<TemplateParamDescVo> descs) {
        this.descs = descs;
    }
}
