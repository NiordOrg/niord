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
import org.niord.core.domain.DomainService;
import org.niord.core.publication.Publication;
import org.niord.core.publication.PublicationService;
import org.niord.core.publication.PublicationTypeService;
import org.niord.core.publication.vo.SystemPublicationVo;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Filters publications that need to be a added or updated
 */
@Named
public class BatchPublicationImportProcessor extends AbstractItemHandler {

    @Inject
    PublicationService publicationService;

    @Inject
    PublicationTypeService publicationTypeService;

    @Inject
    DomainService domainService;

    /** {@inheritDoc} **/
    @Override
    public Object processItem(Object item) throws Exception {

        SystemPublicationVo publicationVo = (SystemPublicationVo) item;

        Publication publication = new Publication(publicationVo);

        // Find or create the associated publication type
        publication.setType(publicationTypeService.findOrCreatePublicationType(publication.getType()));

        // Substitute the domain with the persisted on
        if (publication.getDomain() != null) {
            publication.setDomain(domainService.findByDomainId(publication.getDomain().getDomainId()));
        }

        // Look for an existing publication with the same name
        Publication orig = publicationService.findByPublicationId(publication.getPublicationId());

        if (orig == null) {
            // Persist new publication
            getLog().info("Persisting new publication " + publication);
            return publication;

        } else {
            // Update original publication
            getLog().info("Updating publication " + orig.getId());
            orig.updatePublication(publication);
            return orig;
        }
    }
}
