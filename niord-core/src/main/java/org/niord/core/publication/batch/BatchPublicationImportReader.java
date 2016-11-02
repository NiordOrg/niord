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

package org.niord.core.publication.batch;

import com.fasterxml.jackson.core.type.TypeReference;
import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.publication.vo.PublicationVo;
import org.niord.core.util.JsonUtils;

import javax.inject.Named;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.List;

/**
 * Reads publications from a publication.json file.
 * <p>
 * Please note, the actual publication-import.xml job file is not placed in the META-INF/batch-jobs of this project,
 * but rather, in the META-INF/batch-jobs folder of the niord-web project.<br>
 * This is because of a class-loading bug in the Wildfly implementation. See e.g.
 * https://issues.jboss.org/browse/WFLY-4988
 * <p>
 * Format of json file is defined by the PublicationVo class (with ID's being ignored). Example:
 * <pre>
 * [
 *   {
 *     "id": 105282,
 *     "active": true,
 *     "internal": false,
 *     "descs": [
 *       {
 *         "name": "Den danske Havnelods",
 *         "format": "<a href=\"www.danskehavnelods.dk\" target=\"_blank\">www.danskehavnelods.dk</a>",
 *         "lang": "da"
 *       },
 *       {
 *         "name": "The Danish Habour Pilot",
 *         "format": "<a href=\"www.danskehavnelods.dk\" target=\"_blank\">www.danskehavnelods.dk</a>",
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
@Named
public class BatchPublicationImportReader extends AbstractItemHandler {

    List<PublicationVo> publications;
    int publicationNo = 0;

    /** {@inheritDoc} **/
    @Override
    public void open(Serializable prevCheckpointInfo) throws Exception {

        // Get hold of the data file
        Path path = batchService.getBatchJobDataFile(jobContext.getInstanceId());

        // Load the publications from the file
        publications = JsonUtils.readJson(
                new TypeReference<List<PublicationVo>>(){},
                path);

        if (prevCheckpointInfo != null) {
            publicationNo = (Integer) prevCheckpointInfo;
        }

        getLog().info("Start processing " + publications.size() + " publications from index " + publicationNo);
    }

    /** {@inheritDoc} **/
    @Override
    public Object readItem() throws Exception {
        if (publicationNo < publications.size()) {
            getLog().info("Reading publication no " + publicationNo);
            return publications.get(publicationNo++);
        }
        return null;
    }

    /** {@inheritDoc} **/
    @Override
    public Serializable checkpointInfo() throws Exception {
        return publicationNo;
    }
}
