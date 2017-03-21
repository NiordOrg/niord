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

import com.fasterxml.jackson.core.type.TypeReference;
import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.category.vo.ParamTypeVo;
import org.niord.core.util.JsonUtils;

import javax.inject.Named;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.List;

/**
 * Reads parameter types from a param-types.json file.
 * <p>
 * Please note, the actual template-param-import.xml job file is not placed in the META-INF/batch-jobs of this project,
 * but rather, in the META-INF/batch-jobs folder of the niord-web project.<br>
 * This is because of a class-loading bug in the Wildfly implementation. See e.g.
 * https://issues.jboss.org/browse/WFLY-4988
 * <p>
 * Format of json file is defined by the ParamTypeVo class and sub-classes:
 * <pre>
 * [{
 *      "type" : "COMPOSITE",
 *      "name" : "buoy_by_function",
 *      "templateParams" : [
 *          { "paramId" : "aton_type",
 *            "type" : "buoy_function_list",
 *            "mandatory" : false,
 *            "list" : false,
 *            "descs":[{ "name" : "Buoy Type", "lang":"en"}]
 *          },
 *          ....
 *   },
 *   ...
 * ]
 * </pre>
 */
@Named
public class BatchParamTypeImportReader extends AbstractItemHandler {

    List<ParamTypeVo> paramTypes;
    int paramNo = 0;

    /** {@inheritDoc} **/
    @Override
    public void open(Serializable prevCheckpointInfo) throws Exception {

        // Get hold of the data file
        Path path = batchService.getBatchJobDataFile(jobContext.getInstanceId());

        // Load the template parameters from the file
        paramTypes = JsonUtils.readJson(
                new TypeReference<List<ParamTypeVo>>(){},
                path);

        if (prevCheckpointInfo != null) {
            paramNo = (Integer) prevCheckpointInfo;
        }

        getLog().info("Start processing " + paramTypes.size() + " parameter type from index " + paramNo);
    }


    /** {@inheritDoc} **/
    @Override
    public Object readItem() throws Exception {
        if (paramNo < paramTypes.size()) {
            getLog().info("Reading parameter type no " + paramNo);
            return paramTypes.get(paramNo++);
        }
        return null;
    }


    /** {@inheritDoc} **/
    @Override
    public Serializable checkpointInfo() throws Exception {
        return paramNo;
    }
}
