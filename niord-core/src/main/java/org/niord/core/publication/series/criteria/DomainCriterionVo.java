package org.niord.core.publication.series.criteria;

/** A Domain.domainId. This node is a MACRO, not a predicate: the resolver expands it to a messageSeries set before the query runs, and freezes the expansion into the snapshot. */
public class DomainCriterionVo extends IssueCriterionVo {

    @Override
    public CriterionKind kind() {
        return CriterionKind.DOMAIN;
    }
}
