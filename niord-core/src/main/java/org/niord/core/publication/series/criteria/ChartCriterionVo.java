package org.niord.core.publication.series.criteria;

/** A Chart.chartNumber, matched exactly and case-sensitively. */
public class ChartCriterionVo extends IssueCriterionVo {

    @Override
    public CriterionKind kind() {
        return CriterionKind.CHART;
    }
}
