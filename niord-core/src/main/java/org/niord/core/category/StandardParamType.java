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

import org.niord.core.category.vo.StandardParamTypeVo;
import org.niord.model.DataFilter;

import javax.persistence.Entity;

/**
 * Entity class for the standard-based template parameter type, i.e.
 * "text", "boolean", "number", etc.
 */
@Entity
@SuppressWarnings("unused")
public class StandardParamType extends ParamType {

    public static final String[] STANDARD_PARAM_TYPES = {
            "text",             // Generic text
            "number",           // Numbers
            "boolean",          // Booleans
            "aton_name",        // Textual name of AtoN
            "light_character",  // Validated light character field (e.g. "Fl(2+1) R 10s"
            "call_sign"         // Validated vessel call sign
    };


    /** Constructor **/
    public StandardParamType() {
    }


    /** Constructor **/
    public StandardParamType(String name) {
        this.name = name;
    }


    /** Constructor **/
    public StandardParamType(StandardParamTypeVo type) {
        super(type);
    }


    /** {@inheritDoc} **/
    @Override
    public StandardParamTypeVo toVo(DataFilter filter) {
        StandardParamTypeVo paramType = new StandardParamTypeVo();
        paramType.setId(id);
        paramType.setName(name);
        return paramType;
    }
}
