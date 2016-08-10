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

import com.vividsolutions.jts.geom.Geometry;
import org.apache.commons.lang.StringUtils;
import org.niord.core.geojson.JtsConverter;
import org.niord.core.model.VersionedEntity;
import org.niord.model.DataFilter;
import org.niord.model.vo.ChartVo;

import javax.persistence.Cacheable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.Transient;
import java.util.Objects;

/**
 * Represents a chart
 */
@Entity
@Cacheable
@Table(indexes = {
        @Index(name = "chart_chart_number", columnList="chartNumber", unique = true)
})
@NamedQueries({

        @NamedQuery(name  = "Chart.searchCharts",
                query = "select distinct c from Chart c where c.active in (:active) and "
                        +"(lower(c.chartNumber) like lower(:term) "
                        + "or str(c.internationalNumber) like lower(:term) "
                        + "or lower(c.name) like lower(:term) )"),

        @NamedQuery(name  = "Chart.findAll",
                query = "select c from Chart c order by coalesce(scale, 99999999) asc, chartNumber"),

        @NamedQuery(name="Chart.findByChartNumber",
                query="SELECT chart FROM Chart chart where chart.chartNumber = :chartNumber"),

        @NamedQuery(name="Chart.findByChartNumbers",
                query="SELECT chart FROM Chart chart where chart.chartNumber IN :chartNumbers")
})
@SuppressWarnings("unused")
public class Chart extends VersionedEntity<Integer> {

    @Column(unique = true, nullable = false)
    String chartNumber;

    Integer internationalNumber;

    boolean active = true;

    @Column(columnDefinition = "GEOMETRY")
    Geometry geometry;

    String horizontalDatum;

    Integer scale;

    String name;

    /**
     * Constructor
     */
    public Chart() {
    }


    /**
     * Constructor
     */
    public Chart(String chartNumber, Integer internationalNumber) {
        this.chartNumber = chartNumber;
        this.internationalNumber = internationalNumber;
    }


    /**
     * Constructor
     */
    public Chart(ChartVo chart) {
        updateChart(chart);
    }


    /** Updates this chart from the given chart */
    public void updateChart(ChartVo chart) {
        this.chartNumber = chart.getChartNumber();
        this.internationalNumber = chart.getInternationalNumber();
        this.active = chart.isActive();
        this.geometry = JtsConverter.toJts(chart.getGeometry());
        this.horizontalDatum = chart.getHorizontalDatum();
        this.scale = chart.getScale();
        this.name = chart.getName();
    }

    /** Converts this entity to a value object */
    public ChartVo toVo() {
        return toVo(DataFilter.get().fields(DataFilter.ALL));
    }

    /** Converts this entity to a value object */
    public ChartVo toVo(DataFilter filter) {
        ChartVo chart = new ChartVo();
        chart.setChartNumber(chartNumber);
        chart.setInternationalNumber(internationalNumber);
        chart.setActive(active);

        DataFilter compFilter = filter.forComponent(Chart.class);
        if (compFilter.includeGeometry()) {
            chart.setGeometry(JtsConverter.fromJts(geometry));
        }
        if (compFilter.includeField(DataFilter.DETAILS)) {
            chart.setHorizontalDatum(horizontalDatum);
            chart.setScale(scale);
            chart.setName(name);
        }
        return chart;
    }

    /**
     * Checks if the values of the chart has changed.
     * Only checks relevant values, not e.g. database id, created date, etc.
     * Hence we do not use equals()
     *
     * @param template the template to compare with
     * @return if the chart has changed
     */
    @Transient
    public boolean hasChanged(Chart template) {
        return !Objects.equals(chartNumber, template.getChartNumber()) ||
                !Objects.equals(internationalNumber, template.getInternationalNumber()) ||
                !Objects.equals(active, template.isActive()) ||
                !Objects.equals(horizontalDatum, template.getHorizontalDatum()) ||
                !Objects.equals(scale, template.getScale()) ||
                !Objects.equals(name, template.getName()) ||
                geometryChanged(template);
    }


    /** Checks if the geometry has changed */
    protected boolean geometryChanged(Chart template) {
        if (geometry == null && template.getGeometry() == null) {
            return false;
        } else if (geometry == null || template.getGeometry() == null) {
            return true;
        }
        return !geometry.equals(template.getGeometry());
    }

    /**
     * Returns a string representation of the chart including chart number and international number
     * @return a string representation of the chart
     */
    public String toFullChartNumber() {
        return (internationalNumber == null)
                ? chartNumber
                : String.format("%s (INT %d)", chartNumber, internationalNumber);
    }


    /**
     * Computes a text search sort key for the given term.
     * Used to sort a list of matching charts, i.e. where one of "chartNumber", "internationalNumber" and "name"
     * is known to match. Use e.g. with search result from "Chart.searchCharts".
     * Prioritize, so that chartNumber rates higher than name match, which rates higher than internationalNumber match
     */
    public int computeSearchSortKey(String term) {
        if (StringUtils.isBlank(term)) {
            return 0;
        }
        term = term.toLowerCase();
        int indexChartNumber = chartNumber.toLowerCase().indexOf(term);
        int indexInternationalNumber = internationalNumber == null ? -1 : String.valueOf(internationalNumber).indexOf(term);
        int indexName = name == null ? -1 : name.toLowerCase().indexOf(term);

        int sortKey = 0;
        if (indexInternationalNumber != -1) {
            sortKey += 100 - indexInternationalNumber;
        }
        if (indexName != -1) {
            sortKey += 500 - indexName;
        }
        if (indexChartNumber != -1) {
            sortKey += 1000 - indexChartNumber;
        }
        return -sortKey;
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getChartNumber() {
        return chartNumber;
    }

    public void setChartNumber(String chartNumber) {
        this.chartNumber = chartNumber;
    }

    public Integer getInternationalNumber() {
        return internationalNumber;
    }

    public void setInternationalNumber(Integer internationalNumber) {
        this.internationalNumber = internationalNumber;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Geometry getGeometry() {
        return geometry;
    }

    public void setGeometry(Geometry geometry) {
        this.geometry = geometry;
    }

    public String getHorizontalDatum() {
        return horizontalDatum;
    }

    public void setHorizontalDatum(String horizontalDatum) {
        this.horizontalDatum = horizontalDatum;
    }

    public Integer getScale() {
        return scale;
    }

    public void setScale(Integer scale) {
        this.scale = scale;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
