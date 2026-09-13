/*
 * Copyright 2026 Danish Emergency Management Agency.
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

package org.niord.web.publication;

import org.junit.jupiter.api.Test;
import org.niord.core.publication.series.PublicationSeries;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The series export is ordered so that an import of it cannot lose a row.
 *
 * The importer resolves a compilation's source by seriesId against what the
 * installation already holds and drops the row when it finds nothing -- so the
 * order the file is written in decides whether a compilation survives the
 * journey. The estate's own ids make the default order the losing one:
 * "accumulated-yearly-ntm" sorts before "weekly-ntm", so an alphabetical export
 * lists the annual first and an import of it lands every series except that one,
 * with a batch log line as the only trace.
 *
 * No database and no server: the ordering is a function of a list, and this
 * pins it as one.
 */
public class SeriesExportOrderTest {

    private static PublicationSeries series(String seriesId, PublicationSeries source) {
        PublicationSeries s = new PublicationSeries();
        s.setSeriesId(seriesId);
        s.setSourceSeries(source);
        return s;
    }

    private static List<String> idsOf(List<PublicationSeries> series) {
        List<String> out = new ArrayList<>();
        series.forEach(s -> out.add(s.getSeriesId()));
        return out;
    }

    @Test
    public void aCompilationIsExportedAfterTheSeriesItCompiles() {
        PublicationSeries weekly = series("weekly-ntm", null);
        PublicationSeries annual = series("accumulated-yearly-ntm", weekly);

        List<String> exported = idsOf(PublicationSeriesRestService.sourcesBeforeCompilations(
                List.of(annual, weekly)));

        assertTrue(exported.indexOf("weekly-ntm") < exported.indexOf("accumulated-yearly-ntm"),
                "the compilation was exported before its source, and an import of that file "
                        + "drops it: " + exported);
    }

    @Test
    public void everySeriesIsStillExportedExactlyOnce() {
        PublicationSeries weekly = series("weekly-ntm", null);
        PublicationSeries annual = series("accumulated-yearly-ntm", weekly);
        PublicationSeries firing = series("firing-practice-areas", null);

        List<String> exported = idsOf(PublicationSeriesRestService.sourcesBeforeCompilations(
                List.of(annual, firing, weekly)));

        assertEquals(3, exported.size(), "the reorder lost or duplicated a series: " + exported);
        assertTrue(exported.containsAll(
                        List.of("weekly-ntm", "accumulated-yearly-ntm", "firing-practice-areas")),
                "the reorder lost a series: " + exported);
    }

    /**
     * An estate with no compilation exports exactly as it always did.
     *
     * The order of the file is the order an operator diffs two exports in, so a
     * reorder that touched every installation would be a change nobody asked for.
     */
    @Test
    public void anEstateWithoutCompilationsKeepsTheOrderItHad() {
        List<PublicationSeries> given = List.of(
                series("aids-to-navigation", null),
                series("weekly-ntm", null),
                series("firing-practice-areas", null));

        assertEquals(idsOf(given),
                idsOf(PublicationSeriesRestService.sourcesBeforeCompilations(given)),
                "the export order changed for an estate that carries no compilation at all");
    }
}
