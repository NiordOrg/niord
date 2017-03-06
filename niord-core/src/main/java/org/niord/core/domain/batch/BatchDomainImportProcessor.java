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

import org.niord.core.area.Area;
import org.niord.core.area.AreaService;
import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.category.Category;
import org.niord.core.category.CategoryService;
import org.niord.core.domain.Domain;
import org.niord.core.domain.DomainService;
import org.niord.core.domain.vo.DomainVo;
import org.niord.core.message.MessageSeries;
import org.niord.core.message.MessageSeriesService;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Filters domains that need to be a added or updated
 */
@Named
public class BatchDomainImportProcessor extends AbstractItemHandler {

    @Inject
    DomainService domainService;

    @Inject
    AreaService areaService;

    @Inject
    CategoryService categoryService;

    @Inject
    MessageSeriesService messageSeriesService;

    /** {@inheritDoc} **/
    @Override
    public Object processItem(Object item) throws Exception {

        DomainVo domainVo = (DomainVo) item;
        Domain domain = new Domain(domainVo);

        // Look up any existing domain with the same domain ID
        Domain orig = domainService.findByDomainId(domain.getDomainId());

        if (orig == null) {
            // Make sure areas are resolved and/or created
            List<Area> areas = domain.getAreas().stream()
                    .map(a -> areaService.importArea(a, true, false))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            domain.setAreas(areas);

            // Make sure categories are resolved and/or created
            List<Category> categories = domain.getCategories().stream()
                    .map(c -> categoryService.importCategory(c, true, false))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            domain.setCategories(categories);


            // Make sure message series are resolved and/or created
            List<MessageSeries> series = domain.getMessageSeries().stream()
                    .map(s -> messageSeriesService.findOrCreateMessageSeries(s))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            domain.setMessageSeries(series);


            // Persist new domain
            getLog().info("Persisting new domain " + domain);
            return domain;

        } else {

            // Update original
            getLog().info("Ignoring existing domain " + orig.getDomainId());
            return null;
        }
    }
}
