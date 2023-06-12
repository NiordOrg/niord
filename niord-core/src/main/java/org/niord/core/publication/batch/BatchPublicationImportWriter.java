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
import org.niord.core.publication.Publication;
import org.niord.core.publication.PublicationService;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;

/**
 * Persists the publications to the database
 */
@Dependent
@Named("batchPublicationImportWriter")
public class BatchPublicationImportWriter extends AbstractItemHandler {

    @Inject
    PublicationService publicationService;

    /** {@inheritDoc} **/
    @Override
    public void writeItems(List<Object> items) throws Exception {
        long t0 = System.currentTimeMillis();
        for (Object i : items) {
            Publication publication = (Publication) i;

            // Look for an existing publication with the same name
            Publication orig = publicationService.findByPublicationId(publication.getPublicationId());

            if (orig == null) {
                // Persist new publication
                getLog().info("Persisting new publication " + publication.getPublicationId());
                publicationService.createPublication(publication);
            } else {
                // Update original publication
                getLog().info("Updating publication " + orig.getPublicationId());
                publicationService.updatePublication(publication);
            }
        }
        getLog().info(String.format("Persisted %d publications in %d ms", items.size(), System.currentTimeMillis() - t0));
    }
}
