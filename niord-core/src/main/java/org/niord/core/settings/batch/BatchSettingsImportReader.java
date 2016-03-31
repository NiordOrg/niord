/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
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
