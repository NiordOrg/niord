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

package org.niord.core.publication.series;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Turns a title into a series id.
 *
 * ONE function, because there is one namespace. A series id is authored once and
 * is then immutable -- it is the interchange key an import upserts on and the
 * handle a citation stores -- so two mintings that disagree do not produce a
 * conflict anybody notices: they produce two series that were meant to be one,
 * permanently.
 *
 * They did disagree. Two implementations existed, one folding the Danish letters
 * before stripping accents and one after, and the order decides the answer:
 * U+00E5 decomposes under NFD to a plain "a" plus a combining ring, so a fold
 * applied afterwards has nothing left to see and "Aarsberetning" comes out as
 * "arsberetning" from one surface and "aarsberetning" from the other.
 *
 * THE FOLD RUNS FIRST, and that is the answer that stands. It is what the
 * interactive editor already produced -- so it is what is written on the ids a
 * person has seen -- and it is the correct Danish transliteration: aa, oe and ae
 * are the letters, not decorations on a, o and e.
 */
public final class SeriesIdSlug {

    /** The column width the id has to fit, and the reason the fits below exist. */
    public static final int MAX_SERIES_ID = 64;

    private SeriesIdSlug() {
    }

    /**
     * Lower-case ASCII with single hyphens, Danish letters transliterated.
     *
     * A bare non-ASCII filter is not an alternative: slashed o carries no
     * separable diacritic and ae is a ligature, so both would simply vanish and
     * leave a hole in the middle of the identifier.
     */
    public static String fold(String text) {
        if (text == null) {
            return "";
        }
        String folded = text
                .replace("æ", "ae").replace("Æ", "AE")
                .replace("ø", "oe").replace("Ø", "OE")
                .replace("å", "aa").replace("Å", "AA");
        return Normalizer.normalize(folded, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
    }

    /** The slug, cut to length without leaving a trailing hyphen behind. */
    public static String fit(String slug, int max) {
        if (slug == null) {
            return "";
        }
        return slug.length() <= max ? slug : slug.substring(0, max).replaceAll("-+$", "");
    }
}
