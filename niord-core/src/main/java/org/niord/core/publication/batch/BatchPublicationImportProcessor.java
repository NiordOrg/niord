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
import org.niord.core.publication.PublicationCategoryService;
import org.niord.core.publication.PublicationService;
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
    PublicationCategoryService publicationCategoryService;

    @Inject
    DomainService domainService;

    /** {@inheritDoc} **/
    @Override
    public Object processItem(Object item) throws Exception {

        SystemPublicationVo publicationVo = (SystemPublicationVo) item;

        return new Publication(publicationVo);
    }
}
