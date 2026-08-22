package org.niord.web.publication;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every error code the publication API can return, with its HTTP status.
 *
 * One place, and one status per code. A code that means 409 at one endpoint and
 * 400 at another is two codes wearing one name, and a client cannot branch on
 * it -- which is how "retry on 409" turns into a retry loop against a 400 that
 * will never succeed.
 *
 * The catalogue is also what stops the status being chosen inline at each throw
 * site, where it drifts silently.
 */
public final class PublicationErrorCatalogue {

    private static final Map<String, Integer> STATUS = new LinkedHashMap<>();

    static {
        // 400 -- the request itself is wrong and retrying it unchanged cannot help.
        put("CRITERIA_INVALID", 400);
        put("UNKNOWN_TOKEN", 400);
        put("EMPTY_OPERAND", 400);
        put("UNRESOLVABLE_OPERAND", 400);
        put("OVERRIDE_REASON_REQUIRED", 400);
        put("NO_SUCH_LANGUAGE", 400);
        put("RETRO_CREATE_NOT_APPLICABLE", 400);
        put("SERIES_INVALID", 400);

        // 404 -- nothing of that identity exists.
        put("SERIES_NOT_FOUND", 404);
        put("ISSUE_NOT_FOUND", 404);
        put("CATEGORY_NOT_FOUND", 404);

        // 409 -- the request is well formed, but the thing is in the wrong state.
        // Distinct from 400 because the SAME request may succeed later.
        put("ISSUE_ALREADY_PUBLISHED", 409);
        put("ISSUE_NOT_PUBLISHED", 409);
        put("ISSUE_NOT_RETIRED", 409);
        put("ISSUE_NOT_OPEN", 409);
        put("ISSUE_NOT_DELETABLE", 409);
        put("SERIES_HAS_ISSUES", 409);
        put("PREDECESSOR_NOT_PUBLISHED", 409);
        put("WARNING_NOT_ACKNOWLEDGED", 409);
        put("SERIES_ID_TAKEN", 409);
        put("CATEGORY_IN_USE", 409);

        // 500 -- the server could not do what it was asked, and the caller did
        // nothing wrong. ARCHIVE_FAILED is here deliberately: it aborts a publish,
        // and presenting it as a client error would invite a retry that fails the
        // same way.
        put("ARCHIVE_FAILED", 500);
        put("FILE_WRITE_FAILED", 500);
        put("RENDER_FAILED", 500);
    }

    private static void put(String code, int status) {
        if (STATUS.containsKey(code)) {
            throw new IllegalStateException("duplicate error code: " + code);
        }
        STATUS.put(code, status);
    }

    private PublicationErrorCatalogue() {
    }

    /** The status for a code. Unknown codes are 500: an unmapped error is a bug here. */
    public static int statusOf(String code) {
        return STATUS.getOrDefault(code, 500);
    }

    public static boolean knows(String code) {
        return STATUS.containsKey(code);
    }

    public static Map<String, Integer> all() {
        return Map.copyOf(STATUS);
    }
}
