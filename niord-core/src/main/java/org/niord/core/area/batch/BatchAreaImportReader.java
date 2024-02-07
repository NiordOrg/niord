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
package org.niord.core.area.batch;

import com.fasterxml.jackson.core.type.TypeReference;
import org.niord.core.area.vo.SystemAreaVo;
import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.util.JsonUtils;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Named;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads areas from a areas.json file.
 * <p>
 * Please note, the actual area-import.xml job file is not placed in the META-INF/batch-jobs of this project,
 * but rather, in the META-INF/batch-jobs folder of the niord-web project.<br>
 * This is because of a class-loading bug in the Wildfly implementation. See e.g.
 * https://issues.jboss.org/browse/WFLY-4988
 * <p>
 * Format of json file is defined by the AreaVo class:
 * <pre>
 * [{
 *     "children": [
 *       {
 *         "descs": [ {
 *             "lang": "da",
 *              "name": "Nykøbing Falster Havn"
 *           },
 *           {
 *             "lang": "en",
 *             "name": "Nykøbing Falster Harbour"
 *           }
 *         ],
 *         "siblingSortOrder": 0.23871272797333265
 *       }
 *     ],
 *     "descs": [
 *       {
 *         "lang": "da",
 *         "name": "Guldborg Sund"
 *       },
 *       {
 *         "lang": "en",
 *         "name": "Guldborg Sund"
 *       }
 *     ],
 *     "sortOrder": 0.0,
 *     "siblingSortOrder": 10.80234803309762
 *   },
 *   ...
 * ]
 * </pre>
 */
@Dependent
@Named("batchAreaImportReader")
public class BatchAreaImportReader extends AbstractItemHandler {

    List<SystemAreaVo> areas;
    int areasNo = 0;

    /** {@inheritDoc} **/
    @Override
    public void open(Serializable prevCheckpointInfo) throws Exception {

        // Get hold of the data file
        Path path = batchService.getBatchJobDataFile(jobContext.getInstanceId());

        // Load the charts from the file
        List<SystemAreaVo> areasRoots = JsonUtils.readJson(
                new TypeReference<List<SystemAreaVo>>(){},
                path);

        // Serialize the list of areas
        areas = new ArrayList<>();
        serializeAreas(null, areasRoots, areas);

        // Remove all non-leaf areas, since leaf areas will generate their parent areas as well
        areas.removeIf(a -> a.getChildren() != null && a.getChildren().size() > 0);

        if (prevCheckpointInfo != null) {
            areasNo = (Integer) prevCheckpointInfo;
        }

        getLog().info("Start processing " + areas.size() + " areas from index " + areasNo);
    }

    /** {@inheritDoc} **/
    @Override
    public Object readItem() throws Exception {
        if (areasNo < areas.size()) {
            getLog().info("Reading area no " + areasNo);
            return areas.get(areasNo++);
        }
        return null;
    }

    /** {@inheritDoc} **/
    @Override
    public Serializable checkpointInfo() throws Exception {
        return areasNo;
    }


    /** Serialize the siblings root hierarchy to a flat list + define the parent field + resets ID fields */
    private void serializeAreas(SystemAreaVo parent, List<SystemAreaVo> siblings, List<SystemAreaVo> areas) {
        if (siblings != null) {
            siblings.forEach(a -> {
                a.setId(null);
                a.setParent(parent);
                areas.add(a);
                serializeAreas(a, a.getChildren(), areas);
            });
        }
    }
}
