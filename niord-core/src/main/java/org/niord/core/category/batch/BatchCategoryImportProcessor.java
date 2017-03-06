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
import org.niord.core.category.vo.SystemCategoryVo;
import org.niord.model.DataFilter;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Filters categories that need to be a added or updated
 */
@Named
public class BatchCategoryImportProcessor extends AbstractItemHandler {

    @Inject
    CategoryService categoryService;

    /** {@inheritDoc} **/
    @Override
    public Object processItem(Object item) throws Exception {

        SystemCategoryVo categoryVo = (SystemCategoryVo) item;

        DataFilter filter = DataFilter.get().fields("parent");
        Category category = new Category(categoryVo, filter);

        getLog().info("Creating or updating category " + category);
        return categoryService.importCategory(category, true, true);
    }
}
