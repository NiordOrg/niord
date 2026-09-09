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

package org.niord.core.publication.series.legacy;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.publication.series.ContentMode;
import org.niord.core.publication.series.PublicationSeries;
import org.niord.core.publication.series.PublicationSeriesDesc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A SHARED series is named by ruling, and the year of its newest edition lives in
 * the pattern rather than in the name.
 *
 * THE ROLES INVERT WITHOUT A TEMPLATE. A legacy template separates a series' name
 * from the pattern its issues are named by -- "Weekly NtM" beside "NtM Week
 * ${week} - ${year}" -- so copying both verbatim is right for the five
 * template-derived series. An orphan has no titleFormat, and its title names ONE
 * EDITION: the verbatim copy put "for vinteren 2026" and "2022" into two series
 * NAMES and left the pattern that should carry the year empty.
 *
 * IT SPREADS. The next issue's suggested name falls back through the pattern to
 * the SERIES name, so an admin creating the next ice-service annex was handed one
 * named for the winter of 2026, and a file called Isbilag-2026.pdf to put it in.
 *
 * AND THE LINK IS WORSE THAN THE NAME. An uploaded publication's legacy link
 * addresses one issue's repository folder, revision and file name -- as a series
 * pattern it can only ever point at the wrong edition.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class SharedOrphanNamingTest {

    private static final String ICE = "nm-annex-ice-service";
    private static final String LIGHTS = "danish-list-of-lights";
    private static final String NCAGS = "nm-annex-ncags";

    private static final String ICE_NAME =
            "Meddelelse fra Marinestaben om istjeneste samt om ismeldinger m.m.";

    @Inject
    LegacyImportService importService;

    private static LegacyImportService.Plan plan;

    private LegacyImportService.Plan plan() {
        if (plan == null) {
            plan = importService.planFrom(LegacyEstateFixture.templates(),
                    LegacyEstateFixture.publications());
        }
        return plan;
    }

    /** The ice-service annex: the ruled name, and the winter in the pattern. */
    @Test
    public void theIceServiceAnnexIsNamedByRulingAndSuggestsTheYear() {
        PublicationSeries series = series(ICE);

        assertEquals(ICE_NAME, desc(series, "da").getName(),
                "the Danish name still carries an edition's year");
        assertEquals(ICE_NAME + " (Danish Only)", desc(series, "en").getName(),
                "the English row is the Danish text with the language note, and nothing else");

        assertEquals(ICE_NAME + " for vinteren ${year}",
                desc(series, "da").getNameSuggestionPattern(),
                "the year belongs in the pattern, where the next issue renders it");
        assertEquals(ICE_NAME + " for vinteren ${year} (Danish Only)",
                desc(series, "en").getNameSuggestionPattern());

        for (String lang : List.of("da", "en")) {
            assertEquals("Isbilag-${year}.pdf", desc(series, lang).getFileNamePattern(),
                    "the file name was frozen at one edition's year too");
        }
    }

    /** The list of lights: the same defect, and a name that only had to lose a year. */
    @Test
    public void theListOfLightsIsNamedByRulingAndSuggestsTheYear() {
        PublicationSeries series = series(LIGHTS);

        assertEquals("Dansk Fyrliste", desc(series, "da").getName());
        assertEquals("Danish List of Lights", desc(series, "en").getName());

        assertEquals("Dansk Fyrliste ${year}", desc(series, "da").getNameSuggestionPattern());
        assertEquals("Danish List of Lights ${year}", desc(series, "en").getNameSuggestionPattern());
    }

    /**
     * NCAGS keeps its name and gets no pattern, because its editions never carried
     * a year to move.
     *
     * The control for the year substitution: every NCAGS edition is titled
     * "NCAGS", so there is nothing to parameterise, and authoring a pattern equal
     * to the title would claim a naming rule nobody stated.
     */
    @Test
    public void ncagsHasNoYearToMoveAndGetsNoPattern() {
        PublicationSeries series = series(NCAGS);

        for (String lang : List.of("da", "en")) {
            assertEquals("NCAGS", desc(series, lang).getName());
            assertNull(desc(series, lang).getNameSuggestionPattern(),
                    "a title with no year states no pattern");
        }
    }

    /**
     * No shared series carries a link into one of its own issues' folders.
     *
     * Kept only where the publication IS a link, which on this estate is the list
     * of lights alone -- and that one still points at a particular year's PDF,
     * which is a ruling waiting to be made rather than something the importer can
     * work out.
     */
    @Test
    public void onlyALinkBackedSharedSeriesKeepsALinkPattern() {
        for (String seriesId : List.of(ICE, LIGHTS, NCAGS)) {
            PublicationSeries series = series(seriesId);
            boolean link = series.getContentMode() == ContentMode.EXTERNAL_LINK;
            for (PublicationSeriesDesc d : series.getDescs()) {
                if (link) {
                    assertNotNull(d.getLinkPattern(),
                            seriesId + " is a link publication and must keep its address");
                } else {
                    assertNull(d.getLinkPattern(),
                            seriesId + "/" + d.getLang() + " carries " + d.getLinkPattern()
                                    + " -- one issue's repository folder, as the series' pattern");
                }
            }
        }
        assertEquals(ContentMode.EXTERNAL_LINK, series(LIGHTS).getContentMode(),
                "the list of lights is the estate's only link-backed shared series");
        assertEquals(ContentMode.UPLOADED_FILE, series(ICE).getContentMode());
    }

    /**
     * No imported series' NAME carries a four-digit year.
     *
     * The sweep behind the two rulings: the template-derived series were always
     * clean, and these two were the only orphans whose newest edition put a year
     * in the name. A third would be a new ruling, and this is what asks for it.
     */
    @Test
    public void noImportedSeriesIsNamedForAYear() {
        List<String> offenders = new java.util.ArrayList<>();
        for (PublicationSeries series : plan().series()) {
            for (PublicationSeriesDesc d : series.getDescs()) {
                if (d.getName() != null && d.getName().matches(".*(?<!\\d)(19|20)\\d{2}(?!\\d).*")) {
                    offenders.add(series.getSeriesId() + "/" + d.getLang() + ": " + d.getName());
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "a series named for one of its editions' years will be wrong the moment the next "
                        + "edition arrives: " + offenders);
    }

    /**
     * The configuration source is still the newest member, and that is now only
     * about the SETTINGS.
     *
     * The pick was never wrong -- a series' settings are what its next issue will
     * follow. What was wrong is that the name came with them.
     */
    @Test
    public void theRuledNameDoesNotDependOnWhichEditionConfiguredTheSeries() {
        PublicationSeries ice = series(ICE);
        assertFalse(ICE_NAME.contains("2026"),
                "the ruled name must not be derived from the edition that happens to be newest");
        assertEquals(ICE_NAME, desc(ice, "da").getName());
    }

    // ------------------------------------------------------------------ helpers

    private PublicationSeries series(String seriesId) {
        for (PublicationSeries s : plan().series()) {
            if (seriesId.equals(s.getSeriesId())) {
                return s;
            }
        }
        throw new IllegalStateException(seriesId + " is not in the plan");
    }

    private static PublicationSeriesDesc desc(PublicationSeries series, String lang) {
        for (PublicationSeriesDesc d : series.getDescs()) {
            if (lang.equals(d.getLang())) {
                return d;
            }
        }
        throw new IllegalStateException(series.getSeriesId() + " has no " + lang + " row");
    }
}
