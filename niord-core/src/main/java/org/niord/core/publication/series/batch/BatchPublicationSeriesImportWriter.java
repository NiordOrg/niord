/*
 * Copyright 2026 Danish Maritime Authority.
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

package org.niord.core.publication.series.batch;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.transaction.Transactional;

import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.publication.series.PublicationSeries;
import org.niord.core.publication.series.PublicationSeriesService;

import java.util.List;

/** Persists the imported series. New ones are created, existing ones updated in place. */
@Dependent
@Named("batchPublicationSeriesImportWriter")
public class BatchPublicationSeriesImportWriter extends AbstractItemHandler {

    @Inject
    PublicationSeriesService seriesService;

    @Transactional
    @Override
    public void writeItems(List<Object> items) {
        long t0 = System.currentTimeMillis();
        for (Object item : items) {
            PublicationSeries series = (PublicationSeries) item;
            if (series.isNew()) {
                seriesService.create(series);
            } else {
                seriesService.update(series);
            }
        }
        getLog().info(String.format("Persisted %d publication series in %d ms",
                items.size(), System.currentTimeMillis() - t0));
    }
}
