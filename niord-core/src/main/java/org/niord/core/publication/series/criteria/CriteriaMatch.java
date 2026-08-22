package org.niord.core.publication.series.criteria;

/**
 * How the nodes of a document combine.
 *
 * Only ALL exists. It is declared as an enum rather than left implicit so that
 * adding ANY later is additive and does not bump the schema version. All four
 * production filters are top-level conjunctions; the single disjunction in the
 * estate (type == T or type == P) is expressed inside a set-valued node instead.
 */
public enum CriteriaMatch {
    ALL
}
