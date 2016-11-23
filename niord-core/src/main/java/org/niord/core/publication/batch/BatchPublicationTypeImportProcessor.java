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
import org.niord.core.publication.PublicationType;
import org.niord.core.publication.PublicationTypeService;
import org.niord.model.publication.PublicationTypeVo;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Filters publication types that need to be a added or updated
 */
@Named
public class BatchPublicationTypeImportProcessor extends AbstractItemHandler {

    @Inject
    PublicationTypeService publicationTypeService;


    /** {@inheritDoc} **/
    @Override
    public Object processItem(Object item) throws Exception {

        PublicationTypeVo publicationTypeVo = (PublicationTypeVo) item;

        PublicationType publicationType = new PublicationType(publicationTypeVo);

        // Look for an existing publication with the same name
        PublicationType orig = publicationTypeService.findByTypeId(publicationType.getTypeId());

        if (orig == null) {
            // Persist new publication
            getLog().info("Persisting new publication type " + publicationType);
            return publicationType;

        } else {
            // Update original publication
            getLog().info("Updating publication type " + orig.getId());
            orig.updatePublicationType(publicationType);
            return orig;
        }
    }
}
