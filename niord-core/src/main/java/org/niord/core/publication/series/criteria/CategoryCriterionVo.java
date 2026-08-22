package org.niord.core.publication.series.criteria;

/** A Category MRN, never a numeric surrogate id. Matches self-or-descendant. */
public class CategoryCriterionVo extends IssueCriterionVo {

    @Override
    public CriterionKind kind() {
        return CriterionKind.CATEGORY;
    }
}
