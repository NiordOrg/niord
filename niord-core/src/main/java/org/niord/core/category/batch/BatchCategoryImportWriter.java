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
package org.niord.core.category.batch;

import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.category.Category;
import org.niord.core.category.CategoryService;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.transaction.Transactional;
import java.util.List;

/**
 * Persists the categories to the database
 */
@Dependent
@Named("batchCategoryImportWriter")
public class BatchCategoryImportWriter extends AbstractItemHandler {

    @Inject
    CategoryService categoryService;

    /** {@inheritDoc} **/
    @Override
    @Transactional
    public void writeItems(List<Object> items) throws Exception {
        long t0 = System.currentTimeMillis();
        for (Object i : items) {
            Category category = (Category) i;
            categoryService.saveEntity(category);
        }
        getLog().info(String.format("Persisted %d categories in %d ms", items.size(), System.currentTimeMillis() - t0));
    }
}
