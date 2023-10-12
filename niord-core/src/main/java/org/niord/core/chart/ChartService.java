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
package org.niord.core.chart;

import org.apache.commons.lang.StringUtils;
import org.hibernate.query.sqm.NodeBuilder;
import org.locationtech.jts.geom.Geometry;
import org.niord.core.db.CriteriaHelper;
import org.niord.core.db.SpatialIntersectsPredicate;
import org.niord.core.service.BaseService;
import org.slf4j.Logger;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Business interface for accessing sea charts
 */
@RequestScoped
@SuppressWarnings("unused")
public class ChartService extends BaseService {

    @Inject
    private Logger log;


    /**
     * Returns the chart with the given legacy id
     *
     * @param legacyId the id of the category
     * @return the chart with the given id or null if not found
     */
    public Chart findByLegacyId(String legacyId) {
        try {
            return em.createNamedQuery("Chart.findByLegacyId", Chart.class)
                    .setParameter("legacyId", legacyId)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Searches for charts matching the given term
     *
     * @param term     the search term
     * @param inactive whether to include inactive charts as well as active
     * @param limit    the maximum number of results
     * @return the search result
     */
    public List<Chart> searchCharts(String term, boolean inactive, int limit) {

        if (StringUtils.isNotBlank(term)) {
            Set<Boolean> activeFlag = new HashSet<>();
            activeFlag.add(Boolean.TRUE);
            if (inactive) {
                activeFlag.add(Boolean.FALSE);
            }
            return em
                    .createNamedQuery("Chart.searchCharts", Chart.class)
                    .setParameter("active", activeFlag)
                    .setParameter("term", "%" + term + "%")
                    .getResultList()
                    .stream()
                    .sorted(Comparator.comparingInt(c -> c.computeSearchSortKey(term)))
                    .limit(limit)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }


    /**
     * Returns the list of charts
     *
     * @return the list of charts
     */
    public List<Chart> getCharts() {
        return getAll(Chart.class);
    }


    /**
     * Returns the chart with the given chart number
     *
     * @param chartNumber the chart number
     * @return the chart with the given chart number
     */
    public Chart findByChartNumber(String chartNumber) {
        try {
            return em
                    .createNamedQuery("Chart.findByChartNumber", Chart.class)
                    .setParameter("chartNumber", chartNumber)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns the charts with the given chart numbers
     *
     * @param chartNumbers the chart numbers
     * @return the charts with the given chart numbers
     */
    public List<Chart> findByChartNumbers(String... chartNumbers) {
        try {
            return em
                    .createNamedQuery("Chart.findByChartNumbers", Chart.class)
                    .setParameter("chartNumbers", Arrays.asList(chartNumbers))
                    .getResultList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }


    /**
     * Updates the chart data from the chart template
     *
     * @param chart the chart to update
     * @return the updated chart
     */
    public Chart updateChart(Chart chart) {
        Chart original = findByChartNumber(chart.getChartNumber());
        if (original == null) {
            throw new IllegalArgumentException("Cannot update non-existing chart " + chart.getChartNumber());
        }
        return updateChart(original, chart);
    }


    /**
     * Updates the chart data from the chart template
     *
     * @param original the original chart to update
     * @param chart    the template chart to update the original from
     * @return the updated chart
     */
    @Transactional
    public Chart updateChart(Chart original, Chart chart) {
        // Copy the chart data
        original.setInternationalNumber(chart.getInternationalNumber());
        original.setActive(chart.isActive());
        original.setHorizontalDatum(chart.getHorizontalDatum());
        original.setName(chart.getName());
        original.setScale(chart.getScale());
        original.setGeometry(chart.getGeometry());

        return saveEntity(original);
    }


    /**
     * Check if there are any changes to the chart data, and update if there are
     * @param original the original chart to update
     * @param chart the template chart to update the original from
     * @return the updated chart
     */
    public Chart checkUpdateChart(Chart original, Chart chart) {
        if (original.hasChanged(chart)) {
            return updateChart(original, chart);
        }
        return original;
    }


    /**
     * Creates a new chart based on the chart template
     * @param chart the chart to create
     * @return the created chart
     */
    @Transactional
    public Chart createChart(Chart chart) {
        Chart original = findByChartNumber(chart.getChartNumber());
        if (original != null) {
            throw new IllegalArgumentException("Cannot create chart with duplicate chart number "
                    + chart.getChartNumber());
        }

        return saveEntity(chart);
    }


    /**
     * Returns the chart matching the given chart template, or creates if it does not exists
     * @param chartTemplate the chart template to find or create
     * @param create whether to create a missing chart or just find it
     * @param update whether to update an existing chart or just find it
     * @return the chart
     */
    public Chart importChart(Chart chartTemplate, boolean create, boolean update) {
        Chart chart = findByChartNumber(chartTemplate.getChartNumber());
        if (create && chart == null) {
            chart = createChart(chartTemplate);
        } else if (update && chart != null) {
            chart = checkUpdateChart(chart, chartTemplate);
        }
        return chart;
    }


    /**
     * Deletes the chart
     * @param chartNumber the id of the chart to delete
     */
    @Transactional
    public boolean deleteChart(String chartNumber) {

        Chart chart = findByChartNumber(chartNumber);
        if (chart != null) {
            remove(chart);
            return true;
        }
        return false;
    }


    /**
     * Returns a list of persisted charts based on a list of template charts
     * @param charts the list of charts to look up persisted charts for
     * @return the list of corresponding persisted charts
     */
    public List<Chart> persistedCharts(List<Chart> charts) {
        return charts.stream()
                .map(c -> findByChartNumber(c.getChartNumber()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }


    /**
     * Returns the list of active charts intersecting with the given geometry.
     * The resulting charts will be ordered by scale
     * @param geometry the geometry
     * @return the list of active charts intersecting with the given geometry
     */
    public List<Chart> getIntersectingCharts(Geometry geometry) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Chart> chartQuery = cb.createQuery(Chart.class);

        Root<Chart> chartRoot = chartQuery.from(Chart.class);

        // Build the predicate
        CriteriaHelper<Chart> criteriaHelper = new CriteriaHelper<>(cb, chartQuery);

        Predicate geomPredicate = new SpatialIntersectsPredicate(
                getNodeBuilder(),
                chartRoot.get("geometry"),
                geometry,
                false);
        criteriaHelper.add(geomPredicate);

        // Only search for active charts
        criteriaHelper.add(cb.equal(chartRoot.get("active"), true));

        // Complete the query
        chartQuery.select(chartRoot)
                .distinct(true)
                .where(criteriaHelper.where())
                .orderBy(cb.asc(chartRoot.get("scale")));

        // Execute the query and update the search result
        return em.createQuery(chartQuery)
                .getResultList();
    }
}
