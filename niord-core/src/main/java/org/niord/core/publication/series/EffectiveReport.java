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

/**
 * Which report renders THIS issue: its own, where it names one, else the
 * series'.
 *
 * The escape hatch a one-week deviation needs. An edition that has to be set out
 * differently -- a supplement, a year-end double issue with its own front matter
 * -- used to require cloning the whole series, which is how six `dont-use-`
 * series ended up in the estate fragmenting the archives they were cloned from.
 *
 * ONE RESOLUTION, and the three callers are the reason it is a method rather
 * than an expression written out three times. The release rail reports whether a
 * report is configured, the preview renders one, and the publish renders the
 * other -- and a rail that answered for the series while the publish rendered
 * the issue's would pass an issue that then failed, or refuse one that would
 * have worked. The same defect the criteria override already documents, one
 * field along.
 *
 * An override equal to the series' value is NOT a deviation, and is not stored
 * as one: writing it would badge the issue "tilpasset" while it renders exactly
 * what the series renders.
 */
public final class EffectiveReport {

    private EffectiveReport() {
    }

    /** The report this issue renders with, or null where nothing names one. */
    public static String idOf(PublicationIssue issue, PublicationSeries series) {
        String own = trimmed(issue == null ? null : issue.getReportId());
        if (own != null) {
            return own;
        }
        return series == null ? null : series.getReportId();
    }

    /** The same, for a caller that holds only the issue. */
    public static String idOf(PublicationIssue issue) {
        return idOf(issue, issue == null ? null : issue.getSeries());
    }

    /**
     * Whether this issue renders with something other than its series says.
     *
     * DERIVED, and deliberately not "the column is set": a series edited to name
     * the report the issue already named is no longer a deviation, and a screen
     * that badged it as one would be reporting a difference nobody can see.
     */
    public static boolean isOverridden(PublicationIssue issue) {
        String own = trimmed(issue == null ? null : issue.getReportId());
        if (own == null) {
            return false;
        }
        PublicationSeries series = issue.getSeries();
        return series == null || !own.equals(series.getReportId());
    }

    private static String trimmed(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
