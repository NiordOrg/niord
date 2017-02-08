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

package org.niord.core.fm.batch;

import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.fm.FmTemplate;
import org.niord.core.fm.FmTemplateService;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.Objects;

/**
 * Persists the templates to the database
 */
@Named
public class BatchFmTemplateImportWriter extends AbstractItemHandler {

    @Inject
    FmTemplateService templateService;

    /** {@inheritDoc} **/
    @Override
    public void writeItems(List<Object> items) throws Exception {
        long t0 = System.currentTimeMillis();
        for (Object i : items) {
            FmTemplate template = (FmTemplate) i;

            FmTemplate orig = templateService.findByPath(template.getPath());

            if (orig == null) {
                getLog().info("Persisting new template " + template.getPath());
                templateService.createTemplate(template);
            } else if (Objects.equals(orig.getTemplate(), template.getTemplate())) {
                getLog().info("Ignoring unchanged template " + template.getPath());
            } else {
                getLog().info("Updating existing template " + template.getPath());
                orig.setTemplate(template.getTemplate());
                templateService.updateTemplate(orig);
            }
        }
        getLog().info(String.format("Persisted %d templates in %d ms", items.size(), System.currentTimeMillis() - t0));
    }
}
