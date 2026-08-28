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

package org.niord.core.publication.series.resolve;

import org.niord.model.message.MainType;
import org.niord.model.message.Status;
import org.niord.model.message.Type;

import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The facts about one message that membership depends on.
 *
 * Deliberately a plain value with no entity behind it. Two of these fields are
 * mutable in production -- publishDateFrom is editor-writable and nullable even
 * once published, and type is mutable and unversioned -- so a frozen member
 * snapshot has to record what they were at freeze time. A structure that could
 * re-read them later would not be reproducible.
 *
 * Keyed on uid, never shortId: shortId is not declared unique and nothing
 * prevents reuse, so a shortId-keyed comparison can report two member sets as
 * identical while they differ.
 *
 * THE COLLECTION-BACKED FACETS ARE TRI-STATE, and that is the point of them.
 * areaMrns, categoryMrns and chartNumbers are null when the facet was NOT READ,
 * and empty when it was read and the message has none. Collapsing the two would
 * be silent in the worst possible way: a reader that skipped the join to save
 * three lazy loads would produce facts on which every message looks area-less,
 * the predicate would reject the whole candidate set, and the issue would
 * publish empty while every step of it looked healthy. The predicate raises on
 * null rather than deciding, so the omission surfaces as a failure instead of as
 * a short member list.
 *
 * mainType is the same tri-state by the same rule -- a message always has one,
 * so null can only mean "not read".
 *
 * AREAS, NOT AREA. The membership facet is the message's whole area list; the
 * single primary area is a sorting field and matching on it would silently drop
 * every message whose second or third area is the one the criterion names.
 * areaMrns and categoryMrns hold each attached node's own MRN AND its
 * ancestors', so a criterion naming a parent matches a message filed under a
 * child -- the hierarchy is expanded into the facts rather than being a lookup
 * the predicate would need a database for.
 */
public record MessageFacts(
        String uid,
        Date publishDateFrom,
        Date publishDateTo,
        Status status,
        Type type,
        String messageSeriesId,
        MainType mainType,
        Set<String> areaMrns,
        Set<String> categoryMrns,
        Set<String> chartNumbers) {

    public MessageFacts {
        areaMrns = frozenOrAbsent(areaMrns);
        categoryMrns = frozenOrAbsent(categoryMrns);
        chartNumbers = frozenOrAbsent(chartNumbers);
    }

    /**
     * The facts a message carries on its own row, with no facet joined in.
     *
     * For callers deciding against criteria that select on nothing but the
     * message row -- and for the frozen-snapshot comparisons, which have never
     * had anything else to compare.
     */
    public MessageFacts(String uid, Date publishDateFrom, Date publishDateTo,
                        Status status, Type type, String messageSeriesId) {
        this(uid, publishDateFrom, publishDateTo, status, type, messageSeriesId,
                null, null, null, null);
    }

    /** Null stays null -- "not read" is a fact, and normalising it away loses it. */
    private static Set<String> frozenOrAbsent(Set<String> values) {
        return values == null ? null : Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }
}
