/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.niord.core.chart;

import org.apache.commons.lang.StringUtils;
import org.niord.core.service.BaseService;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Business interface for accessing sea charts
 */
@Stateless
@SuppressWarnings("unused")
public class ChartService extends BaseService {

    @Inject
    private Logger log;

    /**
     * Searches for charts matching the given term
     *
     * @param term the search term
     * @param limit the maximum number of results
     * @return the search result
     */
    public List<Chart> searchCharts(String term, int limit) {

        if (StringUtils.isNotBlank(term)) {
            return em
                    .createNamedQuery("Chart.searchCharts", Chart.class)
                    .setParameter("term", "%" + term + "%")
                    .getResultList()
                    .stream()
                    .sorted((c1, c2) -> Integer.compare(c1.computeSearchSortKey(term), c2.computeSearchSortKey(term)))
                    .limit(limit)
                    .collect(Collectors.toList());
       }
        return Collections.emptyList();
    }


    /**
     * Returns the list of charts
     * @return the list of charts
     */
    public List<Chart> getCharts() {
        return getAll(Chart.class);
    }


    /**
     * Returns the chart with the given chart number
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
     * @param chart the chart to update
     * @return the updated chart
     */
    public Chart updateChartData(Chart chart) {
        Chart original = findByChartNumber(chart.getChartNumber());
        if (original == null) {
            throw new IllegalArgumentException("Cannot update non-existing chart "
                    + chart.getChartNumber());
        }

        // Copy the chart data
        original.setInternationalNumber(chart.getInternationalNumber());
        original.setHorizontalDatum(chart.getHorizontalDatum());
        original.setName(chart.getName());
        original.setScale(chart.getScale());
        original.setGeometry(chart.getGeometry());

        return saveEntity(original);
    }


    /**
     * Creates a new chart based on the chart template
     * @param chart the chart to create
     * @return the created chart
     */
    public Chart createChart(Chart chart) {
        Chart original = findByChartNumber(chart.getChartNumber());
        if (original != null) {
            throw new IllegalArgumentException("Cannot create chart with duplicate chart number "
                    + chart.getChartNumber());
        }

        return saveEntity(chart);
    }


    /**
     * Deletes the chart
     * @param chartNumber the id of the chart to delete
     */
    public boolean deleteChart(String chartNumber) {

        Chart chart = findByChartNumber(chartNumber);
        if (chart != null) {
            remove(chart);
            return true;
        }
        return false;
    }

}
