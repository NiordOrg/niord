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
import org.niord.core.source.vo.SourceVo;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Objects;

/**
 * Filters sources that need to be a added or updated
 */
@Named
public class BatchSourceImportProcessor extends AbstractItemHandler {

    @Inject
    SourceService sourceService;

    /** {@inheritDoc} **/
    @Override
    public Object processItem(Object item) throws Exception {

        SourceVo sourceVo = (SourceVo) item;
        // Reset the ID of the source
        sourceVo.setId(null);

        Source source = new Source(sourceVo);

        // Look for an existing source with the same name
        Source orig = source.getDescs().stream()
                .map(d -> sourceService.findByName(d.getLang(), d.getName()))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        if (orig == null) {
            // Persist new source
            getLog().info("Persisting new source " + source);
            return source;

        } else {
            // Update original source
            getLog().info("Updating source " + orig.getId());
            orig.updateSource(source);
            return orig;
        }
    }
}
