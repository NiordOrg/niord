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

import org.niord.core.category.vo.CompositeParamTypeVo;
import org.niord.model.DataFilter;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Entity class for the composite template parameter type
 */
@Entity
@SuppressWarnings("unused")
public class CompositeParamType extends ParamType {

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn(name = "indexNo")
    List<TemplateParam> templateParams = new ArrayList<>();


    /** Constructor **/
    public CompositeParamType() {
    }


    /** Constructor **/
    public CompositeParamType(CompositeParamTypeVo type) {
        super(type);
        type.getTemplateParams().forEach(v -> templateParams.add(new TemplateParam(v)));
    }


    /** {@inheritDoc} **/
    @Override
    public CompositeParamTypeVo toVo(DataFilter filter) {
        CompositeParamTypeVo paramType = new CompositeParamTypeVo();
        paramType.setId(this.getId());
        paramType.setName(name);
        paramType.setTemplateParams(templateParams.stream()
            .map(v -> v.toVo(filter))
            .collect(Collectors.toList()));
        return paramType;
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public List<TemplateParam> getTemplateParams() {
        return templateParams;
    }

    public void setTemplateParams(List<TemplateParam> values) {
        this.templateParams = values;
    }

}
