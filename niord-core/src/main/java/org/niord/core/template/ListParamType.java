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

package org.niord.core.template;

import org.niord.core.template.vo.ListParamTypeVo;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.OrderColumn;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity class for the list-based template parameter type
 */
@Entity
@NamedQueries({
        @NamedQuery(name  = "ListParamType.findAll",
                query = "select t from ListParamType t order by lower(t.name) asc")
})
@SuppressWarnings("unused")
public class ListParamType extends ParamType {

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "listParamType", orphanRemoval = true)
    @OrderColumn(name = "indexNo")
    List<ListParamValue> values = new ArrayList<>();


    /** Constructor **/
    public ListParamType() {
    }


    /** Constructor **/
    public ListParamType(ListParamTypeVo type) {
        super(type);
        type.getValues().forEach(v -> values.add(new ListParamValue(v)));
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    public List<ListParamValue> getValues() {
        return values;
    }

    public void setValues(List<ListParamValue> values) {
        this.values = values;
    }

}
