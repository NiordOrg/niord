/*
 * Copyright 2017 Danish Maritime Authority.
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
package org.niord.core.template.batch;

import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.category.CategoryService;
import org.niord.core.domain.DomainService;
import org.niord.core.template.Template;
import org.niord.core.template.TemplateService;
import org.niord.core.template.vo.TemplateVo;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Processes templates that need to be a added or updated
 */
@Named
public class BatchTemplateImportProcessor extends AbstractItemHandler {

    @Inject
    TemplateService templateService;

    @Inject
    CategoryService categoryService;

    @Inject
    DomainService domainService;


    /** {@inheritDoc} **/
    @Override
    public Object processItem(Object item) throws Exception {

        TemplateVo templateVo = (TemplateVo) item;

        Template template = new Template(templateVo);

        // Reset any existing ID - the batch import might be an export from a different system
        template.setId(null);

        // Replace Category and Domain references with persisted entities - do not create or update categories
        template.setCategory(categoryService.importCategory(template.getCategory(), false, false));
        if (template.getCategory() == null) {
            getLog().warning("Invalid category - skipping template import");
            return null;
        }
        template.setDomains(domainService.persistedDomains(template.getDomains()));

        getLog().info("Creating or updating template " + template);
        return templateService.importTemplate(template, true, true);
    }
}
