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

package org.niord.core.message;

import java.util.Collection;

/**
 * Resolves {@code publication=} ids to the set of messages they designate.
 *
 * Narrow on purpose, and it points this way on purpose. The publication service
 * already injects {@link MessageService}; having the message service inject it
 * back would close a cycle. So the message search declares what it needs -- a
 * designation -- and the publication package supplies it.
 *
 * It hands back data rather than a JPA predicate: building the predicate needs
 * the criteria builder and the message root, both of which belong to the search,
 * and pushing them across this boundary would put query internals into the
 * publication package.
 */
public interface PublicationMemberSetSource {

    /** Which tier the answer is for. A public caller sees less, and is told less. */
    enum Audience {

        /** Anonymous, or an endpoint that is public whoever calls it. */
        PUBLIC,

        /** An authenticated caller inside the application. */
        INTERNAL
    }

    /**
     * Resolves the ids, or refuses.
     *
     * Resolution order never widens: a new-model issue first, then a legacy
     * publication, then failure. An id that resolves to nothing the caller may
     * see is refused rather than ignored -- ignoring it is what turns a typo into
     * "every published message".
     *
     * @param publicationIds the ids from the {@code publication=} parameter
     * @param audience       the tier the answer is for
     * @return the designation, or {@link MemberSetDesignation#NONE} if no id was given
     * @throws org.niord.core.publication.series.IssueLifecycleService.TransitionRefusedException
     *         with code {@code PUBLICATION_UNRESOLVABLE} if any id does not resolve
     */
    MemberSetDesignation designate(Collection<String> publicationIds, Audience audience);
}
