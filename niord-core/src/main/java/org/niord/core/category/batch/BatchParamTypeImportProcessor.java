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
package org.niord.core.category.batch;

import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.category.ParamType;
import org.niord.core.category.TemplateExecutionService;
import org.niord.core.category.vo.ParamTypeVo;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * Processes parameter types
 */
@Dependent
@Named("batchParamTypeImportProcessor")
public class BatchParamTypeImportProcessor extends AbstractItemHandler {

    @Inject
    TemplateExecutionService templateExecutionService;


    /** {@inheritDoc} **/
    @Override
    public Object processItem(Object item) throws Exception {

        ParamTypeVo paramTypeVo = (ParamTypeVo)item;

        ParamType paramType = paramTypeVo.toEntity();

        getLog().info("Creating or updating parameter types " + paramType.getName());

        ParamType original = templateExecutionService.getParamTypeByName(paramType.getName());

        if (original == null) {
            return templateExecutionService.createParamType(paramType);
        } else {
            paramType.setId(original.getId());
            return templateExecutionService.updateParamType(paramType);
        }
    }
}
