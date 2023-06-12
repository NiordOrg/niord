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

package org.niord.core.source.batch;

import com.fasterxml.jackson.core.type.TypeReference;
import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.source.vo.SourceVo;
import org.niord.core.util.JsonUtils;

import javax.enterprise.context.Dependent;
import javax.inject.Named;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.List;

/**
 * Reads sources from a source.json file.
 * <p>
 * Please note, the actual source-import.xml job file is not placed in the META-INF/batch-jobs of this project,
 * but rather, in the META-INF/batch-jobs folder of the niord-web project.<br>
 * This is because of a class-loading bug in the Wildfly implementation. See e.g.
 * https://issues.jboss.org/browse/WFLY-4988
 * <p>
 * Format of json file is defined by the SourceVo class (with ID's being ignored). Example:
 * <pre>
 * [
 *   {
 *     "active": true,
 *     "descs": [
 *       {
 *         "name": "Arktisk Kommando",
 *         "abbreviation": "AKO",
 *         "lang": "da"
 *       },
 *       {
 *         "name": "Joint Arctic Command",
 *         "abbreviation": "JACMD",
 *         "lang": "en"
 *       }
 *     ]
 *   },
 *   {
 *       etc, etc
 *   }
 * ]
 * </pre>
 */
@Dependent
@Named("batchSourceImportReader")
public class BatchSourceImportReader extends AbstractItemHandler {

    List<SourceVo> sources;
    int sourceNo = 0;

    /** {@inheritDoc} **/
    @Override
    public void open(Serializable prevCheckpointInfo) throws Exception {

        // Get hold of the data file
        Path path = batchService.getBatchJobDataFile(jobContext.getInstanceId());

        // Load the sources from the file
        sources = JsonUtils.readJson(
                new TypeReference<List<SourceVo>>(){},
                path);

        if (prevCheckpointInfo != null) {
            sourceNo = (Integer) prevCheckpointInfo;
        }

        getLog().info("Start processing " + sources.size() + " sources from index " + sourceNo);
    }

    /** {@inheritDoc} **/
    @Override
    public Object readItem() throws Exception {
        if (sourceNo < sources.size()) {
            getLog().info("Reading source no " + sourceNo);
            return sources.get(sourceNo++);
        }
        return null;
    }

    /** {@inheritDoc} **/
    @Override
    public Serializable checkpointInfo() throws Exception {
        return sourceNo;
    }
}
