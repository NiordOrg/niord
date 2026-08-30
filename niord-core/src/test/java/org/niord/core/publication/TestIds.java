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

package org.niord.core.publication;

import java.util.UUID;

/**
 * Unique identifiers for the fixtures.
 *
 * The suites here commit their fixtures to a database that is shared, long-lived
 * and never truncated between runs, so every fixture identifier lands in a table
 * that already holds every identifier every previous run minted. Several of the
 * columns they land in carry a UNIQUE key -- seriesId, categoryId, username,
 * domainId -- and a repeat is not a flaky assertion but a constraint violation
 * that fails whichever test happened to draw second.
 *
 * A SHORTENED identifier is what makes that reachable. Eight hex characters is
 * thirty-two bits, and by the birthday bound a few tens of thousands of rows in
 * one column are enough to make a repeat likely rather than remarkable -- which
 * is the scale a shared database reaches in weeks. The whole value, at 128 bits,
 * does not reach that scale in the lifetime of the project.
 *
 * SEPARATORS ARE DROPPED rather than kept, so the suffix costs 32 characters
 * instead of 36 and stays inside the tightest column any of these identifiers
 * has to fit: PublicationSeries.seriesId is varchar(64), which leaves 32
 * characters for the caller's prefix. The others are varchar(255) and have room
 * to spare. Hex is also safe in a slug, a repository path and an MRN, so one
 * suffix serves every caller.
 */
public final class TestIds {

    /**
     * The narrowest column a fixture identifier has to fit.
     *
     * A prefix longer than {@code MAX_ID_LENGTH} minus 32 overflows a seriesId.
     */
    public static final int MAX_ID_LENGTH = 64;

    private TestIds() {
    }

    /** A whole random UUID as 32 hex characters, with the separators removed. */
    public static String suffix() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** The prefix verbatim -- separator included -- followed by a fresh suffix. */
    public static String id(String prefix) {
        return prefix + suffix();
    }

    /** A series identifier. */
    public static String series() {
        return id("s-");
    }

    /** A publication-category identifier. */
    public static String category() {
        return id("cat-");
    }

    /** A username. */
    public static String user() {
        return id("u-");
    }

    /** A domain identifier. */
    public static String domain() {
        return id("dom-");
    }
}
