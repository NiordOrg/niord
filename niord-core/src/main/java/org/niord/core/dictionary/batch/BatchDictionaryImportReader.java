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
package org.niord.core.dictionary.batch;

import com.fasterxml.jackson.core.type.TypeReference;
import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.chart.vo.SystemChartVo;
import org.niord.core.dictionary.vo.ExportedDictionaryVo;
import org.niord.core.util.JsonUtils;

import javax.enterprise.context.Dependent;
import javax.inject.Named;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.List;

/**
 * Reads dictionaries from a dictionaries.json file.
 * <p>
 * Please note, the actual dictionary-import.xml job file is not placed in the META-INF/batch-jobs of this project,
 * but rather, in the META-INF/batch-jobs folder of the niord-web project.<br>
 * This is because of a class-loading bug in the Wildfly implementation. See e.g.
 * https://issues.jboss.org/browse/WFLY-4988
 * <p>
 * Format of json file is defined by the ExportedDictionaryVo class. Example:
 * <pre>
 * [
 *   {
 *     "name" : "template",
 *     "entries" : [
 *        {
 *          "key" : "position.east",
 *          "descs" : [ { "lang" : "da", "value" : "øst" } , { "lang" : "en" , "value" : "East" } ]
 *        },
 *        { ... }
 *     ]
 *   },
 *   {
 *       etc, etc
 *   }
 * ]
 * </pre>
 */
@Dependent
@Named("batchDictionaryImportReader")
public class BatchDictionaryImportReader extends AbstractItemHandler {

    List<ExportedDictionaryVo> dictionaries;
    int dictNo = 0;

    /** {@inheritDoc} **/
    @Override
    public void open(Serializable prevCheckpointInfo) throws Exception {

        // Get hold of the data file
        Path path = batchService.getBatchJobDataFile(jobContext.getInstanceId());

        // Load the dictionaries from the file
        dictionaries = JsonUtils.readJson(
                new TypeReference<List<ExportedDictionaryVo>>(){},
                path);

        if (prevCheckpointInfo != null) {
            dictNo = (Integer) prevCheckpointInfo;
        }

        getLog().info("Start processing " + dictionaries.size() + " dictionaries from index " + dictNo);
    }

    /** {@inheritDoc} **/
    @Override
    public Object readItem() throws Exception {
        if (dictNo < dictionaries.size()) {
            getLog().info("Reading dictionary no " + dictNo);
            return dictionaries.get(dictNo++);
        }
        return null;
    }

    /** {@inheritDoc} **/
    @Override
    public Serializable checkpointInfo() throws Exception {
        return dictNo;
    }
}
