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

import com.fasterxml.jackson.core.type.TypeReference;
import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.script.vo.ScriptResourceVo;
import org.niord.core.util.JsonUtils;

import javax.enterprise.context.Dependent;
import javax.inject.Named;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.List;

/**
 * Reads Script Resources, i.e. Freemarker templates and Javascript resource from a script-resources.json file.
 * <p>
 * NB: Resource IDs are ignored, and the path is used to identify which resource to update.
 * <p>
 * Please note, the actual script-resource-import.xml job file is not placed in the META-INF/batch-jobs of this project,
 * but rather, in the META-INF/batch-jobs folder of the niord-web project.<br>
 * This is because of a class-loading bug in the Wildfly implementation. See e.g.
 * https://issues.jboss.org/browse/WFLY-4988
 * <p>
 * Format of json file is defined by the ScriptResourceVo class. Example:
 * <pre>
 * [
 *   {
 *       "id": 111111,
 *       "type": "FM",
 *       "path": "templates/messages/message-list-pdf.ftl",
 *       "content": "<#include \"message-support.ftl\"/>\n\n<html>\n..."
 *   },
 *   {
 *       etc, etc
 *   }
 * ]
 * </pre>
 */
@Dependent
@Named("batchScriptResourceImportReader")
public class BatchScriptResourceImportReader extends AbstractItemHandler {

    List<ScriptResourceVo> resources;
    int resourceNo = 0;

    /** {@inheritDoc} **/
    @Override
    public void open(Serializable prevCheckpointInfo) throws Exception {

        // Get hold of the data file
        Path path = batchService.getBatchJobDataFile(jobContext.getInstanceId());

        // Load the resources from the file
        resources = JsonUtils.readJson(
                new TypeReference<List<ScriptResourceVo>>(){},
                path);

        if (prevCheckpointInfo != null) {
            resourceNo = (Integer) prevCheckpointInfo;
        }

        getLog().info("Start processing " + resources.size() + " resources from index " + resourceNo);
    }

    /** {@inheritDoc} **/
    @Override
    public Object readItem() throws Exception {
        if (resourceNo < resources.size()) {
            getLog().info("Reading resource no " + resourceNo);
            return resources.get(resourceNo++);
        }
        return null;
    }

    /** {@inheritDoc} **/
    @Override
    public Serializable checkpointInfo() throws Exception {
        return resourceNo;
    }
}
