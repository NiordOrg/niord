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
import org.niord.core.publication.TestIds;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.criteria.MessageSeriesCriterionVo;
import org.niord.core.publication.series.resolve.TimeRelation;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A curation decision over a list, and its withdrawal.
 *
 * A curator excluding eleven messages is taking one decision, not eleven that
 * happen to arrive together -- so the decision is all or nothing, and a refusal
 * names every message that stopped it rather than the first. Withdrawing a
 * decision is addressed by the message it was about, because that is the row
 * the curator is looking at, and it is audited with its reason like the
 * decision it undoes.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class CurationDecisionTest {

    @Inject
    IssueCurationService curation;

    @Inject
    IssueLifecycleService lifecycle;

    @Inject
    IssueAuditService auditService;

    @Inject
    EntityManager em;

    // ------------------------------------------------------------------ fixtures

    private PublicationSeries series() {
        PublicationCategory c = new PublicationCategory();
        c.setCategoryId(TestIds.category());
        c.setPriority(100);
        em.persist(c);

        PublicationSeries s = new PublicationSeries();
        s.setSeriesId(TestIds.series());
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

        s.createDesc("da").setName("Test series");
        em.persist(s);
        return s;
    }

    private User user() {
        User u = new User();
        u.setUsername(TestIds.user());
        em.persist(u);
        return u;
    }

    private PublicationIssue openIssue() {
        PublicationIssue i = lifecycle.create(series(), new Date(1_699_000_000_000L),
                IntervalBoundSource.STAMPED, user());
        em.flush();
        return i;
    }

    private String message() {
        Message m = new Message();
        m.setUid(UUID.randomUUID().toString());
        m.setMainType(MainType.NM);
        m.setType(Type.TEMPORARY_NOTICE);
        m.setStatus(Status.PUBLISHED);
        em.persist(m);
        em.flush();
        return m.getUid();
    }

    private List<IssueAuditEntry> entries(PublicationIssue issue, AuditAction action) {
        return auditService.forIssue(issue).stream().filter(a -> action == a.getAction()).toList();
    }

    // ================================================================== curate

    /** One decision over three messages: three overrides, three entries, one reason. */
    @Test
    @Transactional
    public void aDecisionOverAListWritesAnOverridePerMessage() {
        PublicationIssue issue = openIssue();
        List<String> uids = List.of(message(), message(), message());

        List<IssueOverride> written = curation.curate(issue, uids, OverrideKind.EXCLUDE, user(),
                "cancelled before the week closed");
        em.flush();

        assertEquals(3, written.size());
        assertEquals(3, curation.forIssue(issue).size());
        List<IssueAuditEntry> excluded = entries(issue, AuditAction.OVERRIDE_EXCLUDED);
        assertEquals(3, excluded.size());
        assertTrue(excluded.stream().allMatch(a -> "cancelled before the week closed".equals(a.getReason())));
    }

    /** One unknown message stops the whole decision, and the refusal names it. */
    @Test
    @Transactional
    public void anUnknownMessageRefusesTheWholeDecision() {
        PublicationIssue issue = openIssue();
        String known = message();
        List<String> uids = List.of(known, "no-such-message", "nor-this-one");

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> curation.curate(issue, uids, OverrideKind.INCLUDE, user(), "seen in the field"));

        assertEquals("OVERRIDE_MESSAGE_NOT_FOUND", e.code());
        assertTrue(e.getMessage().contains("no-such-message") && e.getMessage().contains("nor-this-one"),
                "every offender is named, not the first: " + e.getMessage());
        assertTrue(curation.forIssue(issue).isEmpty(), "nothing was written for the message that did exist");
    }

    /** A decision that names nothing, or too much, is not a decision. */
    @Test
    @Transactional
    public void anEmptyOrOversizedDecisionIsRefused() {
        PublicationIssue issue = openIssue();

        assertEquals("NO_MESSAGES", assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                () -> curation.curate(issue, List.of(), OverrideKind.EXCLUDE, user(), "nothing here")).code());

        List<String> tooMany = new ArrayList<>();
        for (int i = 0; i <= IssueCurationService.MAX_CURATED_AT_ONCE; i++) {
            tooMany.add("m-" + i);
        }
        assertEquals("TOO_MANY_MESSAGES", assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                () -> curation.curate(issue, tooMany, OverrideKind.EXCLUDE, user(), "the whole year")).code());
    }

    /** A reason of one keystroke is not a reason. */
    @Test
    @Transactional
    public void aDecisionWithoutAReadableReasonIsRefused() {
        PublicationIssue issue = openIssue();
        String uid = message();

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> curation.curate(issue, List.of(uid), OverrideKind.EXCLUDE, user(), " x "));
        assertEquals("OVERRIDE_REASON_REQUIRED", e.code());
    }

    // =================================================================== clear

    /** Withdrawing a decision removes the override and records why. */
    @Test
    @Transactional
    public void clearingRemovesTheOverrideAndAuditsTheReason() {
        PublicationIssue issue = openIssue();
        String uid = message();
        curation.curate(issue, List.of(uid), OverrideKind.EXCLUDE, user(), "cancelled");
        em.flush();

        curation.clear(issue, uid, user(), "  it was reinstated after all  ");
        em.flush();

        assertTrue(curation.forIssue(issue).isEmpty());
        List<IssueAuditEntry> removed = entries(issue, AuditAction.OVERRIDE_REMOVED);
        assertEquals(1, removed.size());
        assertEquals("it was reinstated after all", removed.get(0).getReason(), "trimmed, and kept");
    }

    /** Clearing a message no decision names is refused rather than silently agreed with. */
    @Test
    @Transactional
    public void clearingWhereNothingWasDecidedIsRefused() {
        PublicationIssue issue = openIssue();

        IssueLifecycleService.TransitionRefusedException e =
                assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                        () -> curation.clear(issue, message(), user(), "never mind"));
        assertEquals("OVERRIDE_NOT_FOUND", e.code());
    }

    /** Once the member set is frozen, neither direction is open. */
    @Test
    @Transactional
    public void aFrozenIssueTakesNoDecisions() {
        PublicationIssue issue = openIssue();
        String uid = message();
        curation.curate(issue, List.of(uid), OverrideKind.EXCLUDE, user(), "cancelled");
        issue.setStatus(IssueStatus.PUBLISHED);
        em.flush();

        assertEquals("ISSUE_NOT_OPEN", assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                () -> curation.curate(issue, List.of(message()), OverrideKind.EXCLUDE, user(), "too late")).code());
        assertEquals("ISSUE_NOT_OPEN", assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                () -> curation.clear(issue, uid, user(), "too late as well")).code());
        assertEquals(1, curation.forIssue(issue).size(), "the frozen decision is untouched");
    }
}
