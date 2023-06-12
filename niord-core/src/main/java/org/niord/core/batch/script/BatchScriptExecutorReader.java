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
package org.niord.core.batch.script;

import org.apache.commons.io.FileUtils;
import org.niord.core.batch.AbstractItemHandler;

import javax.enterprise.context.Dependent;
import javax.inject.Named;
import java.io.Serializable;
import java.nio.file.Path;

/**
 * Reads and executes a javascript from the batch-job file.
 * <p>
 * Please note, the actual settings-import.xml job file is not placed in the META-INF/batch-jobs of this project,
 * but rather, in the META-INF/batch-jobs folder of the niord-web project.<br>
 * This is because of a class-loading bug in the Wildfly implementation. See e.g.
 * https://issues.jboss.org/browse/WFLY-4988
 * <p>
 * Format of the script file is javascript. Example:
 * <pre>
 *     print("GED!");
 * </pre>
 */
@Dependent
@Named("batchScriptExecutorReader")
public class BatchScriptExecutorReader extends AbstractItemHandler {

    String script;
    int scriptNo = 0;

    /** {@inheritDoc} **/
    @Override
    public void open(Serializable prevCheckpointInfo) throws Exception {

        // Get hold of the data file
        Path path = batchService.getBatchJobDataFile(jobContext.getInstanceId());

        // Load the script
        script = FileUtils.readFileToString(path.toFile());

        if (prevCheckpointInfo != null) {
            scriptNo = (Integer) prevCheckpointInfo;
        }

        getLog().info("Start processing script from index " + scriptNo);
    }

    /** {@inheritDoc} **/
    @Override
    public Object readItem() throws Exception {
        if (scriptNo == 0) {

            updateProgress(100);
            scriptNo++;

            return script;
        }
        return null;
    }

    /** {@inheritDoc} **/
    @Override
    public Serializable checkpointInfo() throws Exception {
        return scriptNo;
    }
}
