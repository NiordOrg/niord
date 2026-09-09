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
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.TestIds;
import org.niord.core.publication.vo.MessagePublication;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A ONE-OFF PUBLICATION IS DESCRIBED BY THREE INDEPENDENT FACTS, and this is the
 * one that decides WHEN.
 *
 * The public period is hand-set at both ends, editable after release, and a
 * start in the future is ordinary rather than exceptional: it is how a
 * publication is prepared. Give it the day it goes public, release it today, and
 * it sits PUBLISHED and off the public site until that day arrives -- with
 * nothing having to run on the day, because the public listing asks whether the
 * period covers the instant it is read at and not whether anybody pressed
 * anything.
 *
 * WHAT WOULD BREAK THAT is the release stamping its own instant over the
 * prepared one. Publishing opens the window, and a publish that overwrote a
 * period somebody set would put the document on the public site the moment it
 * was released -- the one outcome the preparation existed to prevent, and one
 * nothing on the screen would explain. That is the first test here, and it is
 * asserted against the public adapter rather than against the column, because
 * what is claimed is about the public site rather than about a field.
 *
 * The endpoint that sets the period lives in the web module, which has no
 * container harness; the rule, the write and the audit entry it delegates to are
 * here, which is what makes the refusals and the four audit keys assertable at
 * all.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class OneOffWindowTest {

    private static final long DAY = 24L * 3600_000L;

    @Inject
    PublicationSeriesService seriesService;

    @Inject
    IssueLifecycleService lifecycle;

    @Inject
    IssuePublishService publishService;

    @Inject
    IssuePublicWindowService windowService;

    @Inject
    IssueAuditService auditService;

    @Inject
    PublicationPublicAdapter adapter;

    @Inject
    EntityManager em;

    // ==================================================== the prepared period

    /**
     * A PERIOD SET BEFORE THE RELEASE SURVIVES IT, and the publication is not on
     * the public site until it opens.
     *
     * Both halves matter and neither implies the other. Keeping the columns is
     * what the publish path has to do; staying off the public list is what the
     * admin was promised, and it is decided by the listing predicate rather than
     * by anything this action writes.
     */
    @Test
    @Transactional
    public void apreparedPeriodSurvivesTheRelease() {
        PublicationSeries series = oneOff();
        PublicationIssue issue = lifecycle.create(series, new Date(), IntervalBoundSource.MANUAL, null);

        Date opens = new Date(System.currentTimeMillis() + 30 * DAY);
        Date closes = new Date(opens.getTime() + 90 * DAY);
        assertTrue(windowService.set(issue, opens, closes, null),
                "setting a period on a publication that had none is a change");

        activateAndPublish(series, issue);
        em.clear();

        PublicationIssue released = em.find(PublicationIssue.class, issue.getId());
        assertEquals(IssueStatus.PUBLISHED, released.getStatus());
        assertEquals(opens.getTime(), released.getPublicFrom().getTime(),
                "the release stamped its own instant over the day somebody chose, so the "
                        + "publication went public today instead of on that day");
        assertEquals(closes.getTime(), released.getPublicTo().getTime(),
                "the end of the prepared period was lost at release");
        assertEquals(PublicWindowSource.MANUAL, released.getPublicWindowSource(),
                "the period was decided by hand and must stay recorded as such, or the next "
                        + "action is free to re-derive it");

        Date now = new Date();
        assertFalse(publicIds(now, now).contains(released.getPublicId()),
                "a publication whose period opens in a month is on the public site today");
        Date justAfterOpening = new Date(opens.getTime() + 3600_000L);
        assertTrue(publicIds(justAfterOpening, justAfterOpening).contains(released.getPublicId()),
                "the publication is on no public site even once its period has opened, so "
                        + "preparing one publishes nothing at all");
    }

    /**
     * With no period prepared, the release IS the start.
     *
     * The default, and the shape every one-off had before the period became
     * editable: publishing a document with no decision recorded about when it goes
     * public means it goes public now.
     */
    @Test
    @Transactional
    public void areleaseWithNothingPreparedStartsAtTheReleaseInstant() {
        PublicationSeries series = oneOff();
        PublicationIssue issue = lifecycle.create(series, new Date(), IntervalBoundSource.MANUAL, null);

        Date stamp = new Date();
        series.setStatus(SeriesStatus.ACTIVE);
        seriesService.update(series);
        em.flush();
        publishService.publish(issue.getId(),
                new IssuePublishService.PublishRequest(Set.of(), null, stamp));
        em.flush();
        em.clear();

        PublicationIssue released = em.find(PublicationIssue.class, issue.getId());
        assertEquals(stamp.getTime(), released.getPublicFrom().getTime(),
                "a publication released with no prepared period has to start at the release");
        assertNull(released.getPublicTo(),
                "open-ended is the shape most of these have; the release must not invent an end");
        assertEquals(PublicWindowSource.DERIVED, released.getPublicWindowSource(),
                "nobody decided this period, and recording it as MANUAL would make a later "
                        + "action leave it alone as if somebody had");
    }

    // ========================================================== the audit entry

    /**
     * Changing the period of a RELEASED publication records both ends, before and
     * after.
     *
     * The four keys are the entry. "The period changed" answers nothing an admin
     * came to the history for -- they are there because a document is on or off
     * the public site when they did not expect it -- and the keys are read by a
     * panel in another repository, so renaming one here breaks it silently.
     */
    @Test
    @Transactional
    @SuppressWarnings("unchecked")
    public void changingThePeriodOfAReleasedPublicationIsAudited() {
        PublicationSeries series = oneOff();
        PublicationIssue issue = lifecycle.create(series, new Date(), IntervalBoundSource.MANUAL, null);
        Date stamp = new Date();
        activateAndPublish(series, issue, stamp);

        PublicationIssue released = em.find(PublicationIssue.class, issue.getId());
        Date movedStart = new Date(stamp.getTime() - 7 * DAY);
        Date end = new Date(stamp.getTime() + 30 * DAY);
        assertTrue(windowService.set(released, movedStart, end, null));
        em.flush();

        IssueAuditEntry entry = auditService.forIssue(released).stream()
                .filter(a -> a.getAction() == AuditAction.VISIBILITY_WINDOW_CHANGED)
                .findFirst().orElse(null);
        assertNotNull(entry, "no VISIBILITY_WINDOW_CHANGED entry was written");

        Map<String, Object> detail = (Map<String, Object>) entry.getDetail();
        assertEquals(Long.valueOf(stamp.getTime()), asLong(detail.get("fromBefore")),
                "fromBefore must be the start the publication actually had, or the reader "
                        + "cannot tell what was changed away from");
        assertEquals(Long.valueOf(movedStart.getTime()), asLong(detail.get("fromAfter")));
        assertTrue(detail.containsKey("toBefore"),
                "the key has to be there even when the end was not set, or a panel cannot "
                        + "tell 'it was open-ended' from 'this entry does not say'");
        assertNull(detail.get("toBefore"),
                "the publication was open-ended, and the entry has to be able to say so");
        assertEquals(Long.valueOf(end.getTime()), asLong(detail.get("toAfter")));
    }

    /** The same period set again is not a decision, and writes no history line. */
    @Test
    @Transactional
    public void settingThePeriodItAlreadyHasChangesNothing() {
        PublicationSeries series = oneOff();
        PublicationIssue issue = lifecycle.create(series, new Date(), IntervalBoundSource.MANUAL, null);

        Date opens = new Date(System.currentTimeMillis() + DAY);
        assertTrue(windowService.set(issue, opens, null, null));
        int unchanged = series.getVersion();
        assertFalse(windowService.set(issue, new Date(opens.getTime()), null, null),
                "a save that carries back the period the publication already has is not a "
                        + "change, and a history full of lines nobody took is a history nobody reads");
        assertEquals(unchanged, series.getVersion(),
                "a save that changed nothing spent the caller's revision anyway, so the next "
                        + "real edit from the same form would be refused for a collision with itself");
    }

    // ====================================================== the shared revision

    /**
     * A PERIOD CHANGE MOVES THE SERIES' REVISION, because that is the counter the
     * form is checked against.
     *
     * The period is stored on the ISSUE while the one-off form is version-checked
     * against the SERIES -- one revision covers both rows on that surface. Without
     * this the check is decorative on the action it matters most on: two admins
     * both load the publication at revision 7, both pass at revision 7, and the
     * second one's period silently replaces the first's on the decision that puts
     * a document on the public site or takes it off.
     *
     * Both halves are asserted, and neither implies the other. The counter has to
     * move ON THE ROW, or the next request re-reads the old one; and on the
     * INSTANCE THE RESPONSE IS BUILT FROM, because the endpoint hands that series
     * straight back and the revision on it is the token the form composes its next
     * write against -- one that moved only at commit would hand back the revision
     * just spent and refuse the caller's own next save.
     *
     * The refusal is STALE_VERSION, which the catalogue maps to 409; that mapping
     * is asserted where the catalogue lives.
     */
    @Test
    @Transactional
    public void aperiodChangeMovesTheSeriesRevision() {
        PublicationSeries series = oneOff();
        PublicationIssue issue = lifecycle.create(series, new Date(), IntervalBoundSource.MANUAL, null);
        em.flush();

        Integer seriesKey = series.getId();
        int sent = series.getVersion();

        Date opens = new Date(System.currentTimeMillis() + DAY);
        assertTrue(windowService.set(issue, opens, null, null));

        int now = series.getVersion();
        assertTrue(now > sent,
                "the period was written on the issue and the series' revision stayed at " + sent
                        + ", so a second form loaded at that revision would be allowed to overwrite "
                        + "this period without either admin being told");

        StaleVersionGuard.StaleVersionException stale = assertThrows(
                StaleVersionGuard.StaleVersionException.class,
                () -> StaleVersionGuard.check(series, sent));
        assertEquals(StaleVersionGuard.STALE_VERSION, stale.code());
        assertEquals(now, stale.stored());
        assertEquals(Integer.valueOf(sent), stale.submitted());

        // And a form that re-read is not refused: the revision handed back with the
        // period is one the caller can immediately write against.
        assertDoesNotThrow(() -> StaleVersionGuard.check(series, now));

        em.flush();
        em.clear();
        assertEquals(now, em.find(PublicationSeries.class, seriesKey).getVersion(),
                "the revision moved only in memory, so the next request reads the old one back "
                        + "and the guard is closed for exactly as long as one transaction");
    }

    // ============================================================ the refusals

    /** A period the wrong way round is refused rather than clamped. */
    @Test
    @Transactional
    public void aperiodThatEndsBeforeItStartsIsRefused() {
        PublicationSeries series = oneOff();
        PublicationIssue issue = lifecycle.create(series, new Date(), IntervalBoundSource.MANUAL, null);

        Date opens = new Date(System.currentTimeMillis() + 30 * DAY);
        Date closes = new Date(opens.getTime() - DAY);

        PublicationException refusal = assertThrows(PublicationException.class,
                () -> windowService.set(issue, opens, closes, null));
        assertEquals(IssuePublicWindowService.INVALID, refusal.code());
        assertNull(issue.getPublicFrom(), "the refused period was written anyway");
    }

    /**
     * A released publication may not be left without a start.
     *
     * It would be on no public site at all, and nothing on the screen would say
     * why: the row reads PUBLISHED.
     */
    @Test
    @Transactional
    public void areleasedPublicationMayNotLoseItsStart() {
        PublicationSeries series = oneOff();
        PublicationIssue issue = lifecycle.create(series, new Date(), IntervalBoundSource.MANUAL, null);
        activateAndPublish(series, issue);

        PublicationIssue released = em.find(PublicationIssue.class, issue.getId());
        PublicationException refusal = assertThrows(PublicationException.class,
                () -> windowService.set(released, null, null, null));
        assertEquals(IssuePublicWindowService.INVALID, refusal.code());
    }

    /**
     * A publication still being assembled may leave it open, because there the
     * answer really is "not decided yet".
     */
    @Test
    @Transactional
    public void anunreleasedPublicationMayHaveNoStartYet() {
        PublicationSeries series = oneOff();
        PublicationIssue issue = lifecycle.create(series, new Date(), IntervalBoundSource.MANUAL, null);

        Date end = new Date(System.currentTimeMillis() + 30 * DAY);
        assertTrue(windowService.set(issue, null, end, null));
        assertNull(issue.getPublicFrom());
        assertEquals(end.getTime(), issue.getPublicTo().getTime());

        // And the release supplies the start the decision said nothing about,
        // while leaving the end that it did.
        Date stamp = new Date();
        activateAndPublish(series, issue, stamp);
        em.clear();

        PublicationIssue released = em.find(PublicationIssue.class, issue.getId());
        assertEquals(stamp.getTime(), released.getPublicFrom().getTime(),
                "a hand-set END says nothing about when the publication begins; the release does");
        assertEquals(end.getTime(), released.getPublicTo().getTime(),
                "the hand-set end was overwritten by the release");
    }

    // ------------------------------------------------------------------ pieces

    /** A one-off exactly as its endpoint builds one: no document, no query. */
    private PublicationSeries oneOff() {
        PublicationCategory c = new PublicationCategory();
        c.setCategoryId(TestIds.category());
        c.setPriority(100);
        // The publication has to be one the public list would actually serve, or
        // the two assertions about the public site are true for the wrong reason.
        c.setPublish(true);
        em.persist(c);

        PublicationSeries s = new PublicationSeries();
        s.setSeriesId(TestIds.series());
        s.setStatus(SeriesStatus.DRAFT);
        s.setKind(SeriesKind.ONE_OFF);
        s.setCadence(SeriesCadence.NONE);
        s.setContentMode(ContentMode.NONE);
        s.setTimeRelation(null);
        s.setCriteria(null);
        s.setAliveAtCutoff(null);
        s.setNumberingScheme(NumberingScheme.NONE);
        s.setNextIssueCreation(NextIssueCreation.MANUAL);
        s.setReleaseMode(ReleaseMode.MANUAL_GATE);
        s.setMessagePublication(MessagePublication.NONE);
        // NEW, because what is being asserted is what the public site shows, and
        // the adapter's new half serves only series that have cut over.
        s.setPublicAuthority(PublicAuthority.NEW);
        s.setCategory(c);
        s.setDomain(TestOwnerDomain.of(em));
        s.getLanguages().add("da");
        s.createDesc("da").setName("One-off period probe");
        return seriesService.create(s);
    }

    private void activateAndPublish(PublicationSeries series, PublicationIssue issue) {
        activateAndPublish(series, issue, null);
    }

    private void activateAndPublish(PublicationSeries series, PublicationIssue issue, Date stamp) {
        series.setStatus(SeriesStatus.ACTIVE);
        seriesService.update(series);
        // The publish takes a pessimistic row lock on an issue this transaction has
        // only just written; without the flush it fails naming a conflict with a
        // transaction that does not exist.
        em.flush();
        publishService.publish(issue.getId(),
                new IssuePublishService.PublishRequest(Set.of(), null, stamp));
        em.flush();
    }

    private List<String> publicIds(Date from, Date to) {
        return adapter.list(from, to).stream()
                .map(PublicationPublicAdapter.PublicPublication::publicationId)
                .toList();
    }

    private static Long asLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
