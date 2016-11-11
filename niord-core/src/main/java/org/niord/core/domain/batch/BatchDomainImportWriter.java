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

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;

/**
 * Persists the domains to the database
 */
@Named
public class BatchDomainImportWriter extends AbstractItemHandler {

    @Inject
    DomainService domainService;


    /** {@inheritDoc} **/
    @Override
    public void writeItems(List<Object> items) throws Exception {
        long t0 = System.currentTimeMillis();
        for (Object i : items) {
            Domain domain = (Domain) i;

            domainService.saveEntity(domain);
        }
        getLog().info(String.format("Persisted %d domains in %d ms", items.size(), System.currentTimeMillis() - t0));
    }
}
