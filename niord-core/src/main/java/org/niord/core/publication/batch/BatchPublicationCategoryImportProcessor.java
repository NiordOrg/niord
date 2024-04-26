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

import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.PublicationCategoryService;
import org.niord.model.publication.PublicationCategoryVo;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * Filters publication categories that need to be a added or updated
 */
@Dependent
@Named("batchPublicationCategoryImportProcessor")
public class BatchPublicationCategoryImportProcessor extends AbstractItemHandler {

    @Inject
    PublicationCategoryService publicationCategoryService;


    /** {@inheritDoc} **/
    @Override
    public Object processItem(Object item) throws Exception {

        PublicationCategoryVo publicationCategoryVo = (PublicationCategoryVo) item;

        PublicationCategory publicationCategory = new PublicationCategory(publicationCategoryVo);

        // Look for an existing publication with the same name
        PublicationCategory orig = publicationCategoryService.findByCategoryId(publicationCategory.getCategoryId());

        if (orig == null) {
            // Persist new publication
            getLog().info("Persisting new publication category " + publicationCategory);
            return publicationCategory;

        } else {
            // Update original publication
            getLog().info("Updating publication category " + orig.getId());
            orig.updatePublicationCategory(publicationCategory);
            return orig;
        }
    }
}
