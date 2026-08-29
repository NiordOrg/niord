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

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.message.Message;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.criteria.MessageSeriesCriterionVo;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.series.vo.IssueMemberVo;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.core.user.User;
import org.niord.model.message.MainType;
import org.niord.model.message.Status;
import org.niord.model.message.Type;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What has moved under a published issue since it was published.
 *
 * The member snapshot is frozen and stays frozen -- it is the record of what was
 * printed and the archived PDF is the proof. But the messages go on living, and
 * from the snapshot alone nobody can tell whether a three-week-old issue still
 * describes the world. So the divergence is computed and SURFACED, and these
 * assertions are about surfacing it without ever writing it back: a row healed
 * to agree with today would disagree with the document that went out, and
 * nothing would record that they had ever differed.
 *
 * The three compared fields are the mutable ones, which is why they are frozen
 * in the first place -- type is editor-writable and unversioned, status changes
 * on every withdrawal, and publishDateTo is null while a notice is open and gets
 * a value the moment it closes.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class IssueMemberDriftTest {

    @Inject
    IssueMemberListService memberList;

    @Inject
    EntityManager em;

    // ------------------------------------------------------------------ fixtures

    private User user(String username) {
        User u = new User();
        u.setUsername(username);
        em.persist(u);
        return u;
    }

    private PublicationSeries series() {
        PublicationCategory c = new PublicationCategory();
        c.setCategoryId("cat-" + UUID.randomUUID().toString().substring(0, 8));
        c.setPriority(100);
        em.persist(c);

        PublicationSeries s = new PublicationSeries();
        s.setSeriesId("s-" + UUID.randomUUID().toString().substring(0, 8));
        s.setStatus(SeriesStatus.ACTIVE);
        s.setContentMode(ContentMode.GENERATED_FROM_QUERY);
        s.setCadence(SeriesCadence.WEEKLY);
        s.setTimeRelation(TimeRelation.PUBLISHED_IN_INTERVAL);
        s.setAliveAtCutoff(false);
        s.setReleaseMode(ReleaseMode.MANUAL_GATE);
        s.setNextIssueCreation(NextIssueCreation.MANUAL);
        s.setPublicAuthority(PublicAuthority.LEGACY);
        s.setMessagePublication(MessagePublication.NONE);
        s.setNumberingScheme(NumberingScheme.ISO_WEEK_YEAR);
        s.setCategory(c);
        // Every publication names the desk that owns it: the column is NOT NULL and
        // S-20a refuses a save without one, so a fixture that left it out no longer
        // describes a state the system can be in.
        s.setDomain(TestOwnerDomain.of(em));
        s.getLanguages().add("da");

        IssueCriteriaVo doc = new IssueCriteriaVo();
        MessageSeriesCriterionVo node = new MessageSeriesCriterionVo();
        node.setValues(new ArrayList<>(List.of("dma-nm")));
        doc.getCriteria().add(node);
        s.setCriteria(doc);

        s.createDesc("da").setName("Drift fixture");
        em.persist(s);
        return s;
    }

    private PublicationIssue issue(PublicationSeries s, IssueStatus status) {
        PublicationIssue i = new PublicationIssue();
        i.setSeries(s);
        i.setPublicId(UUID.randomUUID().toString());
        i.setRepoPath("publications/" + i.getPublicId());
        i.setStatus(status);
        i.setSnapshotTimeRelation(TimeRelation.PUBLISHED_IN_INTERVAL);
        i.setIntervalFrom(new Date(1_700_000_000_000L));
        i.setIntervalTo(new Date(1_700_600_000_000L));
        if (status != IssueStatus.OPEN) {
            i.setCutoffStampedAt(new Date(1_700_600_000_000L));
        }
        i.createDesc("da").setName("Drift issue");
        em.persist(i);
        return i;
    }

    private Message message(Type type, Status status, Date publishDateTo) {
        Message m = new Message();
        m.setUid(UUID.randomUUID().toString());
        m.setMainType(MainType.NM);
        m.setType(type);
        m.setStatus(status);
        m.setPublishDateTo(publishDateTo);
        em.persist(m);
        return m;
    }

    /** A frozen row recording the message exactly as it stands right now. */
    private IssueMember member(PublicationIssue issue, Message message, int sortIndex) {
        IssueMember m = new IssueMember();
        m.setIssue(issue);
        m.setMessage(message);
        m.setMessageUid(message.getUid());
        m.setSortIndex(sortIndex);
        m.setFrozenShortId("NM-" + sortIndex);
        m.setFrozenMainType(message.getMainType().name());
        m.setFrozenType(message.getType().name());
        m.setFrozenStatus(message.getStatus().name());
        m.setFrozenPublishDateTo(message.getPublishDateTo());
        m.setSource(MemberSource.CRITERIA);
        em.persist(m);
        return m;
    }

    private IssueMemberVo rowFor(List<IssueMemberVo> rows, String uid) {
        return rows.stream().filter(r -> uid.equals(r.getMessageUid())).findFirst()
                .orElseThrow(() -> new AssertionError("no member row for " + uid));
    }

    // ------------------------------------------------------------------ drift

    /**
     * A member whose message has not moved carries NO drift and NO current.
     *
     * The absence is the signal. If every row carried a `current` block the
     * reader would be back to comparing it against the frozen half themselves,
     * which is the comparison the server is here to make once.
     */
    @Test
    @Transactional
    public void anUnchangedMemberReportsNothing() {
        PublicationSeries s = series();
        PublicationIssue i = issue(s, IssueStatus.PUBLISHED);
        Message m = message(Type.TEMPORARY_NOTICE, Status.PUBLISHED, null);
        member(i, m, 0);
        em.flush();

        IssueMemberVo row = rowFor(memberList.members(i), m.getUid());
        assertNull(row.getDrift(), "an unchanged member reported drift");
        assertNull(row.getCurrent(), "an unchanged member carried a current block");
    }

    /**
     * The three mutable fields each drift under their own name, and the frozen
     * row is untouched.
     */
    @Test
    @Transactional
    public void typeStatusAndPublishDateToEachDriftUnderTheirOwnName() {
        PublicationSeries s = series();
        PublicationIssue i = issue(s, IssueStatus.PUBLISHED);
        Message m = message(Type.TEMPORARY_NOTICE, Status.PUBLISHED, null);
        member(i, m, 0);
        em.flush();

        // The world moves on: the notice is re-typed, withdrawn, and closed.
        m.setType(Type.PRELIMINARY_NOTICE);
        m.setStatus(Status.CANCELLED);
        m.setPublishDateTo(new Date(1_701_000_000_000L));
        em.merge(m);
        em.flush();

        IssueMemberVo row = rowFor(memberList.members(i), m.getUid());
        assertNotNull(row.getDrift());
        assertTrue(row.getDrift().containsAll(List.of("type", "status", "publishDateTo")),
                "expected type, status and publishDateTo to drift; got " + row.getDrift());

        assertNotNull(row.getCurrent());
        assertTrue(row.getCurrent().isExists());
        assertEquals("PRELIMINARY_NOTICE", row.getCurrent().getType());
        assertEquals("CANCELLED", row.getCurrent().getStatus());

        // SURFACED, NEVER HEALED. The row still says what was printed.
        assertEquals("TEMPORARY_NOTICE", row.getFrozenType());
        assertEquals("PUBLISHED", row.getFrozenStatus());
        assertNull(row.getFrozenPublishDateTo());
    }

    /**
     * An open-ended validity that has since been closed is drift.
     *
     * This is the most common one on the estate and the one a null-unsafe
     * comparison misses entirely: null means "still open", which is exactly the
     * value an equality check written for two dates skips over.
     */
    @Test
    @Transactional
    public void anOpenEndedValidityThatWasClosedIsDrift() {
        PublicationSeries s = series();
        PublicationIssue i = issue(s, IssueStatus.PUBLISHED);
        Message m = message(Type.TEMPORARY_NOTICE, Status.PUBLISHED, null);
        member(i, m, 0);
        em.flush();

        m.setPublishDateTo(new Date(1_701_000_000_000L));
        em.merge(m);
        em.flush();

        IssueMemberVo row = rowFor(memberList.members(i), m.getUid());
        assertEquals(List.of("publishDateTo"), row.getDrift());
    }

    /**
     * A member whose message is no longer public says so, without that being a
     * drift of its own invention.
     *
     * "Still in the database" and "still readable by the public" are different
     * questions and a reader needs both: a cancelled member is present, findable
     * and no longer something the public may be pointed at.
     */
    @Test
    @Transactional
    public void aWithdrawnMemberReportsThatItIsNoLongerPublic() {
        PublicationSeries s = series();
        PublicationIssue i = issue(s, IssueStatus.PUBLISHED);
        Message m = message(Type.TEMPORARY_NOTICE, Status.PUBLISHED, null);
        member(i, m, 0);
        em.flush();

        m.setStatus(Status.DELETED);
        em.merge(m);
        em.flush();

        IssueMemberVo row = rowFor(memberList.members(i), m.getUid());
        assertTrue(row.getCurrent().isExists());
        assertFalse(row.getCurrent().isPubliclyVisible(),
                "a DELETED member still reported itself as publicly readable");
        assertTrue(row.getDrift().contains("status"));
    }

    /**
     * A member whose message is GONE drifts under its own name.
     *
     * Reporting three nulls for type, status and publishDateTo would be
     * indistinguishable from a message whose fields happen to be empty.
     */
    @Test
    @Transactional
    public void aDeletedMessageDriftsAsAnAbsence() {
        PublicationSeries s = series();
        PublicationIssue i = issue(s, IssueStatus.PUBLISHED);
        Message m = message(Type.TEMPORARY_NOTICE, Status.PUBLISHED, null);
        IssueMember member = member(i, m, 0);
        em.flush();

        // The member row keeps the uid; the message itself is removed.
        member.setMessage(null);
        em.merge(member);
        em.flush();
        em.remove(em.find(Message.class, m.getId()));
        em.flush();

        IssueMemberVo row = rowFor(memberList.members(i), m.getUid());
        assertEquals(List.of("exists"), row.getDrift());
        assertFalse(row.getCurrent().isExists());
        assertFalse(row.getCurrent().isPubliclyVisible());
        assertNull(row.getCurrent().getType());
    }

    /**
     * An OPEN issue's list carries no drift at all, and is not read from rows.
     *
     * There is nothing frozen for the live message to disagree with: an open issue
     * is RESOLVED, every time it is asked, so every value on it is the current one
     * and a drift marker would claim something had changed when nothing had.
     *
     * The row planted below is exactly what an open issue must not be answered
     * from. Member rows are written by the freeze, so a row on an OPEN issue is a
     * leftover -- an issue that was amended, or a fixture like this one -- and
     * serving it would report a membership decided at some earlier instant as
     * though it were what the issue contains now.
     */
    @Test
    @Transactional
    public void anOpenIssueIsResolvedLiveAndCarriesNoDrift() {
        PublicationSeries s = series();
        PublicationIssue i = issue(s, IssueStatus.OPEN);
        Message m = message(Type.TEMPORARY_NOTICE, Status.PUBLISHED, null);
        member(i, m, 0);
        em.flush();

        m.setStatus(Status.CANCELLED);
        em.merge(m);
        em.flush();

        List<IssueMemberVo> rows = memberList.members(i);
        assertTrue(rows.stream().noneMatch(r -> m.getUid().equals(r.getMessageUid())),
                "the frozen row was served for an OPEN issue; its list is the live resolution, and "
                        + "this message is not one the criteria select");
        for (IssueMemberVo row : rows) {
            assertNull(row.getDrift(), "a LIVE member list carried drift");
            assertNull(row.getCurrent());
        }
    }

    /** A RETIRED issue is frozen too, so its rows drift like a published one's. */
    @Test
    @Transactional
    public void aRetiredIssueStillReportsDrift() {
        PublicationSeries s = series();
        PublicationIssue i = issue(s, IssueStatus.RETIRED);
        Message m = message(Type.TEMPORARY_NOTICE, Status.PUBLISHED, null);
        member(i, m, 0);
        em.flush();

        m.setStatus(Status.CANCELLED);
        em.merge(m);
        em.flush();

        assertTrue(rowFor(memberList.members(i), m.getUid()).getDrift().contains("status"));
    }

    // ------------------------------------------------------------------ curation

    /**
     * A curated member carries who decided, when, and why.
     *
     * "Manually included" alone answers nothing anybody wants to know. The
     * override row has the rest, and a panel that needs one request per member to
     * get it renders the reasons late or not at all.
     */
    @Test
    @Transactional
    public void aCuratedMemberCarriesTheDecisionBehindIt() {
        PublicationSeries s = series();
        PublicationIssue i = issue(s, IssueStatus.PUBLISHED);
        Message m = message(Type.TEMPORARY_NOTICE, Status.PUBLISHED, null);

        IssueOverride override = new IssueOverride();
        override.setIssue(i);
        override.setMessage(m);
        override.setMessageUid(m.getUid());
        override.setKind(OverrideKind.INCLUDE);
        override.setAuthor(user("curator-" + UUID.randomUUID().toString().substring(0, 8)));
        override.setReason("dækket i uge 26");
        em.persist(override);

        IssueMember member = member(i, m, 0);
        member.setSource(MemberSource.OVERRIDE_INCLUDE);
        member.setOverride(override);
        em.merge(member);
        em.flush();

        IssueMemberVo row = rowFor(memberList.members(i), m.getUid());
        assertNotNull(row.getCuration(), "a curated member carried no curation facts");
        assertEquals("INCLUDE", row.getCuration().getKind());
        assertEquals("dækket i uge 26", row.getCuration().getReason());
        assertNotNull(row.getCuration().getAuthor());
        assertNotNull(row.getCuration().getAt());
        assertEquals("MANUAL_INCLUDE", row.getReasonCode());
    }

    /**
     * And the decision is found by uid where the member has no link to it.
     *
     * An imported row carries the curation without the foreign key, because the
     * snapshot was written before the override existed as a row. The uid is the
     * key all of them share, which is why the lookup is by uid rather than by
     * the link alone.
     */
    @Test
    @Transactional
    public void aCurationIsFoundByUidWhenTheMemberHasNoLinkToIt() {
        PublicationSeries s = series();
        PublicationIssue i = issue(s, IssueStatus.PUBLISHED);
        Message m = message(Type.TEMPORARY_NOTICE, Status.PUBLISHED, null);

        IssueOverride override = new IssueOverride();
        override.setIssue(i);
        override.setMessage(m);
        override.setMessageUid(m.getUid());
        override.setKind(OverrideKind.INCLUDE);
        override.setAuthor(user("curator-" + UUID.randomUUID().toString().substring(0, 8)));
        override.setReason("manuelt medtaget");
        em.persist(override);

        // No setOverride: the member row does not point at the decision.
        member(i, m, 0);
        em.flush();

        IssueMemberVo row = rowFor(memberList.members(i), m.getUid());
        assertNotNull(row.getCuration());
        assertEquals("manuelt medtaget", row.getCuration().getReason());
    }

    /**
     * The standing decisions include the EXCLUSIONS, which no member row can carry.
     *
     * An excluded message is not a member -- that is what excluding it means -- so
     * the member list has nowhere to hang a "withdraw this" affordance, and
     * reconstructing the decision from the audit trail reads what HAPPENED as
     * though it were what STANDS.
     */
    @Test
    @Transactional
    public void theStandingDecisionsCarryTheExclusionsTheMemberListCannot() {
        PublicationSeries s = series();
        PublicationIssue i = issue(s, IssueStatus.PUBLISHED);
        Message included = message(Type.TEMPORARY_NOTICE, Status.PUBLISHED, null);
        Message excluded = message(Type.TEMPORARY_NOTICE, Status.PUBLISHED, null);

        override(i, included, OverrideKind.INCLUDE, "manuelt medtaget");
        override(i, excluded, OverrideKind.EXCLUDE, "dækket i uge 26");
        member(i, included, 0);
        em.flush();

        List<String> memberUids = new ArrayList<>();
        memberList.members(i).forEach(r -> memberUids.add(r.getMessageUid()));
        assertFalse(memberUids.contains(excluded.getUid()),
                "an excluded message appeared in the member list");

        var decisions = memberList.standingDecisions(i);
        assertEquals(2, decisions.size());
        var exclusion = decisions.stream()
                .filter(d -> excluded.getUid().equals(d.getMessageUid()))
                .findFirst().orElseThrow(() ->
                        new AssertionError("the exclusion is unreachable from every surface"));
        assertEquals("EXCLUDE", exclusion.getKind());
        assertEquals("dækket i uge 26", exclusion.getReason());
        assertNotNull(exclusion.getAuthor());
        assertNotNull(exclusion.getAt());
    }

    private IssueOverride override(PublicationIssue issue, Message message, OverrideKind kind,
                                   String reason) {
        IssueOverride o = new IssueOverride();
        o.setIssue(issue);
        o.setMessage(message);
        o.setMessageUid(message.getUid());
        o.setKind(kind);
        o.setAuthor(user("curator-" + UUID.randomUUID().toString().substring(0, 8)));
        o.setReason(reason);
        em.persist(o);
        return o;
    }

    /** An uncurated member carries no curation block at all. */
    @Test
    @Transactional
    public void anUncuratedMemberCarriesNoCuration() {
        PublicationSeries s = series();
        PublicationIssue i = issue(s, IssueStatus.PUBLISHED);
        Message m = message(Type.TEMPORARY_NOTICE, Status.PUBLISHED, null);
        member(i, m, 0);
        em.flush();

        assertNull(rowFor(memberList.members(i), m.getUid()).getCuration(),
                "an empty curation block would say a human touched this row and left no reason, "
                        + "which the NOT NULL reason column exists to make impossible");
    }
}
