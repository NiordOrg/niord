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
package org.niord.core.area.batch;

import com.fasterxml.jackson.core.type.TypeReference;
import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.util.JsonUtils;
import org.niord.model.message.AreaVo;

import javax.inject.Named;
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
@Named
public class BatchAreaImportReader extends AbstractItemHandler {

    List<AreaVo> areas;
    int areasNo = 0;

    /** {@inheritDoc} **/
    @Override
    public void open(Serializable prevCheckpointInfo) throws Exception {

        // Get hold of the data file
        Path path = batchService.getBatchJobDataFile(jobContext.getInstanceId());

        // Load the charts from the file
        List<AreaVo> areasRoots = JsonUtils.readJson(
                new TypeReference<List<AreaVo>>(){},
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
    private void serializeAreas(AreaVo parent, List<AreaVo> siblings, List<AreaVo> areas) {
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
