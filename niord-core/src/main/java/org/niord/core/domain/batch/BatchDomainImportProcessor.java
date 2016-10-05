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
package org.niord.core.domain.batch;

import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.domain.Domain;
import org.niord.core.domain.DomainService;
import org.niord.core.domain.vo.DomainVo;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Filters domains that need to be a added or updated
 */
@Named
public class BatchDomainImportProcessor extends AbstractItemHandler {

    @Inject
    DomainService domainService;

    /** {@inheritDoc} **/
    @Override
    public Object processItem(Object item) throws Exception {

        DomainVo domainVo = (DomainVo) item;
        Domain domain = new Domain(domainVo);

        // Look up any existing domain with the same domain ID
        Domain orig = domainService.findByDomainId(domain.getDomainId());

        if (orig == null) {
            // Persist new domain
            getLog().info("Persisting new domain " + domain);
            return domain;

        } else if (orig.hasChanged(domain)) {
            // Update original
            getLog().info("Updating domain " + orig.getDomainId());
            orig.updateDomain(domainVo);
            return orig;
        }

        // No change, ignore...
        getLog().info("Ignoring unchanged domain " + orig.getDomainId());
        return null;
    }
}
