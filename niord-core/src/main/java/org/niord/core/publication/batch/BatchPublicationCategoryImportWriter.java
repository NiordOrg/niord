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

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;

/**
 * Persists the publication categories to the database
 */
@Named
public class BatchPublicationCategoryImportWriter extends AbstractItemHandler {

    @Inject
    PublicationCategoryService publicationCategoryService;

    /** {@inheritDoc} **/
    @Override
    public void writeItems(List<Object> items) throws Exception {
        long t0 = System.currentTimeMillis();
        for (Object i : items) {
            PublicationCategory publicationTpublicationCategory = (PublicationCategory) i;
            if (publicationTpublicationCategory.isNew()) {
                publicationCategoryService.createPublicationCategory(publicationTpublicationCategory);
            } else {
                publicationCategoryService.saveEntity(publicationTpublicationCategory);
            }
        }
        getLog().info(String.format("Persisted %d publication categories in %d ms", items.size(), System.currentTimeMillis() - t0));
    }
}
