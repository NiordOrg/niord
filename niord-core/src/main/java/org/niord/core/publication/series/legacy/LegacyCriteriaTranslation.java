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

package org.niord.core.publication.series.legacy;

import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.criteria.IssueCriterionVo;
import org.niord.core.publication.series.criteria.LegacyFilterTranslator;
import org.niord.core.publication.series.criteria.MessageSeriesCriterionVo;
import org.niord.core.publication.series.criteria.MessageTypeCriterionVo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

/**
 * The criteria document for an imported series.
 *
 * Without one an imported series is unusable: S-1 refuses to activate a
 * query-backed series with a null criteria, so the series stays DRAFT, the
 * shadow diff skips every one of its releases as NO_MEMBERSHIP_SEMANTICS, and
 * the two-consecutive-green-weeks precondition can never be met. The archive
 * imports and then cannot be verified or cut over.
 *
 * TWO SOURCES, AND THEY ARE DIFFERENT IN KIND.
 *
 * The message TYPES come from the legacy filter, which states them: the P&T
 * filter is a disjunction over TEMPORARY_NOTICE and PRELIMINARY_NOTICE, and
 * LegacyFilterTranslator already reduces it to that pair. Nothing is inferred.
 *
 * The message SERIES comes from EVIDENCE -- the series the archive actually drew
 * from, read off the tagged messages. It is not in the filter and cannot be
 * derived from it, and it is not optional either: C-6 requires a messageSeries
 * or domain node, because a document without one resolves over every message in
 * the system. Legacy scoped by which recorder wrote the tag; the new model
 * scopes by an explicit node, and the only honest bridge is what the tag
 * actually contained.
 *
 * WHAT IS DELIBERATELY NOT IN THE DOCUMENT. Status is a resolver invariant
 * (RI-1, C-5) and three of the four legacy filters state it; storing it would
 * let an edit weaken it. The phase guard is a recorder trigger -- it says WHEN
 * the tag was written, not WHICH messages belong. Both are dropped, and
 * LegacyFilterTranslator records that it dropped them.
 *
 * THIS IS A PROPOSAL, NOT A FACT. The series lands DRAFT precisely so an admin
 * reviews the translation before activating, and the shadow diff is what checks
 * it against reality: it resolves this document over each historical interval
 * and compares the result to the frozen members, per release, with the delta.
 * A document that is wrong shows up there as missing/extra rather than as a
 * quietly wrong publication.
 */
public final class LegacyCriteriaTranslation {

    private LegacyCriteriaTranslation() {
    }

    /**
     * The document, or NULL when there is no evidence to scope it with.
     *
     * Null rather than a document with no series node, and that is the whole
     * decision: an unscoped document resolves over every message in the system,
     * and an issue that silently contains everything is far worse than a series
     * that refuses to activate until somebody looks at it. C-6 would reject it
     * anyway; returning null makes the refusal happen at import, where the
     * report can name it.
     *
     * @param filter the template's own filter translation
     * @param messageSeriesIds the series the archive drew from; may be empty
     */
    public static IssueCriteriaVo translate(LegacyFilterTranslator.Translation filter,
                                            Collection<String> messageSeriesIds) {
        if (filter == null || messageSeriesIds == null) {
            return null;
        }

        // Sorted and de-duplicated: the document is compared, diffed and reviewed
        // by people, and a set whose order depends on the query plan makes two
        // identical imports produce two different-looking documents.
        List<String> scope = new ArrayList<>(new TreeSet<>(messageSeriesIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .toList()));
        if (scope.isEmpty()) {
            return null;
        }

        List<IssueCriterionVo> nodes = new ArrayList<>();
        nodes.add(node(new MessageSeriesCriterionVo(), scope));

        if (filter.messageTypes() != null && !filter.messageTypes().isEmpty()) {
            nodes.add(node(new MessageTypeCriterionVo(), filter.messageTypes()));
        }

        IssueCriteriaVo doc = new IssueCriteriaVo();
        doc.setCriteria(nodes);
        return doc;
    }

    private static <T extends IssueCriterionVo> T node(T criterion, Collection<String> values) {
        criterion.setValues(new ArrayList<>(values));
        return criterion;
    }
}
