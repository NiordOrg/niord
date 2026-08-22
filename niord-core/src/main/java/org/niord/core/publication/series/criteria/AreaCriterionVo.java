package org.niord.core.publication.series.criteria;

/** An Area MRN, never a numeric surrogate id. Matches self-or-descendant by lineage. */
public class AreaCriterionVo extends IssueCriterionVo {

    @Override
    public CriterionKind kind() {
        return CriterionKind.AREA;
    }
}
