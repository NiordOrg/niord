package org.niord.core.publication.series;

/**
 * What an issue's content IS, and therefore whether it has membership semantics at all.
 * Roughly 48 publications have no membership of any kind, so this is declared rather than inferred
 * from whether a criteria document happens to be present.
 *
 * Persisted as a native MySQL ENUM, which REJECTS values outside this list -- adding a constant
 * later needs an ALTER TABLE.
 */
public enum ContentMode {
    GENERATED_FROM_QUERY,
    UPLOADED_FILE,
    EXTERNAL_LINK,
    NONE
}
