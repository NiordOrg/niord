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

package org.niord.core.script.batch;

import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.script.ScriptResource;
import org.niord.core.script.ScriptResourceService;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.Objects;

/**
 * Persists the resources to the database
 */
@Named
public class BatchScriptResourceImportWriter extends AbstractItemHandler {

    @Inject
    ScriptResourceService resourceService;

    /** {@inheritDoc} **/
    @Override
    public void writeItems(List<Object> items) throws Exception {
        long t0 = System.currentTimeMillis();
        for (Object i : items) {
            ScriptResource template = (ScriptResource) i;

            ScriptResource orig = resourceService.findByPath(template.getPath());

            if (orig == null) {
                getLog().info("Persisting new resource " + template.getPath());
                resourceService.createScriptResource(template);
            } else if (Objects.equals(orig.getContent(), template.getContent())) {
                getLog().info("Ignoring unchanged resource " + template.getPath());
            } else {
                getLog().info("Updating existing resource " + template.getPath());
                orig.setContent(template.getContent());
                resourceService.updateScriptResource(orig);
            }
        }
        getLog().info(String.format("Persisted %d resources in %d ms", items.size(), System.currentTimeMillis() - t0));
    }
}
