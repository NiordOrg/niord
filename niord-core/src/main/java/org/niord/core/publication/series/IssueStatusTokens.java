/*
 * Copyright 2026 Danish Maritime Authority.
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

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * An issue status named in either vocabulary.
 *
 * Two frontends are in service at once and they speak different words for the
 * same three states: the old one says ACTIVE / RECORDING / DRAFT / INACTIVE
 * because that is what a legacy publication's status column held, the new one
 * says PUBLISHED / OPEN / RETIRED. A picker that accepted only the new words
 * would answer the shipped query -- literally {@code status=ACTIVE&status=RECORDING}
 * -- with nothing, and the citation dialog would come back empty for reasons
 * nothing in the response explains.
 *
 * ACCEPTED, NOT EMITTED. Every response says PUBLISHED / OPEN / RETIRED; this
 * only widens what may be asked for. Translating on the way out as well would
 * make the wire vocabulary depend on how the caller phrased the question.
 *
 * An unrecognised token is a refusal rather than a silent drop, because a
 * dropped status filter widens a list rather than narrowing it -- a picker asked
 * for released issues would quietly offer drafts. {@code IssueStatus.valueOf}
 * raises IllegalArgumentException, which reaches a caller as a 500 saying
 * nothing, so the parse is here rather than at each call site.
 */
public final class IssueStatusTokens {

    private IssueStatusTokens() {
    }

    /** One token, in either vocabulary. */
    public static IssueStatus parse(String token) {
        if (token == null || token.isBlank()) {
            throw new IssueLifecycleService.TransitionRefusedException("INVALID_STATUS",
                    "a status token is required");
        }
        return switch (token.trim().toUpperCase()) {
            case "OPEN", "DRAFT", "RECORDING" -> IssueStatus.OPEN;
            case "PUBLISHED", "ACTIVE" -> IssueStatus.PUBLISHED;
            case "RETIRED", "INACTIVE" -> IssueStatus.RETIRED;
            default -> throw new IssueLifecycleService.TransitionRefusedException("INVALID_STATUS",
                    "'" + token + "' is not an issue status; the states are OPEN, PUBLISHED and "
                            + "RETIRED, and the older words DRAFT, RECORDING, ACTIVE and INACTIVE "
                            + "are accepted for them");
        };
    }

    /**
     * A repeated status parameter as a set, or the fallback when none was given.
     *
     * The fallback is the caller's, never a default hidden here: what "no status"
     * means differs per endpoint, and an endpoint that inherited someone else's
     * default would widen or narrow its list for reasons its own code does not
     * show.
     */
    public static Set<IssueStatus> parseAll(Collection<String> tokens, Set<IssueStatus> fallback) {
        if (tokens == null || tokens.isEmpty()) {
            return fallback;
        }
        Set<IssueStatus> out = new LinkedHashSet<>();
        for (String token : tokens) {
            // A repeated query parameter arrives as separate values, but a client
            // that comma-joined them instead means the same thing and is not
            // asking for a status literally called "PUBLISHED,OPEN".
            for (String part : token.split(",")) {
                if (!part.isBlank()) {
                    out.add(parse(part));
                }
            }
        }
        return out.isEmpty() ? fallback : out;
    }
}
