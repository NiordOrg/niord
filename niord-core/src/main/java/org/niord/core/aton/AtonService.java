/*
 * Copyright 2016 Danish Maritime Authority.
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
package org.niord.core.aton;

import org.apache.commons.lang.StringUtils;
import org.hibernate.query.sqm.NodeBuilder;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.scope.SearchScope;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.niord.core.area.Area;
import org.niord.core.chart.Chart;
import org.niord.core.db.CriteriaHelper;
import org.niord.core.db.SpatialWithinPredicate;
import org.niord.core.model.BaseEntity;
import org.niord.core.service.BaseService;
import org.niord.core.user.UserService;
import org.niord.model.search.PagedSearchResultVo;
import org.slf4j.Logger;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.*;
import jakarta.transaction.Transactional;
import java.util.*;
import java.util.stream.Collectors;

import static org.niord.core.util.LuceneUtils.normalize;

/**
 * Interface for handling AtoNs
 */
@RequestScoped
@SuppressWarnings("unused")
public class AtonService extends BaseService {

    @Inject
    private Logger log;

    @Inject
    EntityManager entityManager;

    @Inject
    private UserService userService;

    /*************************/
    /** NEW Aton Model      **/
    /*************************/

    /**
     * Returns the AtoNs with the given tag key-value
     *
     * @param key   the tag key
     * @param value the tag value
     * @return the AtoNs with the given tag key-value
     */
    @Transactional
    public List<AtonNode> findByTag(String key, String value) {
        return em
                .createNamedQuery("AtonNode.findByTag", AtonNode.class)
                .setParameter("key", key)
                .setParameter("value", value)
                .getResultList();
    }


    /**
     * Returns the AtoNs with the given tag key and values
     *
     * @param key   the tag key
     * @param values the tag values
     * @return the AtoNs with the given tag key and values
     */
    @Transactional
    public List<AtonNode> findByTagValues(String key, String... values) {
        Set<String> valueSet = new HashSet<>(Arrays.asList(values));
        return em
                .createNamedQuery("AtonNode.findByTagValues", AtonNode.class)
                .setParameter("key", key)
                .setParameter("values", valueSet)
                .getResultList();
    }


    /**
     * Returns the AtoNs with the given AtoN UID
     * @param atonUid the AtoN UID
     * @return the AtoNs with the given AtoN UID or null if not found
     */
    @Transactional
    public AtonNode findByAtonUid(String atonUid) {
        return findByTag(AtonTag.TAG_ATON_UID, atonUid).stream()
                .findFirst()
                .orElse(null);
    }


    /**
     * Returns the AtoNs with the given AtoN UIDs
     * @param atonUids the AtoN UIDs
     * @return the AtoNs with the given AtoN UIDs
     */
    @Transactional
    public List<AtonNode> findByAtonUids(String... atonUids) {
        return findByTagValues(AtonTag.TAG_ATON_UID, atonUids);
    }


    /**
     * Replaces the AtoN DB
     * @param atons the new AtoNs
     */
    @Transactional
    public void updateAtons(List<AtonNode> atons) {

        // Persist new list of AtoNs
        long t0 = System.currentTimeMillis();
        int created = 0, updated = 0, unchanged = 0;
        for (AtonNode aton : atons) {

            AtonNode orig = findByAtonUid(aton.getAtonUid());
            if (orig == null) {
                em.persist(aton);
                created++;

            } else if (orig.hasChanged(aton)) {
                orig.updateNode(aton);
                em.persist(orig);
                updated++;

            } else {
                unchanged++;
            }

            if ((created + updated + unchanged) % 100 == 0) {
                em.flush();
            }
        }
        log.info(String.format("Updated %s AtoNs (created %d, updated %d, ignored %d) in %d ms",
                atons.size(), created, updated, unchanged, System.currentTimeMillis() - t0));
    }


    /**
     * Computes the list of AtoNs that matches the search parameters.<br>
     *
     * @return the AtoNs within that matches the search parameters
     */
    @Transactional
    public PagedSearchResultVo<AtonNode> search(AtonSearchParams param) {
        try {
            //"select count(a) from AtonNode a, Chart c where c.chartNumber in ('101') and within(a.geometry, c.geometry) = true";

            PagedSearchResultVo<AtonNode> result = new PagedSearchResultVo<>();

            // First fetch the ID's of the of all matching AtoNs
            CriteriaHelper<Tuple> criteriaHelper = CriteriaHelper.initWithTupleQuery(em);

            Root<AtonNode> atonRoot = buildSearchCriteria(criteriaHelper, param);

            criteriaHelper.getCriteriaQuery()
                    .multiselect(atonRoot.get("id"))
                    .distinct(true)
                    .where(criteriaHelper.where());

            List<Integer> atonIds = em
                    .createQuery(criteriaHelper.getCriteriaQuery())
                    .getResultList()
                    .stream()
                    .map(t -> (Integer)t.get(0))
                    .collect(Collectors.toList());

            result.setTotal(atonIds.size());

            // For efficiency reasons the callee may not want any data returned if the
            // result is larger than maxAtonNo
            if (param.isEmptyOnOverflow() && param.getMaxSize() < atonIds.size()) {
                return result;
            }

            // Get the ID's of the sub-list to fetch
            atonIds = atonIds.subList(0, Math.min(atonIds.size(), param.getMaxSize()));
            if (atonIds.size() == 0) {
                return result;
            }

            // TODO: When cache is implemented, look up AtoNs via cache

            List<AtonNode> atons = em.createNamedQuery("AtonNode.findByIds", AtonNode.class)
                    .setParameter("ids", atonIds)
                    .getResultList();

            result.setData(atons);
            result.updateSize();
            return result;

        } catch (Exception e) {
            log.error("Error searching for AtoNs with params " + param, e);
            return new PagedSearchResultVo<>();
        }
    }


    /**
     * Computes the list of AtoN lon-lat positions that matches the search parameters.<br>
     *
     * @return the AtoN lon-lat positions
     */
    @Transactional
    public List<double[]> searchPositions(AtonSearchParams param) {
        try {
            CriteriaHelper<Tuple> criteriaHelper = CriteriaHelper.initWithTupleQuery(em);

            Root<AtonNode> atonRoot = buildSearchCriteria(criteriaHelper, param);

            criteriaHelper.getCriteriaQuery()
                    .multiselect(atonRoot.get("lon"), atonRoot.get("lat"))
                    .distinct(true)
                    .where(criteriaHelper.where());

            return em
                    .createQuery(criteriaHelper.getCriteriaQuery())
                    .setMaxResults(param.getMaxSize())
                    .getResultList()
                    .stream()
                    .map(t -> new double[] { (double)t.get(0), (double)t.get(1) })
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error searching for AtoNs positions with params " + param, e);
            return Collections.emptyList();
        }
    }

    /**
     * Computes the list of AtoNs that matches the search parameters.<br>
     *
     * @return the AtoNs within that matches the search parameters
     */
    private <T> Root<AtonNode> buildSearchCriteria(CriteriaHelper<T> criteriaHelper, AtonSearchParams param) {

        CriteriaBuilder cb = criteriaHelper.getCriteriaBuilder();
        CriteriaQuery<T> c = criteriaHelper.getCriteriaQuery();

        Root<AtonNode> atonRoot = c.from(AtonNode.class);

        if (StringUtils.isNotBlank(param.getName())) {
            Join<AtonNode, AtonTag> tags = atonRoot.join("tags", JoinType.LEFT);
            criteriaHelper
                    .like(tags.get("v"), normalize(param.getName()));
        }

        if (param.getExtent() != null) {
            criteriaHelper.add(new SpatialWithinPredicate((NodeBuilder)cb, atonRoot.get("geometry"), param.getExtent(), false));
        }

        if (!param.getChartNumbers().isEmpty()) {
            Root<Chart> chartRoot = c.from(Chart.class);
            criteriaHelper
                    .add(new SpatialWithinPredicate(getNodeBuilder(), atonRoot.get("geometry"), chartRoot.get("geometry"), false))
                    .in(chartRoot.get("chartNumber"), param.getChartNumbers());
        }

        if (!param.getAreaIds().isEmpty()) {
            Root<Area> areaRoot = c.from(Area.class);
            criteriaHelper
                    .add(new SpatialWithinPredicate((NodeBuilder)cb, atonRoot.get("geometry"), areaRoot.get("geometry"), false))
                    .in(areaRoot.get("id"), param.getAreaIds());
        }

        return atonRoot;
    }


    /**
     * Performs a Hibernate Search on the AtoN tag values
     * @param value the search string
     * @return the IDs of the AtoN nodes that matches the search criteria
     */
    private List<Integer> searchAtonTagKeys(String value) {
        // Initialise the search scope to AtoN nodes
        SearchSession searchSession = Search.session(entityManager);
        SearchScope<AtonNode> scope = searchSession.scope( AtonNode.class );

        // Execute search
        return searchSession.search( scope )
                .where(f -> f.wildcard()
                        .fields("tags.v")
                        .matching("*" + normalize(value) + "*"))
                .fetchAllHits()
                .stream()
                .map(BaseEntity::getId)
                .collect(Collectors.toList());
    }

    /**
     * Checks whether the provided AtoN's UID conforms to the norms. That is
     * non empty and without whitespaces.
     * @param aton The AtoN whose UID will be checked
     * @return Whether the AtoN's UID conforms to the norms
     */
    private boolean checkAtonUID(AtonNode aton) {
        // Check that the UID is not null
        if(StringUtils.trimToNull(aton.getAtonUid()) == null) {
            return false;
        }
        // Check that we don't have any white spaces
        if(!aton.getAtonUid().matches("\\S+")) {
            return false;
        }
        // Return success
        return true;
    }

}
