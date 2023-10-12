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
import org.locationtech.jts.geom.Geometry;
import org.niord.core.chart.vo.SystemChartVo;
import org.niord.core.geojson.JtsConverter;
import org.niord.core.model.VersionedEntity;
import org.niord.model.DataFilter;
import org.niord.model.message.ChartVo;

import jakarta.persistence.Cacheable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a chart
 */
@Entity
@Cacheable
@Table(indexes = {
        @Index(name = "chart_chart_number", columnList="chartNumber", unique = true)
})
@NamedQueries({
        @NamedQuery(name="Chart.findByLegacyId",
                query = "select c FROM Chart c where c.legacyId = :legacyId"),

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

    public static Pattern CHART_FORMAT = Pattern.compile(
                "^" +
                "(?<chartNumber>\\w+)" +
                "([ ]*\\(INT (?<intNumber>\\d+)\\))?" +
                "$",
            Pattern.CASE_INSENSITIVE
    );

    String legacyId;

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
        this.scale = chart.getScale();
        this.name = chart.getName();

        if (chart instanceof SystemChartVo) {
            SystemChartVo sysChart = (SystemChartVo)chart;
            this.geometry = JtsConverter.toJts(sysChart.getGeometry());
            this.horizontalDatum = sysChart.getHorizontalDatum();
        }
    }


    /** Converts this entity to a value object */
    public <C extends ChartVo> C toVo(Class<C> clz) {
        return toVo(clz, DataFilter.get().fields(DataFilter.ALL));
    }


    /** Converts this entity to a value object */
    public <C extends ChartVo> C toVo(Class<C> clz, DataFilter filter) {

        C chart = newInstance(clz);
        chart.setChartNumber(chartNumber);
        chart.setInternationalNumber(internationalNumber);
        chart.setActive(active);
        chart.setScale(scale);

        DataFilter compFilter = filter.forComponent(Chart.class);
        if (compFilter.includeDetails()) {
            chart.setName(name);
        }

        if (chart instanceof SystemChartVo) {
            SystemChartVo sysChart = (SystemChartVo)chart;

            if (compFilter.includeGeometry()) {
                sysChart.setGeometry(JtsConverter.fromJts(geometry));
            }
            if (compFilter.includeDetails()) {
                sysChart.setHorizontalDatum(horizontalDatum);
            }
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

    /**
     * Parses the chart spec into a chart template, or null if not valid
     * @param chartSpec the chart spec to parse
     * @return the parsed chart
     */
    public static Chart parse(String chartSpec) {
        if (StringUtils.isNotBlank(chartSpec)) {
            Matcher m = Chart.CHART_FORMAT.matcher(chartSpec.trim());
            if (m.find()) {
                try {
                    Chart chart = new Chart();
                    chart.setChartNumber(m.group("chartNumber"));
                    if (StringUtils.isNumeric(m.group("intNumber"))) {
                        chart.setInternationalNumber(Integer.valueOf(m.group("intNumber")));
                    }
                    return chart;
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    /*************************/
    /** Getters and Setters **/
    /***/

    public String getLegacyId() {
        return legacyId;
    }

    public void setLegacyId(String legacyId) {
        this.legacyId = legacyId;
    }

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
