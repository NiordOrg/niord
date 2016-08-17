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
package org.niord.core.settings.batch;

import com.fasterxml.jackson.core.type.TypeReference;
import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.settings.Setting;
import org.niord.core.util.JsonUtils;

import javax.inject.Named;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.List;

/**
 * Reads settings from a settings.json file.
 * <p>
 * Please note, the actual settings-import.xml job file is not placed in the META-INF/batch-jobs of this project,
 * but rather, in the META-INF/batch-jobs folder of the niord-web project.<br>
 * This is because of a class-loading bug in the Wildfly implementation. See e.g.
 * https://issues.jboss.org/browse/WFLY-4988
 * <p>
 * Format of json file is defined by the Setting class. Example:
 * <pre>
 * [
 *    {
 *    "key"         : "baseUri",
 *    "description" : "The base application server URI",
 *    "value"       : "http://localhost:8080",
 *    "web"         : false,
 *    "editable"    : true
 *    },
 *    {
 *    "key"         : "modelLanguages",
 *    "description" : "The default message model languages",
 *    "value"       : [ "da", "en" ],
 *    "type"        : "json",
 *    "web"         : true,
 *    "editable"    : true
 *    },
 *    ...
 * ]
 * </pre>
 */
@Named
public class BatchSettingsImportReader extends AbstractItemHandler {

    List<Setting> settings;
    int settingNo = 0;

    /** {@inheritDoc} **/
    @Override
    public void open(Serializable prevCheckpointInfo) throws Exception {

        // Get hold of the data file
        Path path = batchService.getBatchJobDataFile(jobContext.getInstanceId());

        // Load the charts from the file
        settings = JsonUtils.readJson(
                new TypeReference<List<Setting>>(){},
                path);

        if (prevCheckpointInfo != null) {
            settingNo = (Integer) prevCheckpointInfo;
        }

        getLog().info("Start processing " + settings.size() + " settings from index " + settingNo);
    }

    /** {@inheritDoc} **/
    @Override
    public Object readItem() throws Exception {
        if (settingNo < settings.size()) {

            // Every now and then, update the progress
            if (settingNo % 10 == 0) {
                updateProgress((int)(100.0 * settingNo / settings.size()));
            }

            getLog().info("Reading setting no " + settingNo);
            return settings.get(settingNo++);
        }
        return null;
    }

    /** {@inheritDoc} **/
    @Override
    public Serializable checkpointInfo() throws Exception {
        return settingNo;
    }
}
