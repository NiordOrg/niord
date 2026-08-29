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

import jakarta.persistence.Query;

import java.util.Map;

/**
 * "Visible from domain X", as ONE predicate every reading surface shares.
 *
 * THREE SURFACES ASK THE SAME QUESTION and they must not answer it differently:
 * the citation picker an editor uses, the anonymous publication search the union
 * is built from, and any issue read that takes a domain. A publication offered by
 * one and withheld by the other is a citation that can be made and then cannot be
 * resolved -- and the two implementations would drift the first time the rule
 * gained a case, because nothing compares them.
 *
 * THE RULE. A series is visible from X when X owns it, or when it is available
 * everywhere, or when it names X in its availability list. An INACTIVE domain in
 * that list is ignored: a domain that has been switched off is not a desk anybody
 * is sitting at, and leaving its entry live would keep a publication reachable
 * from a place that no longer exists.
 *
 * WRITTEN AS EXISTS, NOT AS A JOIN. The obvious form -- LEFT JOIN the availability
 * list and SELECT DISTINCT -- multiplies a row by the number of domains it is
 * shared with, which the picker then pages over: a series shared with three
 * domains would consume three slots of a twenty-row page and the total would count
 * it three times. EXISTS asks whether there is a match without producing one row
 * per match, so the page means what it says.
 *
 * CLAUSE AND BINDINGS TRAVEL TOGETHER. {@link #bind} fills exactly the parameters
 * {@link #clause} names. A clause appended without its bindings is a request-time
 * failure rather than a compile-time one, and this surface has already had that
 * bug once, in the search adapter's status filter.
 */
public final class SeriesVisibility {

    /** The domain the caller is asking from. */
    public static final String DOMAIN_PARAM = "visibleFromDomain";

    private static final String ALL_PARAM = "visibleFromAll";

    private static final String SELECTED_PARAM = "visibleFromSelected";

    private SeriesVisibility() {
    }

    /**
     * The predicate, over an already-joined series and its domain.
     *
     * @param seriesAlias the series alias in the caller's FROM clause
     * @param domainAlias the alias of a LEFT JOIN onto that series' domain. Named
     *                    rather than written as a path expression because a path
     *                    generates an INNER join, which drops a series whose owner
     *                    is missing before the predicate is evaluated -- and on a
     *                    database where the owner backfill could not run, that row
     *                    exists.
     */
    public static String clause(String seriesAlias, String domainAlias) {
        return "(" + domainAlias + ".domainId = :" + DOMAIN_PARAM
                + " OR " + seriesAlias + ".availability = :" + ALL_PARAM
                + " OR (" + seriesAlias + ".availability = :" + SELECTED_PARAM
                + " AND EXISTS (SELECT 1 FROM PublicationSeries vs JOIN vs.availableDomains vd"
                + " WHERE vs = " + seriesAlias + " AND vd.domainId = :" + DOMAIN_PARAM
                + " AND vd.active = true)))";
    }

    /** The bindings {@link #clause} names, for a caller that collects them in a map. */
    public static void bind(Map<String, Object> bindings, String domainId) {
        bindings.put(DOMAIN_PARAM, domainId == null ? null : domainId.trim());
        bindings.put(ALL_PARAM, SeriesAvailability.ALL_DOMAINS);
        bindings.put(SELECTED_PARAM, SeriesAvailability.SELECTED_DOMAINS);
    }

    /** The same bindings, applied straight onto a query. */
    public static void bind(Query query, String domainId) {
        query.setParameter(DOMAIN_PARAM, domainId == null ? null : domainId.trim());
        query.setParameter(ALL_PARAM, SeriesAvailability.ALL_DOMAINS);
        query.setParameter(SELECTED_PARAM, SeriesAvailability.SELECTED_DOMAINS);
    }

    /**
     * The same rule in memory, for a caller holding the entity rather than a query.
     *
     * The two forms are the one rule stated twice, which is a cost -- but the
     * alternative is a round trip per row on surfaces that already hold the series,
     * and the tests drive both against the same cases.
     */
    public static boolean visibleFrom(PublicationSeries series, String domainId) {
        if (series == null) {
            return false;
        }
        if (domainId == null || domainId.isBlank()) {
            // No domain named is no narrowing asked for.
            return true;
        }
        String wanted = domainId.trim();
        if (series.getDomain() != null && wanted.equals(series.getDomain().getDomainId())) {
            return true;
        }
        SeriesAvailability availability = series.getAvailability();
        if (availability == SeriesAvailability.ALL_DOMAINS) {
            return true;
        }
        if (availability != SeriesAvailability.SELECTED_DOMAINS) {
            return false;
        }
        return series.getAvailableDomains().stream()
                .anyMatch(d -> d != null && d.isActive() && wanted.equals(d.getDomainId()));
    }
}
