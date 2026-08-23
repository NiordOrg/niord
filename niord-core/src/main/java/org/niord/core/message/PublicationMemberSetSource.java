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
