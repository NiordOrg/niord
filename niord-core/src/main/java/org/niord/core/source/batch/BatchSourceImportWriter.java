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

package org.niord.core.source.batch;

import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.source.Source;
import org.niord.core.source.SourceService;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;

/**
 * Persists the sources to the database
 */
@Named
public class BatchSourceImportWriter extends AbstractItemHandler {

    @Inject
    SourceService sourceService;

    /** {@inheritDoc} **/
    @Override
    public void writeItems(List<Object> items) throws Exception {
        long t0 = System.currentTimeMillis();
        for (Object i : items) {
            Source source = (Source) i;
            sourceService.saveEntity(source);
        }
        getLog().info(String.format("Persisted %d sources in %d ms", items.size(), System.currentTimeMillis() - t0));
    }
}
