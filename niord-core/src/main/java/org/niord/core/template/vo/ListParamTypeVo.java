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

import com.fasterxml.jackson.annotation.JsonTypeName;

import java.util.ArrayList;
import java.util.List;

/**
 * Value object for the {@code ListParamType} model entity
 */
@JsonTypeName("LIST")
@SuppressWarnings("unused")
public class ListParamTypeVo extends ParamTypeVo {

    List<ListParamValueVo> values = new ArrayList<>();

    /**
     * Returns or creates the list of values
     * @return the list of values
     */
    public List<ListParamValueVo> checkCreateValues() {
        if (values == null) {
            values = new ArrayList<>();
        }
        return values;
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public List<ListParamValueVo> getValues() {
        return values;
    }

    public void setValues(List<ListParamValueVo> values) {
        this.values = values;
    }
}
