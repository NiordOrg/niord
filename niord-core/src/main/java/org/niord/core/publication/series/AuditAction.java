package org.niord.core.publication.series;

/**
 * Every action that may appear in the audit trail.
 *
 * A closed vocabulary, declared once. An action spelled differently at two call
 * sites is two actions as far as any reader is concerned, and the history panel
 * would show one of them as an unknown event. Being an enum rather than a set of
 * string literals moves that check from a runtime guard to the compiler: a typo
 * is a build failure at the call site instead of an exception thrown into the
 * business transaction that was trying to record it.
 *
 * Persisted by NAME into a varchar column, and rendered onto the wire by name
 * too, so the stored and transmitted vocabulary is unchanged by the typing. That
 * also means a constant may be added freely, but never renamed or removed
 * without a data migration -- rows already carry the old spelling.
 *
 * The sibling column actorKind is a native database enum and cannot take a new
 * value without an ALTER TABLE; this one can. The difference is deliberate: the
 * actor kinds are three and settled, the actions grow with the feature.
 */
public enum AuditAction {

    CREATED,
    CREATED_FROM_PREVIOUS_PUBLISH,
    CREATED_RETROACTIVELY,
    CREATED_NEW_EDITION,
    PUBLISHED,
    AMENDED,
    RETIRED,
    REACTIVATED,
    SUPERSEDED_BY,
    DELETED,

    // Specific, never a generic UPDATE: a history panel cannot render "something
    // changed", and each of these carries the before and after that makes the
    // line answer the question it was opened for.
    INTERVAL_CHANGED,
    NAME_CHANGED,
    CRITERIA_OVERRIDDEN,
    OVERRIDE_INCLUDED,
    OVERRIDE_EXCLUDED,
    OVERRIDE_REMOVED,
    FILE_UPLOADED,

    /**
     * A document that was already released, overwritten by hand. Distinct from an
     * upload because the trail has to say whether a file appeared or a cited one
     * was replaced -- the archive path on that entry is the only route back to
     * what the public was reading before.
     */
    FILE_REPLACED_MANUALLY,
    FILE_CLEARED,

    // A link is the published artefact for an external publication, exactly as a
    // file is for a hosted one, so changing one is as much a change to what the
    // public sees as replacing the other.
    LINK_SET,
    LINK_CLEARED,

    PREVIEW_GENERATED,
    WINDOW_ADJUSTED,

    /**
     * The public window closed by a NEIGHBOUR's publish -- the predecessor capped
     * at this stamp, or this issue capped at a successor that had already
     * published. Written on the issue whose window moved, because that is where
     * somebody looks when a publication left the site.
     */
    VISIBILITY_CAPPED,

    IMPORTED,
    SERIES_ACTIVATED,
    SERIES_RETIRED,

    /**
     * Which model answers the public for this series. Visible to every anonymous
     * reader the moment it changes, so it is its own action.
     */
    SERIES_AUTHORITY_CHANGED
}
