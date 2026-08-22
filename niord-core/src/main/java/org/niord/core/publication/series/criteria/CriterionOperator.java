package org.niord.core.publication.series.criteria;

/**
 * How a criterion's operand list is applied.
 *
 * NOT_IN is defined here but rejected by the validator. Keeping it in the schema
 * means adding it later is purely additive; rejecting it means no untested
 * predicate ships. No production filter negates anything.
 */
public enum CriterionOperator {
    IN,
    NOT_IN
}
