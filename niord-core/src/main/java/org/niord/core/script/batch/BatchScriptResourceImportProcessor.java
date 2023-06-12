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

package org.niord.core.script.batch;

import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.script.ScriptResource;
import org.niord.core.script.ScriptResourceService;
import org.niord.core.script.vo.ScriptResourceVo;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.inject.Named;

/**
 * Converts the script resource value object into a script resource entity template
 */
@Dependent
@Named("batchScriptResourceImportProcessor")
public class BatchScriptResourceImportProcessor extends AbstractItemHandler {

    @Inject
    ScriptResourceService resourceService;


    /** {@inheritDoc} **/
    @Override
    public Object processItem(Object item) throws Exception {

        ScriptResourceVo resourceVo = (ScriptResourceVo) item;

        // Reset the ID of the resource, as we match on path, not ID
        resourceVo.setId(null);

        return new ScriptResource(resourceVo);
    }
}
