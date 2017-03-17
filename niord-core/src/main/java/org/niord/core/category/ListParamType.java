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

import org.niord.core.category.vo.ListParamTypeVo;
import org.niord.core.dictionary.DictionaryEntry;
import org.niord.model.DataFilter;

import javax.persistence.Entity;
import javax.persistence.OneToMany;
import javax.persistence.OrderColumn;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Entity class for the list-based template parameter type
 */
@Entity
@SuppressWarnings("unused")
public class ListParamType extends ParamType {

    @OneToMany
    @OrderColumn(name = "indexNo")
    List<DictionaryEntry> values = new ArrayList<>();


    /** Constructor **/
    public ListParamType() {
    }


    /** Constructor **/
    public ListParamType(ListParamTypeVo type) {
        super(type);
        this.id = type.getId();
        type.getValues().forEach(v -> values.add(new DictionaryEntry(v)));
    }


    /** {@inheritDoc} **/
    @Override
    public ListParamTypeVo toVo(DataFilter filter) {
        ListParamTypeVo paramType = new ListParamTypeVo();
        paramType.setId(id);
        paramType.setName(name);
        paramType.setValues(values.stream()
            .map(v -> v.toVo(filter))
            .collect(Collectors.toList()));
        return paramType;
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public List<DictionaryEntry> getValues() {
        return values;
    }

    public void setValues(List<DictionaryEntry> values) {
        this.values = values;
    }

}
