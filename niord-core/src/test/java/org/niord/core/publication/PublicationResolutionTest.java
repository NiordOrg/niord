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

import org.niord.core.domain.Domain;
import org.niord.core.publication.series.TestOwnerDomain;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.niord.core.publication.series.BindsRule;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.message.MemberSetDesignation;
import org.niord.core.message.MessageSearchParams;
import org.niord.core.message.MessageService;
import org.niord.core.message.PublicationMemberSetSource.Audience;
import org.niord.core.publication.series.ContentMode;
import org.niord.core.publication.series.IssueMember;
import org.niord.core.publication.series.IssuePublishService;
import org.niord.core.publication.series.IssueStatus;
import org.niord.core.publication.series.IssueLifecycleService.TransitionRefusedException;
import org.niord.core.publication.series.MemberSource;
import org.niord.core.publication.series.NextIssueCreation;
import org.niord.core.publication.series.NumberingScheme;
import org.niord.core.publication.series.PublicAuthority;
import org.niord.core.publication.series.PublicationIssue;
import org.niord.core.publication.series.PublicationSeries;
import org.niord.core.publication.series.ReleaseMode;
import org.niord.core.publication.series.SeriesCadence;
import org.niord.core.publication.series.SeriesStatus;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.core.publication.vo.PublicationStatus;
import org.niord.model.publication.PublicationType;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What {@code publication=} does now, and what it used to do.
 *
 * The behaviour being replaced: an id that resolved to nothing was silently
 * dropped, the tag filter therefore never appeared, and the widening branch
 * returned every published message. A typo returned 244 messages; so did each of
 * the 27 ACTIVE publications with no message tag. Nothing in the response said
 * the filter had been ignored.
 *
 * These tests are about the difference between "no publication was named" and
 * "a publication was named and it is empty". Every one of them fails if those
 * two collapse back together.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class PublicationResolutionTest {

    @Inject
    PublicationResolver resolver;

    @Inject
    MessageService messageService;

    @Inject
    org.niord.core.publication.series.IssueLifecycleService lifecycle;

    @Inject
    org.niord.core.publication.series.IssuePublishService publishService;

    @Inject
    org.niord.core.publication.series.PublicationSeriesService seriesService;

    @Inject
    EntityManager em;

    // ===================================================== the union, not the intersection

    /**
     * Two issues return the UNION of their member sets.
     *
     * The predicate has to sit inside the same disjunction as the tag predicate.
     * Give the member set its own conjunct and this silently becomes an
     * intersection: two weeks of EfS would return only what the two weeks have in
     * common, which is normally nothing -- an empty page rather than an error.
     */
    @Test
    @Transactional
    public void twoIssuesReturnTheUnionOfTheirMembers() {
        List<String> uids = someMessageUids(6);

        PublicationIssue a = publishedIssueWith(uids.subList(0, 3));
        PublicationIssue b = publishedIssueWith(uids.subList(3, 6));

        MemberSetDesignation both = resolver.designate(
                ordered(a.getPublicId(), b.getPublicId()), Audience.PUBLIC);

        assertEquals(new LinkedHashSet<>(uids), both.memberUids(),
                "two publication ids did not produce the union of their members; as an intersection "
                        + "this returns what the issues have in common, which is usually nothing");
    }

    /** An overlapping member appears once. The union is deduplicated by uid. */
    @Test
    @Transactional
    public void anOverlappingMemberAppearsOnce() {
        List<String> uids = someMessageUids(3);

        PublicationIssue a = publishedIssueWith(uids.subList(0, 2));
        PublicationIssue b = publishedIssueWith(uids.subList(1, 3));

        MemberSetDesignation both = resolver.designate(
                ordered(a.getPublicId(), b.getPublicId()), Audience.PUBLIC);

        assertEquals(3, both.memberUids().size(),
                "the shared message was counted twice; a uid set must dedupe");
    }

    // ============================================== zero members means zero messages

    /**
     * An issue that designates no members returns no messages.
     *
     * The whole point. Before the re-point this produced no filter at all, so the
     * caller received every published message -- and an empty official issue is a
     * perfectly ordinary thing for the model to contain.
     */
    @BindsRule({"RI-15"})
    @Test
    @Transactional
    public void anIssueWithNoMembersDesignatesAnEmptySet() {
        PublicationIssue empty = publishedIssueWith(List.of());

        MemberSetDesignation designation =
                resolver.designate(ordered(empty.getPublicId()), Audience.PUBLIC);

        assertTrue(designation.designatesMemberSet(),
                "an issue with no members must still count as designated; if it does not, the search "
                        + "falls through to the widening branch and returns everything");
        assertTrue(designation.isEmptyDesignation());
        assertTrue(designation.memberUids().isEmpty());

        MessageSearchParams params = new MessageSearchParams()
                .publications(ordered(empty.getPublicId()))
                .memberSetDesignation(designation);
        params.maxSize(1000);

        assertEquals(0, messageService.search(params).getData().size(),
                "a publication designating nothing returned messages. That is the live defect: empty "
                        + "tags plus empty domains plus empty series silently became 'everything "
                        + "published'.");
    }

    /**
     * A legacy publication with no message tag is the same case.
     *
     * 27 ACTIVE publications are in exactly this state today, and each of them
     * currently answers publication=<id> with every published message.
     */
    @Test
    @Transactional
    public void aLegacyPublicationWithNoTagDesignatesAnEmptySet() {
        Publication legacy = legacyPublication(PublicationStatus.ACTIVE);

        MemberSetDesignation designation =
                resolver.designate(ordered(legacy.getPublicationId()), Audience.PUBLIC);

        assertTrue(designation.designatesMemberSet());
        assertTrue(designation.isEmptyDesignation(),
                "a legacy publication with no message tag must designate an empty set rather than "
                        + "nothing at all");
    }

    // ==================================================== the loud failure

    /** An id that resolves to nothing is refused, not ignored. */
    @Test
    @Transactional
    public void anUnknownIdIsRefused() {
        TransitionRefusedException e = assertThrows(TransitionRefusedException.class,
                () -> resolver.designate(ordered("not-a-real-publication-id"), Audience.PUBLIC),
                "an unknown publication id was accepted; today that returns every published message");

        assertEquals(PublicationResolver.UNRESOLVABLE, e.code());
    }

    /** One bad id fails the whole request, rather than quietly answering for the rest. */
    @Test
    @Transactional
    public void oneBadIdFailsTheWholeRequest() {
        PublicationIssue good = publishedIssueWith(someMessageUids(2));

        assertThrows(TransitionRefusedException.class,
                () -> resolver.designate(ordered(good.getPublicId(), "not-a-real-publication-id"),
                        Audience.PUBLIC),
                "a partial answer was returned; a caller cannot tell it from a complete one");
    }

    /**
     * No existence oracle.
     *
     * An OPEN issue is refused to a public caller with byte-identical text to a
     * missing id. Different wording would let an anonymous caller enumerate
     * unpublished issues by comparing error messages.
     */
    @Test
    @Transactional
    public void anOpenIssueIsRefusedPubliclyWithTheSameTextAsAMissingId() {
        PublicationIssue open = openIssue();

        TransitionRefusedException refusedExisting = assertThrows(TransitionRefusedException.class,
                () -> resolver.designate(ordered(open.getPublicId()), Audience.PUBLIC));

        TransitionRefusedException refusedMissing = assertThrows(TransitionRefusedException.class,
                () -> resolver.designate(ordered(open.getPublicId() + "-nope"), Audience.PUBLIC));

        assertEquals(refusedExisting.getMessage().replace(open.getPublicId(), "ID"),
                refusedMissing.getMessage().replace(open.getPublicId() + "-nope", "ID"),
                "the refusal text differs between an issue that exists and one that does not, which "
                        + "is an existence oracle an anonymous caller can query");
    }

    /** And the same OPEN issue resolves for an internal caller. */
    @BindsRule({"RI-14"})
    @Test
    @Transactional
    public void anOpenIssueResolvesInternally() {
        PublicationIssue open = openIssue();

        MemberSetDesignation designation =
                resolver.designate(ordered(open.getPublicId()), Audience.INTERNAL);

        assertTrue(designation.designatesMemberSet(),
                "an OPEN issue must resolve for an authenticated caller; the editor previews it");
    }

    /** A DRAFT legacy publication is refused publicly -- the status gate that was missing. */
    @Test
    @Transactional
    public void aDraftLegacyPublicationIsRefusedPublicly() {
        Publication draft = legacyPublication(PublicationStatus.DRAFT);

        assertThrows(TransitionRefusedException.class,
                () -> resolver.designate(ordered(draft.getPublicationId()), Audience.PUBLIC),
                "a DRAFT publication resolved anonymously; the legacy resolution had no status "
                        + "filter at all, so an unfinished issue's contents were public to anyone "
                        + "who knew the id");

        assertTrue(resolver.designate(ordered(draft.getPublicationId()), Audience.INTERNAL)
                        .designatesMemberSet(),
                "and it must still resolve internally");
    }

    // ================================================== nothing named, nothing changed

    /**
     * With no publication= at all, nothing is designated.
     *
     * The union row: a plain tag= search must be untouched by the re-point. If
     * an empty id set designated something, every ordinary search would acquire
     * an always-false disjunct and return nothing.
     */
    @Test
    @Transactional
    public void noPublicationMeansNoDesignation() {
        assertFalse(resolver.designate(Set.of(), Audience.PUBLIC).designatesMemberSet());
        assertFalse(resolver.designate(null, Audience.PUBLIC).designatesMemberSet());
        assertEquals(MemberSetDesignation.NONE, resolver.designate(Set.of(), Audience.INTERNAL));
    }

    /**
     * A search reached directly resolves for itself.
     *
     * Mailing-list triggers call the search service rather than a REST layer, so
     * they parse publication= and then discard it today. Nothing sets the
     * designation for them, so the search has to resolve it.
     */
    @Test
    @Transactional
    public void aDirectSearchResolvesPublicationForItself() {
        List<String> uids = someMessageUids(2);
        PublicationIssue issue = publishedIssueWith(uids);

        // No designation set on the params: exactly the mailing-list path.
        MessageSearchParams params = new MessageSearchParams()
                .publications(ordered(issue.getPublicId()));
        params.maxSize(1000);
        assertNotNull(params.getPublications());

        List<String> found = messageService.search(params).getData().stream()
                .map(m -> m.getUid())
                .toList();

        assertEquals(new LinkedHashSet<>(uids), new LinkedHashSet<>(found),
                "a search reached directly ignored publication=; that is the mailing-list defect, "
                        + "where the parameter parses and is then silently discarded");
    }

    /** The caller's order survives, so "the first publication" means something. */
    @Test
    @Transactional
    public void theCallerOrderSurvivesIntoTheParams() {
        MessageSearchParams params = new MessageSearchParams()
                .publications(ordered("zzz", "aaa", "mmm"));

        assertEquals(List.of("zzz", "aaa", "mmm"), new ArrayList<>(params.getPublications()),
                "the publication ids were reordered. The first one supplies the sort domain, so in a "
                        + "HashSet two identical requests can sort differently.");
    }

    // ================================================== the citation survives

    /**
     * publicId is byte-identical through amend, retire and reactivate.
     *
     * The message-to-publication relation exists ONLY as publication="<id>" inside
     * stored message HTML. There is no join table and no endpoint that removes a
     * citation, so the archive is effectively immutable: change an id and every
     * citation naming it is dead, permanently, with nothing to migrate.
     */
    @Test
    @Transactional
    public void theIdSurvivesEveryLifecycleTransition() {
        PublicationIssue issue = publishedIssueWith(someMessageUids(1));
        String citedId = issue.getPublicId();

        publishService.amend(issue.getId(),
                new org.niord.core.publication.series.IssuePublishService.AmendRequest(false, org.niord.core.publication.series.IssuePublishService.PublishRequest.ALL_WARNINGS, null, "typo in the heading"));
        assertEquals(citedId, issue.getPublicId(), "amend changed the id");
        assertNotNull(resolver.findIssue(citedId), "the citation stopped resolving after amend");

        lifecycle.retire(issue, null, "superseded");
        assertEquals(citedId, issue.getPublicId(), "retire changed the id");
        assertNotNull(resolver.findIssue(citedId),
                "a retired issue must still RESOLVE -- it leaves the listing, but a citation to it "
                        + "must not become a dead link");

        lifecycle.reactivate(issue, null, "withdrawn in error");
        assertEquals(citedId, issue.getPublicId(), "reactivate changed the id");
        assertNotNull(resolver.findIssue(citedId), "the citation stopped resolving after reactivate");
    }

    /**
     * A retired issue still answers publication= for a public caller.
     *
     * Different from the listing rule on purpose: retiring an issue removes it
     * from what the site advertises, not from what a citation can reach. The
     * member set was frozen at publish and retiring did not unfreeze it, so
     * "which messages were in this publication" has the same true answer it had
     * the day before. Refusing it would take the entire imported archive dark for
     * anonymous callers the moment the import runs, because every INACTIVE legacy
     * row arrives as RETIRED.
     */
    @Test
    @Transactional
    public void aRetiredIssueStillResolvesForACitation() {
        PublicationIssue issue = publishedIssueWith(someMessageUids(1));
        lifecycle.retire(issue, null, "superseded");
        em.flush();

        MemberSetDesignation designation =
                resolver.designate(ordered(issue.getPublicId()), Audience.PUBLIC);
        assertTrue(designation.designated(),
                "a RETIRED issue must still designate its frozen member set on the public search");
        assertEquals(1, designation.memberUids().size(),
                "the frozen members are the answer; retiring froze nothing further");

        assertNotNull(resolver.findIssue(issue.getPublicId()),
                "and the citation resolver must still find it, or every stored link goes dead");
    }

    /**
     * The single public read still refuses it, and that asymmetry is the contract.
     *
     * The download page resolves a deep link through this call, and a retired
     * issue is precisely what that page must stop offering -- so it answers as a
     * missing id does, with nothing to tell the two apart.
     */
    @Test
    @Transactional
    public void aRetiredIssueIsStillAbsentFromTheSinglePublicRead() {
        PublicationIssue issue = publishedIssueWith(someMessageUids(1));
        lifecycle.retire(issue, null, "superseded");
        em.flush();

        assertNull(resolver.publicVo(issue.getPublicId(), "en", Audience.PUBLIC),
                "a RETIRED issue must read as missing on the public single read");
        assertNotNull(resolver.publicVo(issue.getPublicId(), "en", Audience.INTERNAL),
                "but an internal caller still reads it");
    }

    /**
     * An OPEN issue designates nothing publicly -- that is the one status that is
     * genuinely not there yet.
     */
    @Test
    @Transactional
    public void anOpenIssueDoesNotDesignateForAPublicCaller() {
        PublicationIssue issue = publishedIssueWith(someMessageUids(1));
        issue.setStatus(org.niord.core.publication.series.IssueStatus.OPEN);
        em.flush();

        assertThrows(TransitionRefusedException.class,
                () -> resolver.designate(ordered(issue.getPublicId()), Audience.PUBLIC),
                "an OPEN issue has no frozen member set and must not answer publication= publicly");
    }

    /**
     * A published issue in a non-publishing category reads as missing.
     *
     * The public LIST has always applied the category's publish flag; the single
     * read did not, so the same issue was absent from the list and readable by id.
     * The estate carries four such rows on day one.
     */
    @Test
    @Transactional
    public void aPublishedIssueInANonPublishingCategoryIsNotPubliclyReadable() {
        PublicationIssue issue = publishedIssueWith(someMessageUids(1));
        issue.getSeries().getCategory().setPublish(false);
        em.flush();

        assertNull(resolver.publicVo(issue.getPublicId(), "en", Audience.PUBLIC),
                "a non-publishing category must hide the issue from the public single read, "
                        + "exactly as it hides it from the public list");
        assertNotNull(resolver.publicVo(issue.getPublicId(), "en", Audience.INTERNAL),
                "an internal caller still reads it -- the gate is about what the public page carries");
    }

    /**
     * messagePublication cannot flip once an issue has been released.
     *
     * It decides which field a citation is written into -- "publication" or
     * "internalPublication". Flip it after citations exist and every one of them
     * becomes unfindable, because it is sitting in the other field; re-applying
     * the citation appends a second copy rather than finding the first. There is
     * no endpoint that removes a citation, so nothing can undo it.
     */
    @Test
    @Transactional
    public void messagePublicationCannotFlipOnceAnIssueIsReleased() {
        PublicationIssue issue = publishedIssueWith(someMessageUids(1));
        PublicationSeries s = issue.getSeries();
        em.flush();

        s.setMessagePublication(MessagePublication.EXTERNAL);

        TransitionRefusedException e = assertThrows(TransitionRefusedException.class,
                () -> seriesService.update(s),
                "messagePublication was allowed to change on a series with a released issue");
        assertEquals("MESSAGE_PUBLICATION_IMMUTABLE", e.code());
    }

    /** And it is free to change until then -- the guard is on release, not on existence. */
    @Test
    @Transactional
    public void messagePublicationIsFreeUntilAnIssueIsReleased() {
        PublicationIssue open = openIssue();
        PublicationSeries s = open.getSeries();
        em.flush();

        s.setMessagePublication(MessagePublication.EXTERNAL);
        seriesService.update(s);

        assertEquals(MessagePublication.EXTERNAL, s.getMessagePublication(),
                "a series with only OPEN issues must still be editable; nothing can have cited it");
    }

    /**
     * A tag id nobody can see still means zero messages.
     *
     * The tag predicates and the member-set predicate now share one disjunction.
     * Before, an unresolvable tag produced builder.or() of an empty array, which
     * is FALSE -- collect the predicates into a shared list instead and that
     * falls out, turning an invisible tag into no filter at all.
     */
    @Test
    @Transactional
    public void anUnresolvableTagStillMeansZeroMessages() {
        MessageSearchParams params = new MessageSearchParams()
                .tags(Set.of("a-tag-that-does-not-exist"));
        params.maxSize(1000);

        assertEquals(0, messageService.search(params).getData().size(),
                "a tag id that resolves to nothing returned messages; the empty tag set has to stay "
                        + "an always-false predicate, not disappear");
    }

    // ================================================== the sort domain

    /**
     * The sort domain comes from the FIRST named publication, and no other.
     *
     * Falling through to a later one would look like an improvement and would
     * change the order of an existing mixed request.
     *
     * IT NOW FOLLOWS THE OWNER, and that is an accepted change. Every publication
     * names one, so the answer is a real domain where it used to be null for the
     * seventeen that carried none -- and a sort order taken from the desk that
     * produces a publication is a better answer than no order at all. What must
     * not change is WHICH publication supplies it.
     */
    @Test
    @Transactional
    public void theSortDomainComesFromTheFirstIdAndNoOther() {
        // TWO DIFFERENT DESKS, and that is what makes this an assertion. Both
        // publications used to share one owner, so "reads the first" and "reads
        // whichever it finds" gave the identical answer and a fall-through would
        // have passed. Now the second names a domain of its own, and reading it
        // is a visible failure.
        PublicationIssue first = publishedIssueWith(List.of());
        PublicationIssue second = publishedIssueWith(List.of());
        second.getSeries().setDomain(TestOwnerDomain.of(em, "test-owner-domain-2"));
        em.flush();

        String firstOwner = first.getSeries().getDomain().getDomainId();
        String secondOwner = second.getSeries().getDomain().getDomainId();
        assertNotEquals(firstOwner, secondOwner,
                "the fixture gave both publications the same owner, so a fall-through to the "
                        + "second would be indistinguishable from reading the first");

        Domain resolved = resolver.sortDomain(ordered(first.getPublicId(), second.getPublicId()));
        assertNotNull(resolved, "an owned publication must yield a sort domain");
        assertEquals(firstOwner, resolved.getDomainId(),
                "the sort domain fell through to a later publication, which silently reorders an "
                        + "existing mixed request");

        assertNull(resolver.sortDomain(Set.of()));
        assertNull(resolver.sortDomain(null));
    }

    // ============================================ the system tier

    /**
     * A search reached directly resolves at the PUBLIC tier, not the internal one.
     *
     * The caller that reaches here carrying publications is a mailing-list
     * trigger, whose stored query is parsed rather than built, and whose
     * recipients this system does not control. At the internal tier a trigger
     * naming an issue that is still OPEN would mail an unpublished issue's
     * contents outward -- unrecoverable, because the mail is sent, and invisible,
     * because it looks like an ordinary send.
     *
     * Refusing is the failure to prefer: it is logged against the trigger id and
     * the rest of the run is unaffected.
     */
    @Test
    @Transactional
    public void aDirectSearchResolvesAtThePublicTier() {
        PublicationIssue open = openIssue();

        MessageSearchParams params = new MessageSearchParams()
                .publications(ordered(open.getPublicId()));
        params.maxSize(10);

        assertThrows(TransitionRefusedException.class,
                () -> messageService.search(params),
                "a search with no declared tier resolved an OPEN issue; a mailing-list trigger "
                        + "naming an unpublished issue would mail its contents to external recipients");
    }

    /** And a caller that needs the internal tier says so by setting the designation. */
    @Test
    @Transactional
    public void acallerThatNeedsTheInternalTierSetsTheDesignationItself() {
        PublicationIssue open = openIssue();
        em.flush();

        MessageSearchParams params = new MessageSearchParams()
                .publications(ordered(open.getPublicId()))
                .memberSetDesignation(
                        resolver.designate(ordered(open.getPublicId()), Audience.INTERNAL));
        params.maxSize(10);

        assertEquals(0, messageService.search(params).getData().size(),
                "the pre-set designation was ignored and re-resolved");
    }

    // ============================================ the blank parameter

    /**
     * "?publication=" with no value names no publication.
     *
     * It parses to a set holding one empty string. Skipping the blank inside the
     * loop still returned a designation -- an always-false one -- so a UI clearing
     * its filter blanked the result list AND suppressed the default published
     * domains, with a 200 and no indication anything had been filtered.
     */
    @Test
    @Transactional
    public void anEmptyPublicationParameterDesignatesNothing() {
        assertFalse(resolver.designate(ordered(""), Audience.PUBLIC).designatesMemberSet(),
                "an empty publication= value produced a designation, which is an always-false "
                        + "filter rather than no filter");
        assertFalse(resolver.designate(ordered("", "   "), Audience.PUBLIC).designatesMemberSet());
        assertEquals(MemberSetDesignation.NONE, resolver.designate(ordered(""), Audience.INTERNAL));

        MessageSearchParams params = new MessageSearchParams().publications(ordered(""));
        params.maxSize(10);
        assertFalse(messageService.search(params).getData().isEmpty(),
                "a cleared publication filter blanked the result list");
    }

    /** A blank alongside a real id is ignored, not treated as a failed resolution. */
    @Test
    @Transactional
    public void aBlankAlongsideARealIdIsIgnored() {
        PublicationIssue issue = publishedIssueWith(someMessageUids(2));

        MemberSetDesignation d = resolver.designate(
                ordered("", issue.getPublicId()), Audience.PUBLIC);

        assertTrue(d.designatesMemberSet());
        assertEquals(2, d.memberUids().size(),
                "the blank was counted as an unresolvable id and refused the whole request");
    }

    // ============================================ the cutover window

    /**
     * An imported issue that is not yet published falls back to its legacy row.
     *
     * During cutover an issue adopts the legacy publicationId as its own publicId
     * and sits at OPEN until it is first published. Resolving new-model-first and
     * refusing there -- rather than falling through -- takes that id dark for
     * every anonymous caller for the whole of that window, while the legacy row is
     * still ACTIVE and still the right answer. Every stored citation into it
     * breaks, and comes back on its own later, which is the worst way to find out.
     */
    @BindsRule({"X-3"})
    @Test
    @Transactional
    public void anImportedIssueNotYetPublishedFallsBackToTheLegacyRow() {
        Publication legacy = legacyPublication(PublicationStatus.ACTIVE);
        String sharedId = legacy.getPublicationId();

        PublicationIssue imported = issue(IssueStatus.OPEN);
        imported.setLegacyPublicationId(sharedId);
        imported.setPublicId(sharedId);
        em.flush();

        MemberSetDesignation d = resolver.designate(ordered(sharedId), Audience.PUBLIC);
        assertTrue(d.designatesMemberSet(),
                "the id was refused for a public caller even though the legacy row is ACTIVE; "
                        + "every citation into it goes dark until the issue is first published");

        assertNotNull(resolver.publicVo(sharedId, "da", Audience.PUBLIC),
                "the public detail endpoint 404s for an id whose legacy row is still served");
    }

    /** Once the issue IS published, the new model wins and the legacy row is not consulted. */
    @Test
    @Transactional
    public void oncePublishedTheIssueTakesOverTheId() {
        Publication legacy = legacyPublication(PublicationStatus.ACTIVE);
        String sharedId = legacy.getPublicationId();

        PublicationIssue imported = publishedIssueWith(someMessageUids(2));
        imported.setLegacyPublicationId(sharedId);
        imported.setPublicId(sharedId);
        em.flush();

        MemberSetDesignation d = resolver.designate(ordered(sharedId), Audience.PUBLIC);
        assertEquals(2, d.memberUids().size(),
                "after publish the id must resolve to the ISSUE, not to the legacy row it replaced");
        assertTrue(d.tagIds().isEmpty());
    }

    // ------------------------------------------------------------------ fixtures

    private static Set<String> ordered(String... ids) {
        return new LinkedHashSet<>(List.of(ids));
    }

    /** Real message uids from the corpus. */
    private List<String> someMessageUids(int count) {
        List<String> uids = em.createQuery("SELECT m.uid FROM Message m ORDER BY m.id", String.class)
                .setMaxResults(count)
                .getResultList();
        assertEquals(count, uids.size(),
                "the test database holds fewer than " + count + " messages. Seed it first: "
                        + "node scripts/seed-dev-database.mjs");
        return uids;
    }

    private PublicationSeries series() {
        PublicationCategory c = new PublicationCategory();
        c.setCategoryId(TestIds.category());
        // Explicit, because the single public read gates on it: an issue in a
        // non-publishing category reads as missing, so a fixture that left the
        // column at its false default would be testing the gate rather than the
        // resolution.
        c.setPublish(true);
        em.persist(c);

        PublicationSeries s = new PublicationSeries();
        s.setSeriesId(TestIds.series());
        s.setStatus(SeriesStatus.ACTIVE);
        s.setContentMode(ContentMode.GENERATED_FROM_QUERY);
        s.setReportId("some-report");
        s.setCadence(SeriesCadence.WEEKLY);
        s.setTimeRelation(TimeRelation.PUBLISHED_IN_INTERVAL);
        s.setAliveAtCutoff(false);
        s.setReleaseMode(ReleaseMode.MANUAL_GATE);
        s.setNextIssueCreation(NextIssueCreation.MANUAL);
        s.setPublicAuthority(PublicAuthority.NEW);
        s.setMessagePublication(MessagePublication.NONE);
        s.setNumberingScheme(NumberingScheme.ISO_WEEK_YEAR);
        s.setCategory(c);
        // Every publication names the desk that owns it: the column is NOT NULL and
        // S-20a refuses a save without one, so a fixture that left it out no longer
        // describes a state the system can be in.
        s.setDomain(TestOwnerDomain.of(em));
        s.getLanguages().add("da");
        s.createDesc("da").setName("Test series");
        em.persist(s);
        return s;
    }

    /**
     * A PUBLISHED issue whose members are exactly the given uids.
     *
     * Built directly rather than through the publish path: what is under test is
     * the RESOLUTION of a member set, and going through the criteria engine would
     * make the member list depend on whatever the corpus happens to contain.
     */
    private PublicationIssue publishedIssueWith(List<String> uids) {
        PublicationIssue issue = issue(IssueStatus.PUBLISHED);
        int index = 0;
        for (String uid : uids) {
            IssueMember member = new IssueMember();
            member.setIssue(issue);
            member.setMessageUid(uid);
            member.setSortIndex(index++);
            member.setFrozenMainType("NM");
            member.setFrozenType("MISCELLANEOUS");
            member.setFrozenStatus("PUBLISHED");
            member.setSource(MemberSource.CRITERIA);
            em.persist(member);
        }
        em.flush();
        return issue;
    }

    private PublicationIssue openIssue() {
        PublicationIssue issue = issue(IssueStatus.OPEN);
        em.flush();
        return issue;
    }

    private PublicationIssue issue(IssueStatus status) {
        PublicationIssue issue = new PublicationIssue();
        issue.setSeries(series());
        // A bare lowercase UUID: publicId is 36 characters and the shape is a
        // one-way decision, so the fixture has to be the real thing.
        issue.setPublicId(UUID.randomUUID().toString());
        issue.setRepoPath(TestIds.id("publications/test/"));
        issue.setStatus(status);
        issue.setIntervalFrom(new Date(1_700_000_000_000L));
        issue.setIntervalTo(new Date(1_700_600_000_000L));
        if (status == IssueStatus.PUBLISHED) {
            issue.setCutoffStampedAt(new Date(1_700_600_000_000L));
            issue.setPublicFrom(new Date(1_700_600_000_000L));
            issue.setPublishedAt(new Date(1_700_600_000_000L));
        }
        em.persist(issue);
        return issue;
    }

    private Publication legacyPublication(PublicationStatus status) {
        PublicationCategory c = new PublicationCategory();
        c.setCategoryId(TestIds.id("legacy-cat-"));
        c.setPublish(true);
        em.persist(c);

        Publication p = new Publication();
        p.setPublicationId(UUID.randomUUID().toString());
        p.setStatus(status);
        p.setType(PublicationType.LINK);
        p.setCategory(c);
        p.setMessagePublication(MessagePublication.NONE);
        em.persist(p);
        em.flush();
        return p;
    }
}
