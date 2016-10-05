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

package org.niord.core.category.vo;

import org.niord.model.message.CategoryVo;

import java.util.ArrayList;
import java.util.List;

/**
 * Extends the {@linkplain CategoryVo} model with system-specific fields and attributes.
 */
@SuppressWarnings("unused")
public class SystemCategoryVo extends CategoryVo {

    List<SystemCategoryVo> children;
    List<String> editorFields;


    /** Returns the list of child categories, and creates an empty list if needed */
    public List<SystemCategoryVo> checkCreateChildren() {
        if (children == null) {
            children = new ArrayList<>();
        }
        return children;
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public List<SystemCategoryVo> getChildren() {
        return children;
    }

    public void setChildren(List<SystemCategoryVo> children) {
        this.children = children;
    }

    public List<String> getEditorFields() {
        return editorFields;
    }

    public void setEditorFields(List<String> editorFields) {
        this.editorFields = editorFields;
    }
}
