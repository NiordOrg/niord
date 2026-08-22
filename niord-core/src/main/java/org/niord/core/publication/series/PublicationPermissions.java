package org.niord.core.publication.series;

/**
 * The curation permission.
 *
 * OQ-1: the realm does not carry PUBLICATION_CURATE yet, and that change is
 * outside this repository and outside this project's control. Rather than block
 * the feature on it indefinitely, the check accepts ADMIN as well -- documented,
 * dated, and to be removed one release after the realm gains the role.
 *
 * The fallback is written here, once, so removing it later is deleting one
 * method rather than hunting for every call site that inlined the same OR.
 */
public final class PublicationPermissions {

    /** The role, once it exists in the realm. */
    public static final String PUBLICATION_CURATE = "publication-curate";

    /** The transitional fallback. REMOVE one release after the realm change. */
    public static final String ADMIN = "admin";

    /** Dated so it does not quietly become permanent. */
    public static final String FALLBACK_ADDED = "2026-08-22";

    private PublicationPermissions() {
    }

    /**
     * Whether the caller may curate an issue's membership.
     *
     * @param hasRole a predicate over the caller's roles, so this stays testable
     *                without a security context
     */
    public static boolean mayCurate(java.util.function.Predicate<String> hasRole) {
        if (hasRole == null) {
            return false;
        }
        return hasRole.test(PUBLICATION_CURATE) || hasRole.test(ADMIN);
    }

    /** True while the transitional fallback is still in place. */
    public static boolean fallbackActive() {
        return true;
    }
}
