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
import org.niord.core.message.Message;
import org.niord.core.message.MessageSeries;
import org.niord.core.publication.PublicationCategory;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two admins on one publication.
 *
 * The failure being closed is not a crash. Two people open the same weekly
 * issue; one corrects the interval and saves; the second, whose form predates
 * that, saves a rename -- and because a save sends the whole object, the rename
 * carries the old interval back and silently reverts the correction. Nobody is
 * told. These tests are the four sentences that make that impossible: a stale
 * write is refused and leaves nothing behind, a matching one goes through, a
 * write that names no revision keeps the old last-write-wins behaviour, and the
 * two actions that change an issue without changing a column on it -- a curation
 * and a release -- move its revision anyway.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class OptimisticLockingTest {

    @Inject
    IssueLifecycleService lifecycle;

    @Inject
    IssueEditService editService;

    @Inject
    IssueCurationService curation;

    @Inject
    IssuePublishService publishService;

    @Inject
    IssuePreviewService previews;

    @Inject
    EntityManager em;

    // ------------------------------------------------------------------ fixtures

    private PublicationSeries series() {
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
        s.setTimeRelation(TimeRelation.PUBLISHED_IN_INTERVAL);
        s.setAliveAtCutoff(false);
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
        em.flush();
        return s;
    }

    private User user() {
        User u = new User();
        u.setUsername("u-" + UUID.randomUUID().toString().substring(0, 8));
        em.persist(u);
        return u;
    }

    private PublicationIssue openIssue(PublicationSeries s) {
        PublicationIssue i = lifecycle.create(s, new Date(1_700_000_000_000L - WEEK),
                IntervalBoundSource.STAMPED, user());
        i.setIntervalTo(new Date(1_700_000_000_000L));
        em.flush();
        return i;
    }

    private static final long WEEK = 7 * 24 * 3600_000L;

    private String message() {
        Message m = new Message();
        m.setUid(UUID.randomUUID().toString());
        m.setMessageSeries(messageSeries());
        m.setMainType(MainType.NM);
        m.setType(Type.TEMPORARY_NOTICE);
        m.setStatus(Status.PUBLISHED);
        em.persist(m);
        em.flush();
        return m.getUid();
    }

    /** The message series the fixture's criteria name, reused where it exists. */
    private MessageSeries messageSeries() {
        List<MessageSeries> found = em.createQuery(
                        "SELECT ms FROM MessageSeries ms WHERE ms.seriesId = :id", MessageSeries.class)
                .setParameter("id", "dma-nm").setMaxResults(1).getResultList();
        if (!found.isEmpty()) {
            return found.get(0);
        }
        MessageSeries ms = new MessageSeries();
        ms.setSeriesId("dma-nm");
        ms.setMainType(MainType.NM);
        em.persist(ms);
        return ms;
    }

    private void previewFor(PublicationIssue issue) {
        for (PublicationIssueDesc desc : issue.getDescs()) {
            previews.record(issue, desc.getLang(), "preview.pdf",
                    "preview-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    // =============================================================== the compare

    /**
     * The whole point: refused BEFORE anything is written.
     *
     * The sequence is the one every endpoint follows -- look the row up, compare,
     * and only then apply the body -- so the assertion that matters is not that
     * the call threw but that the issue still says what it said. A guard that
     * raised after the edit would be a guard that logged the damage.
     */
    @Test
    @Transactional
    public void aStaleWriteIsRefusedAndNothingIsWritten() {
        PublicationIssue issue = openIssue(series());
        String nameBefore = issue.getDescs().get(0).getName();

        // Somebody else's save lands first, and the revision moves.
        editService.update(issue, new IssueEditService.IssueEdit(
                java.util.Map.of("da", "Renamed by the first admin"), null, null, null), user());
        em.flush();
        int now = issue.getVersion();

        StaleVersionGuard.StaleVersionException e = assertThrows(
                StaleVersionGuard.StaleVersionException.class,
                () -> StaleVersionGuard.check(issue, now - 1));

        assertEquals(StaleVersionGuard.STALE_VERSION, e.code());
        assertEquals(now, e.stored(), "the refusal does not report the revision the row actually carries");
        assertEquals(now - 1, e.submitted(), "the refusal does not report the revision that was sent");
        assertTrue(e.getMessage().contains(issue.getPublicId()),
                "the refusal does not name what it refused: " + e.getMessage());

        // And the second admin's edit never ran, so the first one's rename stands.
        em.flush();
        em.clear();
        PublicationIssue reread = em.find(PublicationIssue.class, issue.getId());
        assertEquals("Renamed by the first admin", reread.getDescs().get(0).getName());
        assertTrue(!"Renamed by the first admin".equals(nameBefore),
                "the fixture did not actually change the name, so this proves nothing");
    }

    /** A revision that matches lets the write through. */
    @Test
    @Transactional
    public void aMatchingRevisionIsWritten() {
        PublicationIssue issue = openIssue(series());
        em.flush();

        StaleVersionGuard.check(issue, issue.getVersion());
        editService.update(issue, new IssueEditService.IssueEdit(
                java.util.Map.of("da", "Renamed against the current revision"), null, null, null), user());
        em.flush();

        assertEquals("Renamed against the current revision", issue.getDescs().get(0).getName());
    }

    /**
     * No revision at all is last-write-wins, exactly as before.
     *
     * Deliberate rather than lenient: the administration client written before
     * this field existed sends nothing, and making the field mandatory would take
     * every one of its writes down at once over a conflict that is not happening.
     */
    @Test
    @Transactional
    public void anAbsentRevisionIsWritten() {
        PublicationIssue issue = openIssue(series());
        em.flush();

        // Move it on, so the only reason this passes is that null means "do not ask".
        editService.update(issue, new IssueEditService.IssueEdit(
                java.util.Map.of("da", "Moved on"), null, null, null), user());
        em.flush();

        StaleVersionGuard.check(issue, null);
        StaleVersionGuard.check(issue.getSeries(), null);
    }

    /** The same three answers on a series, which is written through the same helper. */
    @Test
    @Transactional
    public void theSeriesTakesTheSameThreeAnswers() {
        PublicationSeries s = series();
        int at = s.getVersion();

        StaleVersionGuard.check(s, at);
        StaleVersionGuard.check(s, null);

        StaleVersionGuard.StaleVersionException e = assertThrows(
                StaleVersionGuard.StaleVersionException.class,
                () -> StaleVersionGuard.check(s, at + 5));
        assertTrue(e.getMessage().contains(s.getSeriesId()),
                "the refusal does not name the series: " + e.getMessage());
    }

    /** A null row is somebody else's NOT_FOUND, not a version conflict. */
    @Test
    public void aMissingRowIsNotAVersionConflict() {
        StaleVersionGuard.check((PublicationIssue) null, 7);
        StaleVersionGuard.check((PublicationSeries) null, 7);
    }

    // ================================================================ the bumps

    /**
     * A curation moves the issue's revision, though it writes no column on it.
     *
     * Without the forced increment the guard would be decorative on exactly the
     * endpoints that need it most: an override is a CHILD row, and inserting one
     * leaves the parent's counter where it was -- so two curators both reading
     * revision 7 would both commit at revision 7 and the second decision would
     * quietly replace the first in a member list neither of them re-reads.
     */
    @Test
    @Transactional
    public void aCurationBumpsTheIssueRevision() {
        PublicationIssue issue = openIssue(series());
        em.flush();
        int before = issue.getVersion();

        curation.curate(issue, List.of(message()), OverrideKind.EXCLUDE, user(),
                "withdrawn before the week closed");
        em.flush();

        assertTrue(issue.getVersion() > before,
                "a curation left the issue at revision " + issue.getVersion()
                        + "; two curators on one issue would never collide");
    }

    /** And so does withdrawing one, for the same reason: the member set changed. */
    @Test
    @Transactional
    public void withdrawingACurationBumpsItToo() {
        PublicationIssue issue = openIssue(series());
        String uid = message();
        curation.curate(issue, List.of(uid), OverrideKind.EXCLUDE, user(), "withdrawn for now");
        em.flush();
        int afterExclude = issue.getVersion();

        curation.clear(issue, uid, user(), "put back after all");
        em.flush();

        assertTrue(issue.getVersion() > afterExclude,
                "withdrawing a curation left the issue at revision " + issue.getVersion());
    }

    /**
     * A release moves it too.
     *
     * A publish stamps the cut-off on the issue row, so this one needs no forced
     * increment -- but it is asserted rather than assumed, because a client that
     * holds a pre-publish revision must be refused when it tries to amend or
     * retire against it.
     */
    @Test
    @Transactional
    public void aPublishBumpsTheIssueRevision() {
        PublicationIssue issue = openIssue(series());
        em.flush();
        int before = issue.getVersion();

        previewFor(issue);
        IssuePublishService.PublishResult result = publishService.publish(issue.getId(),
                new IssuePublishService.PublishRequest(false,
                        IssuePublishService.PublishRequest.ALL_WARNINGS, null,
                        new Date(1_700_000_000_000L)));
        em.flush();

        assertNotNull(result.stampedAt());
        assertEquals(IssueStatus.PUBLISHED, issue.getStatus());
        assertTrue(issue.getVersion() > before,
                "a publish left the issue at revision " + issue.getVersion()
                        + "; a form loaded before the release would still be accepted");
    }

    // ============================================================ the body reader

    /** The revision an untyped body names, and what counts as absent. */
    @Test
    public void theBodyReaderTakesANumberAStringOrNothing() {
        assertEquals(7, StaleVersionGuard.versionOf(java.util.Map.of("version", 7)));
        assertEquals(7, StaleVersionGuard.versionOf(java.util.Map.of("version", "7")));
        assertEquals(7, StaleVersionGuard.versionOf(java.util.Map.of("version", " 7 ")));
        assertNull(StaleVersionGuard.versionOf(java.util.Map.of("reason", "no version here")));
        assertNull(StaleVersionGuard.versionOf(java.util.Map.of("version", "  ")));
        assertNull(StaleVersionGuard.versionOf(null));
    }

    /**
     * A revision that is present and unreadable is refused, not ignored.
     *
     * Treating it as absent would silently give the caller the last-write-wins
     * behaviour they were trying to opt out of -- which is the one outcome a
     * client that bothered to send the field cannot detect.
     */
    @Test
    public void anUnreadableRevisionIsRefusedRatherThanIgnored() {
        assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                () -> StaleVersionGuard.versionOf(java.util.Map.of("version", "seven")));
    }
}
