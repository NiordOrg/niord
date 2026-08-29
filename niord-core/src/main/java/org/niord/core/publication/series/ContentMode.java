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

import org.niord.model.publication.PublicationType;

/**
 * What an issue's content IS, and therefore whether it has membership semantics at all.
 * Roughly 48 publications have no membership of any kind, so this is declared rather than inferred
 * from whether a criteria document happens to be present.
 *
 * Persisted as a native MySQL ENUM, which REJECTS values outside this list -- adding a constant
 * later needs an ALTER TABLE.
 *
 * It also carries the bijection onto {@link PublicationType}, the vocabulary the published
 * publication API speaks. The two directions live side by side on purpose: a mapping and its
 * inverse written in two files drift, and the drift shows up as a search that narrows on one
 * meaning while the payload reports another.
 */
public enum ContentMode {
    GENERATED_FROM_QUERY,
    UPLOADED_FILE,
    EXTERNAL_LINK,
    NONE;

    /**
     * The publication type a series of this content mode publishes.
     *
     * Total and bijective, which is why nothing stores it: a stored column can
     * disagree with the mode beside it, a derivation cannot. It reads the SERIES'
     * declared mode and NOTHING about a particular issue -- an issue of a
     * generated series is a message report before anything has been rendered for
     * it, and it stays one after a file is attached. Deriving from what an issue
     * happens to hold makes the type a property of the upload rather than of the
     * publication: every issue carrying a link to its own document -- which is
     * every issue, since a repository file is served over one -- reports LINK, and
     * the editor's message-report picker finds nothing.
     *
     * A null mode reads as NONE. The column is not nullable, so this is reached
     * only by an issue with no series at all.
     */
    public static PublicationType publicationTypeOf(ContentMode mode) {
        if (mode == null) {
            return PublicationType.NONE;
        }
        return switch (mode) {
            case GENERATED_FROM_QUERY -> PublicationType.MESSAGE_REPORT;
            case UPLOADED_FILE -> PublicationType.REPOSITORY;
            case EXTERNAL_LINK -> PublicationType.LINK;
            case NONE -> PublicationType.NONE;
        };
    }

    /** The same bijection read the other way, so a type filter narrows on the stored column. */
    public static ContentMode ofPublicationType(PublicationType type) {
        if (type == null) {
            return NONE;
        }
        return switch (type) {
            case MESSAGE_REPORT -> GENERATED_FROM_QUERY;
            case REPOSITORY -> UPLOADED_FILE;
            case LINK -> EXTERNAL_LINK;
            case NONE -> NONE;
        };
    }
}
