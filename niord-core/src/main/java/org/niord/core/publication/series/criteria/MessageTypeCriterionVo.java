package org.niord.core.publication.series.criteria;

/** A Type enum name, one of eight. */
public class MessageTypeCriterionVo extends IssueCriterionVo {

    @Override
    public CriterionKind kind() {
        return CriterionKind.MESSAGE_TYPE;
    }
}
