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

package org.niord.core.promulgation.batch;

import com.fasterxml.jackson.core.type.TypeReference;
import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.promulgation.vo.PromulgationTypeVo;
import org.niord.core.util.JsonUtils;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Named;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.List;

/**
 * Reads promulgation types from a promulgation-types.json file.
 * <p>
 * Please note, the actual promulgation-type-import.xml job file is not placed in the META-INF/batch-jobs of this project,
 * but rather, in the META-INF/batch-jobs folder of the niord-web project.<br>
 * This is because of a class-loading bug in the Wildfly implementation. See e.g.
 * https://issues.jboss.org/browse/WFLY-4988
 * <p>
 * Format of json file is defined by the PromulgationTypeVo class. Example:
 * <pre>
 * [
 *   {
 *     "typeId"    : "navtex",
 *     "serviceId" : "navtex",
 *     "name"      : "NAVTEX",
 *     "priority"  : 1,
 *     "active"    : true,
 *     "language"  : "en",
 *     "domains"   : [
 *        { "domainId" : "niord-client-nw" }
 *     ]
 *   },
 *   {
 *       etc, etc
 *   }
 * ]
 * </pre>
 */
@Dependent
@Named("batchPromulgationTypeImportReader")
public class BatchPromulgationTypeImportReader extends AbstractItemHandler {

    List<PromulgationTypeVo> promulgationTypes;
    int promulgationTypeNo = 0;

    /** {@inheritDoc} **/
    @Override
    public void open(Serializable prevCheckpointInfo) throws Exception {

        // Get hold of the data file
        Path path = batchService.getBatchJobDataFile(jobContext.getInstanceId());

        // Load the templates from the file
        promulgationTypes = JsonUtils.readJson(
                new TypeReference<List<PromulgationTypeVo>>(){},
                path);

        if (prevCheckpointInfo != null) {
            promulgationTypeNo = (Integer) prevCheckpointInfo;
        }

        getLog().info("Start processing " + promulgationTypes.size()
                + " promulgation types from index " + promulgationTypeNo);
    }


    /** {@inheritDoc} **/
    @Override
    public Object readItem() throws Exception {
        if (promulgationTypeNo < promulgationTypes.size()) {
            getLog().info("Reading promulgation type no " + promulgationTypeNo);
            return promulgationTypes.get(promulgationTypeNo++);
        }
        return null;
    }


    /** {@inheritDoc} **/
    @Override
    public Serializable checkpointInfo() throws Exception {
        return promulgationTypeNo;
    }
}
