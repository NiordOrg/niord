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

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.user.User;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.criteria.MessageSeriesCriterionVo;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.model.message.Status;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The issue and member invariants, asserted against a real publish.
 *
 * These were prose. The specification states them as biconditionals -- X is
 * non-null exactly when Y holds -- and a biconditional is the shape that reads
 * as obviously true and is half-implemented in practice: the forward direction
 * gets written because somebody needed it, and the reverse never does.
 *
 * So each one is asserted in BOTH directions where both are reachable. The
 * fixtures go through the real publish transaction rather than being planted,
 * because an invariant that only holds for rows a test wrote by hand is not an
 * invariant about the system.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class IssueInvariantsTest {

    private static final Pattern LOWERCASE_UUID = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    @Inject
    IssuePublishService publishService;

    @Inject
    IssueLifecycleService lifecycle;

    @Inject
    IssueCurationService curation;

    @Inject
    EntityManager em;

    // ==================================================== the interval bounds

    /**
     * I-1, I-2, I-17. The interval exists exactly when the time relation has one.
     *
     * IN_FORCE_AT_CUTOFF is the LARGER shape in production -- 531 issues with no
     * interval and no lower bound, against 500 weekly ones. Requiring an interval
     * unconditionally would be an EfS-shaped assumption that empties the majority
     * of the corpus, so the biconditional matters in the reverse direction more
     * than the forward one.
     *
     * I-17 rides along because the source columns are what distinguish a stamped
     * bound from a recovered one, and a bound with no source cannot be audited.
     */
    @BindsRule({"I-1", "I-2", "I-17"})
    @Test
    @Transactional
    public void theIntervalExistsExactlyWhenTheTimeRelationHasOne() {
        PublicationSeries tiling = series(TimeRelation.PUBLISHED_IN_INTERVAL);
        PublicationIssue tiled = publishAt(tiling, new Date(1_700_000_000_000L));

        assertNotNull(tiled.getIntervalFrom(),
                "a PUBLISHED_IN_INTERVAL issue has no intervalFrom, so it cannot chain");
        assertNotNull(tiled.getIntervalFromSource(),
                "I-17: an interval bound with no source cannot be told from a recovered one");

        PublicationSeries inForce = series(TimeRelation.IN_FORCE_AT_CUTOFF);
        PublicationIssue atInstant = openIssue(inForce, null);
        assertNull(atInstant.getIntervalFrom(),
                "an IN_FORCE_AT_CUTOFF issue acquired an intervalFrom; that relation has no lower "
                        + "bound at all, and giving it one would drop everything published earlier");
        assertNull(atInstant.getIntervalFromSource(),
                "I-17 reverse: a source with no bound to describe");
    }

    // ==================================================== the stamp and the publish

    /**
     * I-3, I-4, I-5. The stamp, its source, and publishedAt appear together.
     *
     * They are three separate columns because they answer three questions, and
     * HANDOVER decision 5 lets an admin choose a stamp that is not the moment of
     * publishing -- so publishedAt cannot simply be read off cutoffStampedAt.
     */
    @BindsRule({"I-3", "I-4", "I-5"})
    @Test
    @Transactional
    public void theStampItsSourceAndPublishedAtAppearTogether() {
        PublicationSeries s = series(TimeRelation.PUBLISHED_IN_INTERVAL);

        PublicationIssue open = openIssue(s, new Date(1_699_000_000_000L));
        assertNull(open.getCutoffStampedAt(), "an OPEN issue is stamped");
        assertNull(open.getCutoffSource(), "I-4 reverse: a source with no stamp");
        assertNull(open.getPublishedAt(), "an OPEN issue has a publishedAt");

        PublicationIssue published = publishAt(s, new Date(1_700_000_000_000L));
        assertEquals(IssueStatus.PUBLISHED, published.getStatus());
        assertNotNull(published.getCutoffStampedAt(), "I-3: a PUBLISHED issue has no stamp");
        assertNotNull(published.getCutoffSource(), "I-4: a stamp with no source cannot be audited");
        assertNotNull(published.getPublishedAt(), "I-5: a PUBLISHED issue has no publishedAt");

        // And RETIRED keeps all three -- retiring withdraws an issue, it does not
        // un-publish it, and clearing them would uncap the issue before it.
        lifecycle.retire(published, null, "superseded by a corrected edition");
        assertNotNull(published.getCutoffStampedAt(), "I-3: retiring cleared the stamp");
        assertNotNull(published.getPublishedAt(), "I-5: retiring cleared publishedAt");
    }

    /**
     * I-6. The retirement columns appear together, and the reason is real text.
     *
     * A retirement with no reason is indistinguishable from a mistake a year
     * later, which is exactly when anybody looks.
     */
    @BindsRule({"I-6"})
    @Test
    @Transactional
    public void retirementCarriesItsWholeExplanationOrNoneOfIt() {
        PublicationSeries s = series(TimeRelation.PUBLISHED_IN_INTERVAL);
        PublicationIssue published = publishAt(s, new Date(1_700_000_000_000L));

        assertNull(published.getRetiredAt(), "a PUBLISHED issue carries a retiredAt");
        assertNull(published.getRetiredReason(), "a PUBLISHED issue carries a retirement reason");

        lifecycle.retire(published, null, "withdrawn after a chart error");

        assertEquals(IssueStatus.RETIRED, published.getStatus());
        assertNotNull(published.getRetiredAt(), "I-6: RETIRED with no retiredAt");
        assertNotNull(published.getRetiredReason(), "I-6: RETIRED with no reason");
        assertTrue(published.getRetiredReason().trim().length() >= 3,
                "I-6: the reason is shorter than three characters, which is not an explanation");
        assertTrue(published.getRetiredReason().length() <= 512,
                "I-6: the reason exceeds the column and would be truncated on write");
    }

    // ==================================================== identity

    /**
     * I-10. publicId is a lowercase UUID and immutable for life.
     *
     * The shape is a hard constraint rather than a convention: PublicationUtils
     * builds its selector by string concatenation --
     * doc.select("a[publication=" + id + "]") -- with no quoting, so any
     * CSS-special character matches nothing and update-message-publications then
     * APPENDS a duplicate citation instead of finding the first.
     */
    @BindsRule({"I-10"})
    @Test
    @Transactional
    public void thePublicIdIsALowercaseUuidAndNeverChanges() {
        PublicationSeries s = series(TimeRelation.PUBLISHED_IN_INTERVAL);
        PublicationIssue issue = lifecycle.create(s, new Date(1_699_000_000_000L),
                IntervalBoundSource.STAMPED, null);
        em.flush();

        String minted = issue.getPublicId();
        assertTrue(LOWERCASE_UUID.matcher(minted).matches(),
                "publicId " + minted + " is not a lowercase UUID; an unquoted CSS selector is built "
                        + "from it, so a special character silently appends a duplicate citation");

        previewFor(issue);
        publishService.publish(issue.getId(),
                new IssuePublishService.PublishRequest(false, IssuePublishService.PublishRequest.ALL_WARNINGS, null, new Date(1_700_000_000_000L)));
        em.flush();
        PublicationIssue after = em.find(PublicationIssue.class, issue.getId());
        assertEquals(minted, after.getPublicId(), "publish changed publicId");

        publishService.amend(after.getId(),
                new IssuePublishService.AmendRequest(false, IssuePublishService.PublishRequest.ALL_WARNINGS, null, "typo"));
        assertEquals(minted, after.getPublicId(), "amend changed publicId");
        lifecycle.retire(after, null, "withdrawn in error");
        assertEquals(minted, after.getPublicId(), "retire changed publicId");
        lifecycle.reactivate(after, null, "restored");
        assertEquals(minted, after.getPublicId(), "reactivate changed publicId");
    }

    // ==================================================== the frozen snapshot

    /**
     * I-11, I-12. The header agrees with the rows, and describes how they were made.
     *
     * memberCount is denormalised, so it can disagree -- and a count that
     * disagrees with the rows is worse than no count, because the UI shows the
     * count and the document shows the rows.
     *
     * I-12 is what makes the snapshot reproducible: without the criteria and the
     * sort recorded beside it, a frozen member list cannot be explained, only
     * trusted.
     */
    @BindsRule({"I-11", "I-12"})
    @Test
    @Transactional
    public void theHeaderAgreesWithTheRowsAndRecordsHowTheyWereMade() {
        PublicationSeries s = series(TimeRelation.PUBLISHED_IN_INTERVAL);
        PublicationIssue issue = publishAt(s, new Date(1_700_000_000_000L));
        em.flush();

        long rows = em.createQuery(
                        "SELECT COUNT(m) FROM IssueMember m WHERE m.issue.id = :id", Long.class)
                .setParameter("id", issue.getId())
                .getSingleResult();

        assertEquals(rows, issue.getMemberCount().longValue(),
                "I-11: memberCount says " + issue.getMemberCount() + " and there are " + rows
                        + " member rows; the count is what the UI shows");

        if (issue.getMemberCount() > 0) {
            assertNotNull(issue.getSnapshotFrozenAt(), "I-12: members with no freeze time");
            assertNotNull(issue.getMembershipProvenance(), "I-12: members with no provenance");

            // The criteria snapshot is required when a QUERY produced the members,
            // not merely when members exist. Two shapes have members and no
            // criteria, and both are legitimate: an annex issue curated by hand,
            // and an imported annex carrying its one recorded member (ruling
            // hand-named annexes). S-1 forbids criteria on a non-query series, so demanding it
            // whenever memberCount > 0 makes those shapes unrepresentable -- and
            // The exit from the import phase is gated on this harness, so the assertion
            // would go red for a row the importer is required to write.
            if (issue.getSeries().getContentMode() == ContentMode.GENERATED_FROM_QUERY) {
                assertNotNull(issue.getCriteriaSnapshot(),
                        "I-12: a query-derived member list with no criteria recorded beside it "
                                + "cannot be explained afterwards, only trusted");
            }
        }
    }

    /**
     * M-3, M-4. What is frozen on a member row, and what is deliberately nullable.
     *
     * M-4 is the load-bearing one. A NULL frozenPublishDateTo means "still open at
     * freeze", which is precisely the state the alive-at-cut-off clause has to
     * read -- and the NULL-unsafe form of that clause collapses P&T uge 28/2026
     * from 165 members to 47 and returns ZERO for the 2026 and 2027 firing-areas
     * issues.
     */
    @BindsRule({"M-3", "M-4"})
    @Test
    @Transactional
    public void theMemberRowsFreezeWhatWasTrueAndAllowAnOpenEnd() {
        PublicationSeries s = series(TimeRelation.PUBLISHED_IN_INTERVAL);
        PublicationIssue issue = publishAt(s, new Date(1_700_000_000_000L));
        em.flush();

        List<IssueMember> members = em.createQuery(
                        "SELECT m FROM IssueMember m WHERE m.issue.id = :id", IssueMember.class)
                .setParameter("id", issue.getId())
                .getResultList();

        assertFalse(members.isEmpty(),
                "the fixture froze no members, so this asserts nothing; seed the database first");

        Set<String> publicStatuses = new java.util.LinkedHashSet<>();
        for (Status st : Status.values()) {
            if (st.isPublic()) {
                publicStatuses.add(st.name());
            }
        }

        for (IssueMember m : members) {
            assertNotNull(m.getFrozenType(), "M-3: a member row with no frozen type");
            assertNotNull(m.getFrozenStatus(), "M-3: a member row with no frozen status");
            assertTrue(publicStatuses.contains(m.getFrozenStatus()),
                    "M-3: member " + m.getMessageUid() + " froze status " + m.getFrozenStatus()
                            + ", which is not public; the frozen set is what the document shows");
            assertNotNull(m.getMessageUid(), "a member row with no message");
        }

        // M-4: the column must ACCEPT null. Asserting that some row happens to be
        // null would be asserting the corpus, not the schema.
        IssueMember openEnded = members.get(0);
        openEnded.setFrozenPublishDateTo(null);
        em.merge(openEnded);
        em.flush();
        assertNull(em.find(IssueMember.class, openEnded.getId()).getFrozenPublishDateTo(),
                "M-4: frozenPublishDateTo cannot hold null, so \"still open at freeze\" is "
                        + "unrepresentable -- and that is the state the alive-at-cut-off clause reads");
    }

    /**
     * M-5. Member rows are written at freeze, and only at freeze.
     *
     * A live re-resolve silently rewrites published history: type, publishDateFrom
     * and publishDateTo on a message are mutable and unversioned, and NM-300-24
     * sits in ten different issues. Re-reading would move it.
     */
    @BindsRule({"M-5"})
    @Test
    @Transactional
    public void memberRowsAreWrittenAtFreezeAndNotAgain() {
        PublicationSeries s = series(TimeRelation.PUBLISHED_IN_INTERVAL);
        PublicationIssue issue = publishAt(s, new Date(1_700_000_000_000L));
        em.flush();

        List<String> atFreeze = memberUids(issue);
        Date frozenAt = issue.getSnapshotFrozenAt();

        // Reading the issue again -- through the same service the public adapter
        // uses -- must not re-resolve anything.
        em.clear();
        PublicationIssue reread = em.find(PublicationIssue.class, issue.getId());

        assertEquals(atFreeze, memberUids(reread),
                "M-5: the member set changed between reads, so it is being resolved live");
        assertEquals(frozenAt, reread.getSnapshotFrozenAt(),
                "M-5: the freeze timestamp moved on a read");
    }

    // ==================================================== overrides

    /**
     * M-6, M-7. An include is a row with a reason; an exclude is only an override.
     *
     * M-7 is why there is no second table: an exclusion that produced a member row
     * marked "excluded" would mean every reader of the member set has to remember
     * to filter it, and one of them eventually will not.
     */
    @BindsRule({"M-6", "M-7"})
    @Test
    @Transactional
    public void anIncludeIsAMemberRowAndAnExcludeIsNot() {
        PublicationSeries s = series(TimeRelation.PUBLISHED_IN_INTERVAL);
        PublicationIssue issue = publishAt(s, new Date(1_700_000_000_000L));
        em.flush();

        List<IssueMember> members = em.createQuery(
                        "SELECT m FROM IssueMember m WHERE m.issue.id = :id", IssueMember.class)
                .setParameter("id", issue.getId())
                .getResultList();

        for (IssueMember m : members) {
            if (m.getSource() == MemberSource.OVERRIDE_INCLUDE) {
                assertNotNull(m.getOverride(),
                        "M-6: an OVERRIDE_INCLUDE row with no override, so the admin who added it "
                                + "and their reason are unreachable");
            } else {
                assertNull(m.getOverride(),
                        "M-6 reverse: a non-override row pointing at an override");
            }
        }

        long excludeRows = em.createQuery(
                        "SELECT COUNT(m) FROM IssueMember m WHERE m.issue.id = :id "
                                + "AND m.override IS NOT NULL AND m.override.kind = :kind", Long.class)
                .setParameter("id", issue.getId())
                .setParameter("kind", OverrideKind.EXCLUDE)
                .getSingleResult();

        assertEquals(0L, excludeRows,
                "M-7: an EXCLUDE override produced a member row. Exclusions live only as override "
                        + "rows, or every reader of the member set has to remember to filter them");
    }

    // ==================================================== curated membership

    /**
     * A series with no query still publishes what a curator named.
     *
     * The NCAGS and Isbilag annexes hold two live messages a year and each issue
     * names one of them; no query of any shape can select one and not the other,
     * because the only discriminator is the message body. contentMode is not
     * GENERATED_FROM_QUERY for them.
     *
     * The overrides used to be passed only on the query branch, so curating such
     * an issue recorded an audited include -- IssueCurationService has no
     * contentMode guard -- and publish then froze ZERO members while the release
     * checklist reported that every override applied. The annex report takes its
     * heading from the first member, so the visible result is an untitled
     * document rather than an error.
     */
    @BindsRule({"X-7"})
    @Test
    @Transactional
    public void aCuratedIssueOnANonQuerySeriesPublishesWhatWasNamed() {
        String uid = someMessageUid();

        PublicationSeries annexes = series(TimeRelation.PUBLISHED_IN_INTERVAL);
        annexes.setContentMode(ContentMode.NONE);
        annexes.setCriteria(null);
        annexes.setTimeRelation(null);
        em.flush();

        PublicationIssue issue = lifecycle.create(annexes, new Date(1_699_000_000_000L),
                IntervalBoundSource.STAMPED, null);
        em.flush();
        curation.include(issue, uid, user(), "the ice service message for this year");
        em.flush();

        previewFor(issue);
        publishService.publish(issue.getId(),
                new IssuePublishService.PublishRequest(false, IssuePublishService.PublishRequest.ALL_WARNINGS, null, new Date(1_700_000_000_000L)));
        em.flush();
        em.clear();

        PublicationIssue published = em.find(PublicationIssue.class, issue.getId());
        assertEquals(1, published.getMemberCount().intValue(),
                "the curated include was discarded at publish, so the issue froze empty while the "
                        + "checklist reported that every override applied");

        List<String> uids = em.createQuery(
                        "SELECT m.messageUid FROM IssueMember m WHERE m.issue.id = :id", String.class)
                .setParameter("id", published.getId()).getResultList();
        assertEquals(List.of(uid), uids, "the frozen member is not the one that was named");
    }

    /** And an exclude still removes one, so the two overrides stay symmetric. */
    @Test
    @Transactional
    public void anExcludeOnANonQuerySeriesRemovesTheNamedMember() {
        List<String> uids = someMessageUids(2);

        PublicationSeries annexes = series(TimeRelation.PUBLISHED_IN_INTERVAL);
        annexes.setContentMode(ContentMode.NONE);
        annexes.setCriteria(null);
        annexes.setTimeRelation(null);
        em.flush();

        PublicationIssue issue = lifecycle.create(annexes, new Date(1_699_000_000_000L),
                IntervalBoundSource.STAMPED, null);
        em.flush();
        curation.include(issue, uids.get(0), user(), "the NCAGS message");
        curation.include(issue, uids.get(1), user(), "added in error");
        curation.exclude(issue, uids.get(1), user(), "removed again before release");
        em.flush();

        previewFor(issue);
        publishService.publish(issue.getId(),
                new IssuePublishService.PublishRequest(false, IssuePublishService.PublishRequest.ALL_WARNINGS, null, new Date(1_700_000_000_000L)));
        em.flush();
        em.clear();

        assertEquals(1, em.find(PublicationIssue.class, issue.getId()).getMemberCount().intValue(),
                "an exclude on a non-query series did not remove the member it named");
    }

    // ------------------------------------------------------------------ fixtures

    private PublicationSeries series(TimeRelation relation) {
        PublicationCategory c = new PublicationCategory();
        c.setCategoryId("cat-" + UUID.randomUUID().toString().substring(0, 8));
        c.setPriority(100);
        c.setPublish(true);
        em.persist(c);

        PublicationSeries s = new PublicationSeries();
        s.setSeriesId("s-" + UUID.randomUUID().toString().substring(0, 8));
        s.setStatus(SeriesStatus.ACTIVE);
        s.setContentMode(ContentMode.GENERATED_FROM_QUERY);
        s.setReportId("some-report");
        s.setCadence(SeriesCadence.WEEKLY);
        s.setTimeRelation(relation);
        s.setAliveAtCutoff(relation == TimeRelation.IN_FORCE_AT_CUTOFF);
        s.setReleaseMode(ReleaseMode.MANUAL_GATE);
        s.setNextIssueCreation(NextIssueCreation.MANUAL);
        s.setPublicAuthority(PublicAuthority.LEGACY);
        s.setMessagePublication(MessagePublication.NONE);
        s.setNumberingScheme(NumberingScheme.ISO_WEEK_YEAR);
        s.setCategory(c);
        s.getLanguages().add("da");

        IssueCriteriaVo doc = new IssueCriteriaVo();
        MessageSeriesCriterionVo node = new MessageSeriesCriterionVo();
        node.setValues(new ArrayList<>(List.of("dma-nm")));
        doc.getCriteria().add(node);
        s.setCriteria(doc);

        s.createDesc("da").setName("Test series");
        em.persist(s);
        return s;
    }

    /** Every override names its author -- IssueOverride.author is non-null, which is C1. */
    private User user() {
        User u = new User();
        u.setUsername("u-" + UUID.randomUUID().toString().substring(0, 8));
        em.persist(u);
        return u;
    }

    private String someMessageUid() {
        return someMessageUids(1).get(0);
    }

    private List<String> someMessageUids(int count) {
        List<String> uids = em.createQuery("SELECT m.uid FROM Message m ORDER BY m.id", String.class)
                .setMaxResults(count).getResultList();
        assertEquals(count, uids.size(),
                "the test database holds fewer than " + count + " messages; seed it first");
        return uids;
    }

    private PublicationIssue openIssue(PublicationSeries s, Date intervalFrom) {
        PublicationIssue i = lifecycle.create(s, intervalFrom,
                intervalFrom == null ? null : IntervalBoundSource.STAMPED, null);
        em.flush();
        return i;
    }

    private PublicationIssue publishAt(PublicationSeries s, Date stamp) {
        PublicationIssue i = lifecycle.create(s, new Date(stamp.getTime() - 7 * 24 * 3600_000L),
                IntervalBoundSource.STAMPED, null);
        em.flush();
        previewFor(i);
        publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(false, IssuePublishService.PublishRequest.ALL_WARNINGS, null, stamp));
        em.flush();
        return em.find(PublicationIssue.class, i.getId());
    }

    private List<String> memberUids(PublicationIssue issue) {
        return em.createQuery(
                        "SELECT m.messageUid FROM IssueMember m WHERE m.issue.id = :id "
                                + "ORDER BY m.sortIndex", String.class)
                .setParameter("id", issue.getId())
                .getResultList();
    }
    @jakarta.inject.Inject
    org.niord.core.publication.series.IssuePreviewService previewService;

    /**
     * Records a preview so the publish has bytes to promote.
     *
     * A query-backed series names a report and publish refuses to leave a
     * language without a document, so these fixtures release the way an admin
     * does after looking at the preview: regenerate = false, promoting exactly
     * the bytes that were reviewed. The bytes themselves are irrelevant here.
     */
    private void previewFor(org.niord.core.publication.series.PublicationIssue issue) {
        for (org.niord.core.publication.series.PublicationIssueDesc desc : issue.getDescs()) {
            previewService.record(issue, desc.getLang(), "preview.pdf",
                    "preview-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

}
