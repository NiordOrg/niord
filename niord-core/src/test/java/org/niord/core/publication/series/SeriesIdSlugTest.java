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

package org.niord.core.publication.series;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One namespace, one fold.
 *
 * A series id is authored once and is immutable afterwards -- it is the key an
 * import upserts on and the handle a citation stores. Two mintings that disagree
 * therefore do not surface as a conflict anybody notices; they produce two series
 * that were meant to be one, permanently, and the only repair is hand SQL against
 * a live archive.
 *
 * They did disagree, and the difference is one character: the interactive editor
 * folded the Danish letters and then stripped accents, while the importer
 * stripped first -- and NFD turns U+00E5 into a plain "a" plus a combining ring,
 * so a fold applied afterwards has nothing left to see.
 */
public class SeriesIdSlugTest {

    /**
     * The ring above a is a LETTER here, not a decoration.
     *
     * This is the case that names the defect: "Årsberetning" is a real
     * publication title, and the two implementations answered "aarsberetning" and
     * "arsberetning" for it.
     */
    @Test
    public void theDanishLettersTransliterateRatherThanLosingTheirVowel() {
        assertEquals("aarsberetning", SeriesIdSlug.fold("Årsberetning"));
        assertEquals("soefartsstyrelsen", SeriesIdSlug.fold("Søfartsstyrelsen"));
        assertEquals("aendringer", SeriesIdSlug.fold("Ændringer"));
    }

    /** An accent with no Danish meaning is simply dropped. */
    @Test
    public void anordinaryAccentIsStripped() {
        assertEquals("resume", SeriesIdSlug.fold("Résumé"));
    }

    /** Punctuation and runs of it collapse to single separators, with none at the ends. */
    @Test
    public void punctuationBecomesSingleHyphensAndNeverAnEdge() {
        assertEquals("efs-uge-33-2026", SeriesIdSlug.fold("  EfS, uge 33 -- 2026!  "));
        assertEquals("", SeriesIdSlug.fold("---"));
        assertEquals("", SeriesIdSlug.fold(null));
    }

    /**
     * A cut id never ends on a separator.
     *
     * The column is varchar(64) and MySQL in strict mode refuses an over-long
     * insert rather than truncating, so the cut has to happen here -- and an id
     * ending in a hyphen reads as though something was lost, which it was.
     */
    @Test
    public void afitIdIsCutWithoutATrailingSeparator() {
        String long1 = SeriesIdSlug.fold(
                "Meddelelse fra Marinestaben om istjeneste samt om ismeldinger for vinteren 2019");
        assertTrue(long1.length() > SeriesIdSlug.MAX_SERIES_ID, "the fixture stopped being long");

        String fitted = SeriesIdSlug.fit(long1, SeriesIdSlug.MAX_SERIES_ID);
        assertTrue(fitted.length() <= SeriesIdSlug.MAX_SERIES_ID);
        assertTrue(!fitted.endsWith("-"), "a cut id must not end on a separator");
        assertTrue(long1.startsWith(fitted), "the fit changed the id rather than shortening it");
    }

    /** Nothing to cut is left alone. */
    @Test
    public void ashortIdIsUnchangedByTheFit() {
        assertEquals("weekly-ntm", SeriesIdSlug.fit("weekly-ntm", SeriesIdSlug.MAX_SERIES_ID));
    }
}
