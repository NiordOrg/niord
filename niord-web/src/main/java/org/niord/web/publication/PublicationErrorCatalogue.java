package org.niord.web.publication;

import org.niord.core.publication.series.PublicationDomainGuard;
import org.niord.core.publication.series.StaleVersionGuard;

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

        // B1.7b. Three guards that had no code until the invariant gate ran, so
        // they had no catalogue entry either -- and an uncatalogued code returns
        // 500 from an endpoint, which is the failure this catalogue exists to
        // prevent.
        //
        // 409 for both override refusals: each names a CONFLICT with something
        // that already exists -- a membership the criteria already grant, or the
        // absence of the message being named -- rather than a malformed request.
        put("OVERRIDE_ALREADY_A_MEMBER", 409);
        put("OVERRIDE_MESSAGE_NOT_FOUND", 409);

        // 409 as well: the upload is well formed, and conflicts with the file
        // name another language already holds in the same folder.
        put("FILE_NAME_NOT_DISTINCT", 409);
        // 400, not 409: a released document's address is fixed for its lifetime,
        // so no change of state makes the same request correct. The bytes may be
        // replaced in place -- that is the correction path -- but the name and the
        // path may not move, because every stored citation is a URL into them and
        // a rename leaves those pointing at the file that was just superseded.
        put("FILE_NAME_IMMUTABLE", 400);
        put("NO_SUCH_LANGUAGE", 400);
        put("RETRO_CREATE_NOT_APPLICABLE", 400);
        put("SERIES_INVALID", 400);

        // 400, not 409: a state conflict may succeed once the state changes, and
        // this one never will. seriesId is the import/export key and the citation
        // handle, so it is immutable after create -- retrying the same rename is
        // futile and a client that treats it as transient would loop.
        put("SERIES_ID_IMMUTABLE", 400);
        // 400 and not 409, though it reads like a state conflict. The 409 group
        // means "the same request may succeed later"; this one never can. Once a
        // series has released an issue, the field is fixed for its lifetime, and
        // a client that retried on 409 would retry forever.
        put("MESSAGE_PUBLICATION_IMMUTABLE", 400);
        // A publication= id that resolves to nothing the caller may see. 400
        // rather than 404 because it is one parameter of a search, not the
        // resource being addressed -- and the same 400 whether the id is a typo
        // or an issue this caller is not allowed to know exists.
        put("PUBLICATION_UNRESOLVABLE", 400);
        // Citing a publication into a language it has no format for. The caller
        // named the language, so the request is the thing that is wrong -- and
        // retrying it unchanged cannot help until somebody adds the format.
        put("CITATION_FORMAT_MISSING", 400);

        // The issue edit (I8). Both are 400: the request describes something that
        // cannot be right whatever the issue's state, so the same request never
        // succeeds later.
        //
        // An interval that ends before it starts selects nothing, and the issue
        // would publish EMPTY rather than fail -- which is why it is refused at
        // the edge rather than left to produce a document with no contents.
        put("INTERVAL_INVERTED", 400);
        // The name column is NOT NULL precisely because a nameless issue is
        // unfindable in every list that shows it, and "" clears it as well as null.
        put("NAME_BLANK", 400);
        // An override on a series that does not select by criteria would decide
        // nothing. 400: the request is wrong about what the series is, and
        // resending it cannot become right while the content mode stands.
        put("CRITERIA_NOT_APPLICABLE", 400);

        // The upload (I24). All 400 -- the multipart body itself is wrong, and
        // re-posting the same body cannot help.
        put("NO_FILE", 400);
        // One language holds one document, so several files is ambiguous rather
        // than generous: taking whichever the map iterated first would publish a
        // document nobody chose.
        put("TOO_MANY_FILES", 400);
        put("NO_FILE_NAME", 400);
        // A name that is only a path. Stripping it leaves nothing to write to, and
        // inventing one would put a document on the public site under a name
        // nobody chose.
        put("BAD_FILE_NAME", 400);

        // 403 -- the caller is an admin, and this is not theirs to write.
        //
        // The only 403 in the catalogue, and it is not the same refusal as the
        // role gate above it. A missing role is answered by the container before
        // any of this runs and cannot be fixed by the caller at all; THIS one
        // says the caller is administering the wrong domain, which they fix by
        // switching domain. Collapsing the two into one status would send an
        // admin to ask for a role they already hold.
        //
        // Not 404: the series is readable, it is listed, and the screen the
        // caller is looking at is showing it. Pretending it does not exist would
        // contradict the response they just rendered.
        put(PublicationDomainGuard.NOT_IN_DOMAIN, 403);

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
        // 409: the series is a one-off and already holds its one issue. A state
        // conflict that CAN clear -- reclassifying the series as UNSCHEDULED makes
        // the same request succeed -- so it is 409 rather than 400.
        put("SERIES_IS_ONE_OFF", 409);
        // 400, not 409: the one-off form was pointed at a scheduled or unscheduled
        // series. No change of state makes that request correct -- the form has no
        // fields for the cadence, criteria and numbering such a series carries, so
        // saving through it would silently drop them. The caller wants the series
        // editor instead.
        put("SERIES_NOT_ONE_OFF", 400);
        put("PREDECESSOR_NOT_PUBLISHED", 409);
        put("WARNING_NOT_ACKNOWLEDGED", 409);
        put("CUTOFF_IN_FUTURE", 400);
        put("NO_PREVIEW", 404);
        put("REPORT_NOT_CONFIGURED", 409);
        put("SERIES_ID_TAKEN", 409);
        put("CATEGORY_IN_USE", 409);
        // A period a released issue already covered. 409 rather than 400: the
        // request is well formed, and a different period would succeed.
        put("ISSUE_INTERVAL_OVERLAP", 409);
        // An action that changes what the public reads must say why. 400: no
        // retry of the same request can succeed.
        put("REASON_REQUIRED", 400);
        put("INVALID_STATUS_TRANSITION", 400);
        put("INVALID_STATUS", 400);
        // A setting the model carries and the system cannot yet honour.
        put("NOT_YET_SUPPORTED", 400);
        put("PRINT_SETTING_NOT_ALLOWED", 400);
        // A curation decision that named nothing, too much, or a message the
        // issue carries no decision about.
        put("NO_MESSAGES", 400);
        put("TOO_MANY_MESSAGES", 400);
        put("OVERRIDE_NOT_FOUND", 404);
        // The cutover flip, refused. 409: the request is well formed and the
        // same one succeeds once the evidence is there.
        put("NOT_READY_FOR_CUTOVER", 409);
        put("SERIES_NOT_ACTIVE", 409);
        put("CATEGORY_INVALID", 400);
        put("CATEGORY_ID_TAKEN", 409);
        // The interchange key a series stores, and the key an import upserts on.
        // Renaming it through an update would rename the thing every series row
        // points at, so the path wins and a body disagreeing with it is refused
        // rather than quietly ignored -- a caller that believes it renamed a
        // category and did not is worse off than one that got an error.
        put("CATEGORY_ID_IMMUTABLE", 400);
        // An authority token that is not NEW or LEGACY: a client error, not a
        // server failure.
        put("INVALID_AUTHORITY", 400);
        // An imported issue is a historical record; re-deciding it is not an
        // action that exists.
        put("ISSUE_IMPORTED", 409);

        // 409: the row moved under the caller between the read that filled their
        // form and the write. The same request never succeeds again -- the version
        // it names is gone for good -- but the ACTION does, once the caller has
        // re-read and re-applied it, which is the "may succeed later" the 409 group
        // means. Deliberately not 412: the version travels in the body rather than
        // in a conditional header, so there is no precondition to have failed, and
        // a client branching on 412 would look for an ETag that was never sent.
        put(StaleVersionGuard.STALE_VERSION, 409);

        // The release rail, enforced. Every BLOCK row an admin can be shown has a
        // code here, because a refusal the screen can render as a sentence and the
        // client cannot branch on is a refusal that gets retried.
        //
        // 400: the interval does not match what the series' time relation
        // requires. No change of state makes the same request right -- an in-force
        // issue carrying a lower bound is wrong about what kind of publication it
        // belongs to.
        put("INTERVAL_INVALID", 400);
        // 409: uploaded and link-backed content has to exist before it is
        // released, and publish does not write it. The same request succeeds once
        // the bytes or the link are there. Deliberately NOT raised for the
        // generated languages of a query-backed series, whose file publish itself
        // writes -- gating those here would make the first publish of every weekly
        // issue impossible.
        put("MISSING_FILE_FOR_LANGUAGE", 409);
        // 400: a citable series needs a reference format in every configured
        // language, or a message citing the issue renders a blank citation. The
        // format is a series field, so the request is wrong until somebody edits
        // the series -- not a state that clears on its own.
        put("REFERENCE_FORMAT_MISSING_LANGUAGE", 400);
        // 400 for both cut-off bracket violations, and 400 rather than 409 for the
        // same reason: the instant is IN the request. Re-sending it unchanged
        // fails identically; choosing another one succeeds. Until now the
        // half-open Interval raised an IllegalArgumentException here, which no
        // mapper knew, so an admin choosing a cut-off one second too early got a
        // bare 500.
        put("CUTOFF_BEFORE_PREVIOUS", 400);
        put("CUTOFF_AFTER_SUCCESSOR", 400);
        // 409: a loud stop rather than a truncation. An official publication
        // silently missing its tail is worse than one that was not released, and
        // the same request succeeds once the period or the criteria are narrowed.
        put("MEMBER_LIMIT_EXCEEDED", 409);

        // The three consumer endpoints. All 400: each names a bound on the
        // REQUEST rather than a state of anything, so the same request never
        // succeeds later and a client retrying it would loop.
        //
        // Hydration is by ids the caller already holds, which is a handful per
        // message. An unbounded IN-list on an anonymous endpoint is a query
        // anybody can make arbitrarily expensive.
        put("TOO_MANY_IDS", 400);
        // The timeline never scans the estate. Asking for no series is asking for
        // nothing, and answering it with everything would turn a dashboard strip
        // into an enumeration of the whole catalogue.
        put("NO_SERIES_IDS", 400);
        put("TOO_MANY_SERIES_IDS", 400);
        // A filter value that is not a member of the enum it names. 400 rather
        // than a 500 from valueOf, which is the pattern this catalogue exists to
        // stop -- and a silently dropped filter would WIDEN a list rather than
        // narrow it, so it cannot be ignored either.
        put("INVALID_FILTER_VALUE", 400);

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
