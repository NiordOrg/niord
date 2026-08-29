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

package org.niord.core.publication.series.criteria;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.niord.core.domain.Domain;
import org.niord.core.domain.DomainService;
import org.niord.core.message.MessageSeries;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The domain macro, expanded.
 *
 * A domain node is shorthand for "every message series this domain publishes",
 * and it is expanded to that set HERE, before the query is built and before the
 * set is frozen into a published issue's snapshot. It is not a predicate of its
 * own: the message search never reads a domain as a filter, so applying the
 * domain beside the series it expands to would narrow the scope twice and
 * silently.
 *
 * Expanding rather than storing the series list is what makes the node worth
 * having: a domain that gains a message series next year selects it without
 * anybody editing the criteria of every publication that scopes by that domain.
 * The published issues do not move, because each one froze the expansion it
 * actually used.
 *
 * AN UNKNOWN OR EMPTY DOMAIN RESOLVES TO NOTHING, and the resolver refuses that
 * rather than accepting it: an empty operand narrows a query to nothing at all,
 * so the issue would publish EMPTY instead of failing -- the one failure mode
 * that looks like success.
 */
@ApplicationScoped
public class DomainSeriesExpander implements CriteriaResolver.DomainExpander {

    @Inject
    DomainService domainService;

    @Override
    public Set<String> seriesIdsOf(String domainId) {
        if (domainId == null || domainId.isBlank()) {
            return Set.of();
        }
        Domain domain = domainService.findByDomainId(domainId.trim());
        if (domain == null || domain.getMessageSeries() == null) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (MessageSeries series : domain.getMessageSeries()) {
            if (series != null && series.getSeriesId() != null && !series.getSeriesId().isBlank()) {
                out.add(series.getSeriesId());
            }
        }
        return out;
    }

    /**
     * Whether a domain operand names something that exists AND carries series.
     *
     * C-4's half of the same question, asked while there is still a form to
     * correct: a criteria document saved against a domain that resolves to no
     * message series would refuse at every resolve afterwards -- including inside
     * the publish transaction, after the cut-off has been stamped -- and the
     * series would simply become unpublishable with no way back but an edit.
     */
    public boolean exists(String domainId) {
        return !seriesIdsOf(domainId).isEmpty();
    }
}
