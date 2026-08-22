package org.niord.core.publication.series.criteria;

/** A MessageSeries.seriesId. Unique and not null. */
public class MessageSeriesCriterionVo extends IssueCriterionVo {

    @Override
    public CriterionKind kind() {
        return CriterionKind.MESSAGE_SERIES;
    }
}
