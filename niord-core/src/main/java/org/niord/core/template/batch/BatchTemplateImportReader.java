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
package org.niord.core.template.batch;

import com.fasterxml.jackson.core.type.TypeReference;
import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.template.vo.TemplateVo;
import org.niord.core.util.JsonUtils;

import javax.inject.Named;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.List;

/**
 * Reads message templates from a templates.json file.
 * <p>
 * Please note, the actual template-import.xml job file is not placed in the META-INF/batch-jobs of this project,
 * but rather, in the META-INF/batch-jobs folder of the niord-web project.<br>
 * This is because of a class-loading bug in the Wildfly implementation. See e.g.
 * https://issues.jboss.org/browse/WFLY-4988
 * <p>
 * Format of json file is defined by the TemplateVo class:
 * <pre>
 * [{
 *     "category": { "descs":[{ "name": "Fyr", "lang":"da" },{ "name" : "Light", "lang" : "en"}], "id":564},
 *     "domains": [ {"domainId":"niord-client-nw"} ],
 *     "scriptResourcePaths" : [ "templates/tmpl/light-unlit.ftl", "templates/tmpl/standard.js" ],
 *     "active": true,
 *     "descs": [
 *       {
 *         "lang": "da",
 *         "name": "Sejladshindring"
 *       },
 *       {
 *         "lang": "en",
 *         "name": "Obstruction"
 *       }
 *     ]
 *   },
 *   ...
 * ]
 * </pre>
 */
@Named
public class BatchTemplateImportReader extends AbstractItemHandler {

    List<TemplateVo> templates;
    int templatesNo = 0;

    /** {@inheritDoc} **/
    @Override
    public void open(Serializable prevCheckpointInfo) throws Exception {

        // Get hold of the data file
        Path path = batchService.getBatchJobDataFile(jobContext.getInstanceId());

        // Load the templates from the file
        templates = JsonUtils.readJson(
                new TypeReference<List<TemplateVo>>(){},
                path);

        if (prevCheckpointInfo != null) {
            templatesNo = (Integer) prevCheckpointInfo;
        }

        getLog().info("Start processing " + templates.size() + " templates from index " + templatesNo);
    }


    /** {@inheritDoc} **/
    @Override
    public Object readItem() throws Exception {
        if (templatesNo < templates.size()) {
            getLog().info("Reading template no " + templatesNo);
            return templates.get(templatesNo++);
        }
        return null;
    }


    /** {@inheritDoc} **/
    @Override
    public Serializable checkpointInfo() throws Exception {
        return templatesNo;
    }
}
