/*
 * Copyright 2026 Danish Emergency Management Agency.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.niord.core.publication.series;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.niord.core.model.VersionedEntity;

import java.util.Map;

/**
 * Refuse a write that was composed against a row somebody else has since changed.
 *
 * THE FAILURE THIS CLOSES. Two admins open the same weekly issue. The first
 * corrects the interval and saves; the second, whose form was loaded before that,
 * saves a rename -- and because a save sends the WHOLE object, the rename carries
 * the stale interval back with it and silently undoes the correction. Neither
 * admin is told anything. The change is not lost in an audit sense -- both writes
 * are recorded -- but the second one reverted a field it never meant to touch, and
 * the only trace is a diff nobody reads.
 *
 * HOW IT IS DECIDED. Every row already carries an optimistic-lock counter, which
 * the persistence layer bumps on each update. A read hands that counter out; a
 * write hands it back; if the stored one has moved on, the body was composed
 * against a row that no longer exists in that shape and the write is refused with
 * {@link #STALE_VERSION} -- BEFORE anything is changed, so a refused write leaves
 * no partial effect behind.
 *
 * AN ABSENT VERSION IS NOT A CONFLICT. A body that carries no version keeps the
 * behaviour that was there before this guard: last write wins. That is deliberate
 * rather than lenient -- the older administration client sends no version at all,
 * and a hard requirement would take every one of its writes down at once for a
 * conflict that is not happening. A client that wants the protection asks for it
 * by echoing what it read.
 *
 * WHY THE COUNTER IS NEVER ASSIGNED FROM A BODY. It is compared and then dropped.
 * Writing a client-supplied counter onto the row would let a client claim any
 * version it liked -- including the one it is about to overwrite -- which turns
 * the guard into a field the caller controls, and a guard the caller controls is
 * not a guard.
 */
public final class StaleVersionGuard {

    /**
     * The wire code for a write composed against a row that has since moved.
     *
     * A code rather than a bare 409 because the client's remedy is specific and
     * unlike every other conflict's: re-read the entity, show the caller what
     * changed underneath them, and let them decide whether their edit still
     * applies. Blind retry is exactly the wrong response, and a status with no
     * code is what invites it.
     */
    public static final String STALE_VERSION = "STALE_VERSION";

    private StaleVersionGuard() {
    }

    /** A write whose body was composed against an older revision of the row. */
    public static class StaleVersionException extends PublicationException {

        private final int stored;

        private final Integer submitted;

        public StaleVersionException(String message, int stored, Integer submitted) {
            super(STALE_VERSION, message);
            this.stored = stored;
            this.submitted = submitted;
        }

        /** The version the row actually carries now. */
        public int stored() {
            return stored;
        }

        /** The version the request body claimed. */
        public Integer submitted() {
            return submitted;
        }
    }

    // -------------------------------------------------------------- assertions

    /** Refuse a series write composed against an older revision. */
    public static void check(PublicationSeries series, Integer bodyVersion) {
        if (series == null) {
            return;
        }
        check(series, bodyVersion, "The publication series '" + series.getSeriesId() + "'");
    }

    /** Refuse an issue write composed against an older revision. */
    public static void check(PublicationIssue issue, Integer bodyVersion) {
        if (issue == null) {
            return;
        }
        check(issue, bodyVersion, "The issue '" + issue.getPublicId() + "'");
    }

    /**
     * The comparison itself.
     *
     * A null entity is passed through untouched, exactly as the domain guard
     * passes one through: the caller looks the row up and answers its own
     * NOT_FOUND, and turning a missing row into a version conflict here would
     * hand back the wrong diagnosis for a typo in an id.
     */
    private static void check(VersionedEntity<?> entity, Integer bodyVersion, String what) {
        if (bodyVersion == null) {
            return;
        }
        int stored = entity.getVersion();
        if (bodyVersion != stored) {
            throw new StaleVersionException(what + " has been changed by somebody else since this"
                    + " form was loaded (it is now at revision " + stored + ", and this request was"
                    + " composed against revision " + bodyVersion + "). Saving would silently revert"
                    + " their change, because a save sends every field. Re-open it, see what moved,"
                    + " and apply the edit again.",
                    stored, bodyVersion);
        }
    }

    // ------------------------------------------------------------------ bodies

    /**
     * The revision named by an untyped request body, or null when it names none.
     *
     * Several of these actions take a small JSON object rather than a typed shape,
     * and a map has no field to declare. Reading the key here rather than at each
     * of those endpoints keeps one answer to what the key is called and what
     * counts as absent -- a second reader that spelled it differently would leave
     * an endpoint silently unguarded while looking guarded.
     *
     * A value that is present but is not a number is refused rather than treated
     * as absent: silently ignoring it would turn a client bug into the very
     * last-write-wins the caller was trying to opt out of.
     */
    public static Integer versionOf(Map<String, ?> body) {
        if (body == null) {
            return null;
        }
        Object raw = body.get("version");
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException e) {
            throw new IssueLifecycleService.TransitionRefusedException("INVALID_FILTER_VALUE",
                    "'version' names the revision this request was composed against and must be a"
                            + " whole number; '" + text + "' is not one. Sending nothing is how a"
                            + " client opts out of the check.");
        }
    }

    // ------------------------------------------------------------------- bumps

    /**
     * Move an issue's revision on because something ABOUT it changed that is not
     * stored ON it.
     *
     * Curation writes child rows -- one per message a curator added or removed --
     * and a child row insert does not touch the parent's counter. Without this the
     * guard above would be decorative on exactly the endpoints that need it most:
     * two curators both read revision 7, both write children, both commit at
     * revision 7, and the second one's decision silently replaces the first's in a
     * member list neither of them will re-read.
     *
     * The forced increment is what makes the collision real, and it belongs here
     * rather than beside each override write so there is one answer to "what
     * counts as changing an issue".
     */
    public static void forceIncrement(EntityManager em, PublicationIssue issue) {
        if (em == null || issue == null) {
            return;
        }
        PublicationIssue managed = em.contains(issue) ? issue : em.merge(issue);
        em.lock(managed, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
    }
}
