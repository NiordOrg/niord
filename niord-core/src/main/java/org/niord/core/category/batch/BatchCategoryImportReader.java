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
package org.niord.core.category.batch;

import com.fasterxml.jackson.core.type.TypeReference;
import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.util.JsonUtils;
import org.niord.model.message.CategoryVo;

import javax.inject.Named;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads categories from a categories.json file.
 * <p>
 * Please note, the actual category-import.xml job file is not placed in the META-INF/batch-jobs of this project,
 * but rather, in the META-INF/batch-jobs folder of the niord-web project.<br>
 * This is because of a class-loading bug in the Wildfly implementation. See e.g.
 * https://issues.jboss.org/browse/WFLY-4988
 * <p>
 * Format of json file is defined by the CategoryVo class:
 * <pre>
 * [{
 *     "children": [
 *       {
 *         "descs": [ {
 *             "lang": "da",
 *              "name": "Kabelarbejde"
 *           },
 *           {
 *             "lang": "en",
 *             "name": "Cable operations"
 *           }
 *         ]
 *       },
 *       ...
 *     ],
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
public class BatchCategoryImportReader extends AbstractItemHandler {

    List<CategoryVo> categories;
    int categoriesNo = 0;

    /** {@inheritDoc} **/
    @Override
    public void open(Serializable prevCheckpointInfo) throws Exception {

        // Get hold of the data file
        Path path = batchService.getBatchJobDataFile(jobContext.getInstanceId());

        // Load the charts from the file
        List<CategoryVo> categoriesRoots = JsonUtils.readJson(
                new TypeReference<List<CategoryVo>>(){},
                path);

        // Serialize the list of categories
        categories = new ArrayList<>();
        serializeCategories(null, categoriesRoots, categories);

        // Remove all non-leaf categories, since leaf categories will generate their parent categories as well
        categories.removeIf(c -> c.getChildren() != null && c.getChildren().size() > 0);

        if (prevCheckpointInfo != null) {
            categoriesNo = (Integer) prevCheckpointInfo;
        }

        getLog().info("Start processing " + categories.size() + " categories from index " + categoriesNo);
    }

    /** {@inheritDoc} **/
    @Override
    public Object readItem() throws Exception {
        if (categoriesNo < categories.size()) {
            getLog().info("Reading category no " + categoriesNo);
            return categories.get(categoriesNo++);
        }
        return null;
    }

    /** {@inheritDoc} **/
    @Override
    public Serializable checkpointInfo() throws Exception {
        return categoriesNo;
    }


    /** Serialize the siblings root hierarchy to a flat list + define the parent field + resets ID fields */
    private void serializeCategories(CategoryVo parent, List<CategoryVo> siblings, List<CategoryVo> categories) {
        if (siblings != null) {
            siblings.forEach(a -> {
                a.setId(null);
                a.setParent(parent);
                categories.add(a);
                serializeCategories(a, a.getChildren(), categories);
            });
        }
    }
}
