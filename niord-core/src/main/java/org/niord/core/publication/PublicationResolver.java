package org.niord.core.publication;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.niord.core.domain.Domain;
import org.niord.core.message.MemberSetDesignation;
import org.niord.core.message.PublicationMemberSetSource;
import org.niord.core.publication.series.IssueLifecycleService.TransitionRefusedException;
import org.niord.core.publication.series.IssuePublicationMapping;
import org.niord.core.publication.series.IssueStatus;
import org.niord.core.publication.series.PublicationIssue;
import org.niord.core.publication.vo.PublicationStatus;
import org.niord.core.publication.vo.SystemPublicationVo;
import org.niord.core.service.BaseService;
import org.niord.model.DataFilter;
import org.niord.model.publication.PublicationVo;
import org.niord.model.publication.PublicationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The one place a {@code publication=} id is turned into something.
 *
 * There were two independent copies of this resolution -- one in the
 * authenticated message search, one in the public message API -- and a third
 * path, the mailing-list triggers, that parsed the parameter and then discarded
 * it. Three implementations of one rule is three chances to disagree, and they
 * did.
 *
 * THE DEFECT THIS CLOSES. Today an id that resolves to nothing is silently
 * dropped: the publication contributes no tag, no tag means no tag filter, no
 * tag filter means the default widening, and the caller receives every published
 * message. A typo returns 244 messages. So does every ACTIVE publication that
 * has no message tag, of which there are 27. Nothing in the response says the
 * filter was ignored.
 *
 * So resolution never widens. The order is: a new-model issue, then a legacy
 * publication, then refuse -- and refusing is an exception, not an empty result,
 * because an empty result is indistinguishable from a real one.
 *
 * NO EXISTENCE ORACLE. An id that exists but is not servable to this caller is
 * refused with byte-identical text to an id that does not exist. An anonymous
 * caller must not be able to tell a draft issue from a typo by comparing error
 * messages.
 */
@ApplicationScoped
public class PublicationResolver extends BaseService implements PublicationMemberSetSource {

    private static final Logger log = LoggerFactory.getLogger(PublicationResolver.class);

    /** The wire code. One code for both refusal reasons, deliberately. */
    public static final String UNRESOLVABLE = "PUBLICATION_UNRESOLVABLE";

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public MemberSetDesignation designate(Collection<String> publicationIds, Audience audience) {

        // Blanks are dropped BEFORE the emptiness test, not skipped inside the
        // loop. "?publication=" with no value parses to a set holding one empty
        // string, and skipping it inside the loop still returned a designation --
        // an always-false one, so a cleared filter blanked the result list and
        // suppressed the default domains at the same time. An empty parameter
        // names no publication, which is exactly what NONE means.
        Set<String> ids = publicationIds == null ? Set.of() : publicationIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (ids.isEmpty()) {
            return MemberSetDesignation.NONE;
        }

        // LinkedHashSet: the caller's order is meaningful -- the first publication
        // supplies the sort domain -- and it must not depend on hash order.
        Set<String> memberUids = new LinkedHashSet<>();
        Set<String> tagIds = new LinkedHashSet<>();
        List<String> unresolved = new ArrayList<>();

        for (String publicationId : ids) {

            // 1. The new model, first. An imported issue reuses the legacy id as
            //    its publicId, so checking legacy first would keep serving the old
            //    row forever after cutover.
            PublicationIssue issue = findIssue(publicationId);
            if (issue != null && designatable(issue, audience)) {
                memberUids.addAll(memberUids(publicationId));
                continue;
            }

            // 2. The legacy row -- reached ALSO when an issue exists but is not
            //    servable, and this is the cutover case rather than an edge case.
            //
            //    An imported issue adopts the legacy id as its publicId and sits
            //    at OPEN until it is first published. Refusing at step 1 rather
            //    than falling through would take that id dark for every anonymous
            //    caller between import and first publish -- while the legacy row
            //    is still ACTIVE and still the right answer. Every stored citation
            //    into it would break for exactly that window.
            Publication legacy = findLegacy(publicationId);
            if (legacy != null && servable(legacy, audience)) {
                // By the STORED join only. Six tags are shared between
                // publications and two are each shared by three, so resolving by
                // scanning the tag table returns the other publications' contents.
                if (legacy.getType() == PublicationType.MESSAGE_REPORT && legacy.getMessageTag() != null) {
                    tagIds.add(legacy.getMessageTag().getTagId());
                }
                // No tag is a legitimate answer: this publication designates an
                // empty member set. Zero messages -- never "no publication named".
                continue;
            }

            unresolved.add(publicationId);
        }

        if (!unresolved.isEmpty()) {
            // One id failing fails the whole request. A partial answer to a
            // multi-id query is indistinguishable from a complete one.
            log.debug("refusing search: unresolvable publication ids {} for audience {}",
                    unresolved, audience);
            throw new TransitionRefusedException(UNRESOLVABLE, unresolvableMessage(unresolved));
        }

        return new MemberSetDesignation(true, memberUids, tagIds);
    }

    /**
     * Resolves one id to a new-model issue, or null.
     *
     * Public so the citation endpoints share this resolution rather than each
     * writing their own.
     */
    @Transactional
    public PublicationIssue findIssue(String publicationId) {
        List<PublicationIssue> found = em.createQuery(
                        "SELECT i FROM PublicationIssue i WHERE i.publicId = :id", PublicationIssue.class)
                .setParameter("id", publicationId)
                .setMaxResults(1)
                .getResultList();
        return found.isEmpty() ? null : found.get(0);
    }

    /** Resolves one id to a legacy publication, or null. */
    @Transactional
    public Publication findLegacy(String publicationId) {
        List<Publication> found = em.createNamedQuery("Publication.findByPublicationId", Publication.class)
                .setParameter("publicationId", publicationId)
                .setMaxResults(1)
                .getResultList();
        return found.isEmpty() ? null : found.get(0);
    }

    /**
     * A citation, in the public shape. New model first, then legacy, then null.
     *
     * Order matters and it is the same order the search uses: an imported issue
     * carries the legacy id as its own publicId, so checking legacy first would
     * keep serving the old row forever after cutover.
     *
     * Null rather than an exception. This backs endpoints that answer 404 for an
     * unknown id, and turning that into a 400 would change a shape clients have
     * relied on since before the redesign.
     */
    @Transactional
    public PublicationVo publicVo(String publicationId, String lang, Audience audience) {
        if (publicationId == null || publicationId.isBlank()) {
            return null;
        }

        PublicationIssue issue = findIssue(publicationId);
        if (issue != null && citable(issue, audience)) {
            // The publish flag of the CATEGORY, applied on this branch exactly as
            // it is on the legacy one below. Four imported issues land in
            // non-publishing categories on day one, and without the gate the same
            // issue is absent from the public list and readable by id -- so the
            // list and the single read disagree about what is public.
            if (audience == Audience.PUBLIC && !inAPublishingCategory(issue)) {
                return null;
            }
            return IssuePublicationMapping.toPublicationVo(issue, lang);
        }

        // Falls through when the issue exists but is not servable, for the same
        // reason designate() does: between import and first publish the legacy row
        // is still the right answer, and every citation into that id depends on it.
        Publication legacy = findLegacy(publicationId);
        if (legacy == null || !servable(legacy, audience)) {
            return null;
        }
        // The publish flag of the CATEGORY, matching what the public list has
        // always applied. A publication in a non-publishing category is internal
        // even when it is ACTIVE.
        if (audience == Audience.PUBLIC
                && (legacy.getCategory() == null || !legacy.getCategory().isPublish())) {
            return null;
        }
        return legacy.toVo(PublicationVo.class, DataFilter.get().lang(lang));
    }

    /**
     * A citation, in the system shape, for the editor.
     *
     * This is what makes the legacy citation machinery work unchanged against a
     * new-model issue: extract-message-publication and
     * update-message-publications read publicationId, messagePublication and the
     * per-language link and format, and an issue wearing this shape supplies all
     * four.
     *
     * No tier gate. Both endpoints that use it are EDITOR-only, and an editor
     * citing an issue that is still OPEN is the ordinary case -- the citation is
     * written while the issue is being prepared.
     */
    @Transactional
    public SystemPublicationVo systemVo(String publicationId, String lang) {
        if (publicationId == null || publicationId.isBlank()) {
            return null;
        }

        PublicationIssue issue = findIssue(publicationId);
        if (issue != null) {
            return IssuePublicationMapping.toSystemPublicationVo(issue, lang);
        }

        Publication legacy = findLegacy(publicationId);
        return legacy == null ? null : legacy.toVo(SystemPublicationVo.class, DataFilter.get());
    }

    /**
     * The domain to sort by: the FIRST named publication's, whatever it is.
     *
     * The first, and only the first -- including when the first has no domain, in
     * which case the answer is null and the result is not domain-sorted. Falling
     * through to the second publication would read as an improvement and would
     * change the order of an existing mixed request; 17 live publications have no
     * domain, so it would fire.
     *
     * "The first" is only meaningful because the id set preserves the caller's
     * order -- it is a LinkedHashSet for exactly this. Both halves of the
     * transition answer here, so a cut-over series sorts the way its legacy rows
     * did.
     */
    @Transactional
    public Domain sortDomain(Collection<String> publicationIds) {
        if (publicationIds == null || publicationIds.isEmpty()) {
            return null;
        }

        String first = publicationIds.iterator().next();

        PublicationIssue issue = findIssue(first);
        if (issue != null) {
            return issue.getSeries() == null ? null : issue.getSeries().getDomain();
        }

        Publication legacy = findLegacy(first);
        return legacy == null ? null : legacy.getDomain();
    }


    /** The frozen member uids of an issue, in their stored order. */
    private List<String> memberUids(String publicId) {
        return em.createQuery(
                        "SELECT m.messageUid FROM IssueMember m "
                                + "WHERE m.issue.publicId = :id ORDER BY m.sortIndex", String.class)
                .setParameter("id", publicId)
                .getResultList();
    }

    /**
     * Whether an id may designate this issue's member set.
     *
     * Everything but OPEN, for the public caller as well as the internal one. A
     * RETIRED issue is withdrawn from the public LIST, not unmade: its file link
     * and every citation written into it stay live, and the whole point of
     * retiring rather than deleting is that references do not dangle. Refusing it
     * here would break {@code ?publication=} for the entire imported archive on
     * day one, because the import maps every INACTIVE legacy row to RETIRED.
     *
     * OPEN is the one that is genuinely not there yet: no member set has been
     * frozen, so serving it publicly would publish an unfinished list.
     */
    private static boolean designatable(PublicationIssue issue, Audience audience) {
        return audience == Audience.INTERNAL || issue.getStatus() != IssueStatus.OPEN;
    }

    /**
     * Whether a public caller may be handed this issue as a citation row.
     *
     * PUBLISHED only, and the asymmetry with the rule above is deliberate. The
     * single read is what the public download page resolves a deep link through,
     * and a retired issue is exactly the thing that page must stop listing -- so
     * it answers as a missing id does, with no way to tell the two apart. The
     * membership question is a different question: "what was in this publication"
     * has a true answer for a withdrawn one.
     */
    private static boolean citable(PublicationIssue issue, Audience audience) {
        return audience == Audience.INTERNAL || issue.getStatus() == IssueStatus.PUBLISHED;
    }

    /** Whether the issue's series sits in a category the public page carries. */
    private static boolean inAPublishingCategory(PublicationIssue issue) {
        return issue.getSeries() != null
                && issue.getSeries().getCategory() != null
                && issue.getSeries().getCategory().isPublish();
    }

    /**
     * Whether a public caller may be served this legacy publication.
     *
     * ACTIVE only, and this filter is new. The legacy resolution had no status
     * filter at all, so a DRAFT or RECORDING publication's message tag resolved
     * anonymously -- an unfinished issue's contents, served to the public site by
     * anyone who knew the id.
     */
    private static boolean servable(Publication publication, Audience audience) {
        return audience == Audience.INTERNAL || publication.getStatus() == PublicationStatus.ACTIVE;
    }

    /**
     * The refusal text.
     *
     * One wording for every reason. "Does not exist", "not published yet" and
     * "not yours" must read identically, or the difference between them is an
     * oracle an anonymous caller can query.
     */
    private static String unresolvableMessage(List<String> ids) {
        return "Unresolvable publication id(s): " + String.join(", ", ids);
    }
}
