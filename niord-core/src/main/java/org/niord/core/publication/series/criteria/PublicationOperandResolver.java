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

package org.niord.core.publication.series.criteria;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.niord.core.area.AreaService;
import org.niord.core.category.CategoryService;
import org.niord.core.chart.ChartService;
import org.niord.core.message.MessageSeriesService;

/**
 * Whether an operand in a criteria document names something that actually exists.
 *
 * C-4, asked while there is still a form to correct it in. The rule was already
 * written and already enforced for a domain node; every other kind was accepted
 * on the way in and only discovered at resolve time -- which for a query-backed
 * series is INSIDE the publish transaction, after the cut-off has been stamped.
 * By then the choices are an unpublishable series or a release with a member list
 * nobody meant, and neither is a thing to hand an admin at the moment they press
 * release.
 *
 * WHY EACH KIND IS ANSWERED THE WAY IT IS.
 *
 * AREA and CATEGORY resolve through the same lookup the query builder uses, so
 * "exists here" and "exists there" cannot drift apart. That lookup accepts a
 * surrogate id as well as an MRN, which would let a numeric operand pass here and
 * then fail to port across an export -- but rule C-9 has already refused a purely
 * numeric operand before this is reached, so the two rules compose into "an MRN,
 * and one that names a row".
 *
 * CHART is looked up by chart number, which is the operand's own spelling.
 *
 * DOMAIN keeps the stricter question it already had: not merely "does the domain
 * exist" but "does it expand to any message series at all". A domain node is a
 * macro for the series that domain publishes, so one that expands to nothing
 * narrows the query to nothing, and the issue would publish EMPTY rather than
 * fail -- the one failure mode that looks like success.
 *
 * MESSAGE_SERIES is looked up by its authored id. The resolver puts these into
 * the query as bare strings without ever loading the row, so a misspelt series id
 * shrinks the disjunction silently rather than raising: this is the only place
 * that mistake can be caught at all.
 *
 * The two ENUM kinds are answered true because the validator has already checked
 * them against the enum constants themselves before it asks -- there is no row to
 * look up, and answering false here would report the same mistake twice.
 */
@ApplicationScoped
public class PublicationOperandResolver implements CriteriaValidator.OperandResolver {

    @Inject
    AreaService areaService;

    @Inject
    CategoryService categoryService;

    @Inject
    ChartService chartService;

    @Inject
    MessageSeriesService messageSeriesService;

    @Inject
    DomainSeriesExpander domains;

    @Override
    public boolean exists(CriterionKind kind, String value) {
        if (kind == null || value == null || value.isBlank()) {
            // A blank operand is C-1's complaint, not this one's. Answering false
            // would report one mistake as two, and the second report would name
            // the wrong rule.
            return true;
        }
        String operand = value.trim();
        return switch (kind) {
            case AREA -> areaService.findByAreaId(operand) != null;
            case CATEGORY -> categoryService.findByCategoryId(operand) != null;
            case CHART -> chartService.findByChartNumber(operand) != null;
            case DOMAIN -> domains.exists(operand);
            case MESSAGE_SERIES -> messageSeriesService.findBySeriesId(operand) != null;
            // Already checked against the enum's own constants by the validator.
            case MESSAGE_TYPE, MESSAGE_MAIN_TYPE -> true;
        };
    }
}
