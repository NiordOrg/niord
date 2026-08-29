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

/**
 * Who, besides the owner, may SEE and CITE a publication.
 *
 * Separate from the owner, and the separation is the whole point. The owner is
 * the one domain a publication is listed in, administered from, and whose
 * timezone its cut-offs are read in -- exactly one answer, and every series has
 * one. Availability answers a different question: which other desks may point at
 * this publication from a message they are writing. A journal-number reference is
 * needed from every domain and belongs to none of them in an editorial sense, and
 * before this the only way to express that was to leave the publication with no
 * domain at all -- which took its timezone away with it.
 *
 * Availability NEVER grants a write. A shared publication is read-only wherever
 * it is shared: the write guard compares the owner and nothing else, so a domain
 * that can cite a series still cannot retire it, rename it or take it off the
 * public list.
 *
 * It also never reaches the public site. What goes public is decided by the
 * publication category's own publish flag, which is domain-blind.
 */
public enum SeriesAvailability {

    /**
     * The owner's desk only. The default for a generated series, whose issues are
     * assembled from that domain's own messages and mean little anywhere else.
     */
    OWNER_ONLY,

    /**
     * The owner, plus the domains named in the availability list.
     *
     * An empty list is refused rather than treated as OWNER_ONLY: the two are
     * different intentions, and silently collapsing one into the other hides a
     * half-finished edit behind a working screen.
     */
    SELECTED_DOMAINS,

    /**
     * Every domain. What the citation-only publications need, and what the old
     * model expressed by carrying no domain at all.
     */
    ALL_DOMAINS;

    /**
     * What a publication is shared as when nobody says.
     *
     * DECIDED FROM WHAT IT IS. A generated series is assembled from one domain's
     * messages over that domain's cut-off calendar, so its editions mean that
     * desk's week and nothing else. Everything else -- an uploaded document, an
     * external link, a publication with no content model at all -- is a reference
     * somebody points at, and that is how the whole catalogue behaved before this
     * field existed, because there was no way to narrow one.
     *
     * ONE ANSWER FOR THREE CALLERS: the create form's server-side template, a
     * series arriving through the interchange import, and the legacy importer.
     * They have to agree, or a publication authored on a screen and the same
     * publication restored from a file would be shared differently -- and nothing
     * in either path would look wrong.
     */
    public static SeriesAvailability defaultFor(ContentMode contentMode) {
        return contentMode == ContentMode.GENERATED_FROM_QUERY ? OWNER_ONLY : ALL_DOMAINS;
    }
}
