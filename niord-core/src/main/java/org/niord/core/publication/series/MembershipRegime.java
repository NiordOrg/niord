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

import org.niord.core.publication.series.resolve.TimeRelation;

/**
 * Where an issue's members come from: nowhere, a query, or another series.
 *
 * ONE ANSWER, in one place, because six call sites used to decide it
 * independently -- the publish transaction, the amend it shares, the preview, the
 * shared resolve behind the issue screen, the curation guard and the message
 * editor's lookup. Each spelled the same boolean out longhand, and each would
 * have needed the third arm added by hand. A fork that has to be written six
 * times is a fork that drifts: the difference between "what the rail counted" and
 * "what the publish froze" is precisely the kind of disagreement nobody can
 * reproduce.
 *
 * The criteria-document check is deliberately NOT folded in here. "Does this
 * series select by query" and "does the document it selects with resolve" are
 * different questions, and only the QUERY arm has the second one; a compilation
 * has no document at all, and an uploaded series has neither.
 */
public enum MembershipRegime {

    /** No member list at all: an uploaded file, an external link, or nothing. */
    NONE,

    /** The members are what a criteria document selects over the issue's period. */
    QUERY,

    /**
     * The members are the union of a source series' published issues' frozen rows.
     *
     * A compilation that names NO source yet resolves nothing by derivation --
     * overrides alone, exactly as a query series whose document is null does --
     * so it answers QUERY rather than COMPILED and the criteria check downstream
     * then finds no document. That keeps "the regime" and "can it actually be
     * derived" as two questions, which is what the half-configured series in the
     * create form needs.
     */
    COMPILED;

    /** The regime this series is in. Null-safe: a series with no shape has none. */
    public static MembershipRegime of(PublicationSeries series) {
        if (series == null
                || series.getContentMode() != ContentMode.GENERATED_FROM_QUERY
                || series.getTimeRelation() == null) {
            return NONE;
        }
        if (series.getTimeRelation() == TimeRelation.COMPILED_FROM_SOURCE
                && series.getSourceSeries() != null) {
            return COMPILED;
        }
        return QUERY;
    }

    /** Whether this regime raises a membership question at all. */
    public boolean hasMembership() {
        return this != NONE;
    }
}
