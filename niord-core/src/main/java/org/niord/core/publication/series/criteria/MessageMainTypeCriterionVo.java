package org.niord.core.publication.series.criteria;

/** A MainType name: NW or NM. */
public class MessageMainTypeCriterionVo extends IssueCriterionVo {

    @Override
    public CriterionKind kind() {
        return CriterionKind.MESSAGE_MAIN_TYPE;
    }
}
