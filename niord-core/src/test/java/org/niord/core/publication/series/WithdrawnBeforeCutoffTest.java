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
import org.niord.core.message.MessageHistory;
import org.niord.core.message.MessageSeries;
import org.niord.core.publication.series.resolve.CriteriaMissCode;
import org.niord.core.publication.series.resolve.CriteriaMissVo;
import org.niord.core.publication.series.resolve.Interval;
import org.niord.core.publication.series.resolve.MessageFacts;
import org.niord.core.publication.series.resolve.ResolutionWarningCode;
import org.niord.core.publication.series.resolve.ResolutionWarningVo;
import org.niord.core.publication.series.resolve.ResolvedCriteria;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.model.message.MainType;
import org.niord.model.message.Status;
import org.niord.model.message.Type;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R-xxxii on the real reader: the withdrawal instant comes off the message
 * history, so this runs against the database rather than against hand-built
 * facts.
 *
 * Three withdrawn messages, one issue window, one difference between them --
 * when the cancel happened. Before the cut-off: out, as a NOT_ALIVE_AT_CUTOFF
 * miss naming the instant. After the cut-off: in, and the acknowledgeable
 * warning names it. Undated: in, by the date alone, and the warning names it too.
 * Every history row that is NOT a withdrawal is planted as well, so the reader
 * is shown to pick the withdrawal and not merely the oldest row.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class WithdrawnBeforeCutoffTest {

    @Inject
    MemberResolutionService resolver;

    @Inject
    EntityManager em;

    private static final long HOUR = 3600_000L;
    private static final long DAY = 24 * HOUR;

    @BindsRule({"RI-4"})
    @Test
    @Transactional
    public void theWithdrawalInstantComesOffTheHistory() {
        Date cutoff = new Date(1_788_343_200_000L); // 2 Sep 2026 12:00 CEST
        Date previous = new Date(cutoff.getTime() - 7 * DAY);
        Date published = new Date(cutoff.getTime() - 5 * DAY);
        Date validityEnd = new Date(cutoff.getTime() + 21 * DAY); // an editor-set end a cancel never moves
        Interval window = new Interval(previous, cutoff);

        // A series of its own, so the shared corpus cannot reach the assertions.
        MessageSeries series = series("r32-" + UUID.randomUUID());
        ResolvedCriteria weekly = new ResolvedCriteria(
                TimeRelation.PUBLISHED_IN_INTERVAL, Set.of(series.getSeriesId()), Set.of(), true);

        Message cancelledBefore = cancelled(series, published, validityEnd);
        history(cancelledBefore, Status.DRAFT, new Date(published.getTime() - HOUR));
        history(cancelledBefore, Status.PUBLISHED, published);
        Date cancelInstant = new Date(cutoff.getTime() - 4 * DAY);
        history(cancelledBefore, Status.CANCELLED, cancelInstant);

        Message cancelledAfter = cancelled(series, published, validityEnd);
        history(cancelledAfter, Status.PUBLISHED, published);
        history(cancelledAfter, Status.CANCELLED, new Date(cutoff.getTime() + HOUR));

        Message undated = cancelled(series, published, validityEnd);

        em.flush();

        MemberResolutionService.Resolution r = resolver.resolve(weekly, window, null, null);

        assertEquals(3, r.candidateCount(), "all three are candidates; the predicate decides");
        assertFalse(r.members().contains(cancelledBefore.getUid()),
                "cancelled four days before the cut-off, yet a member because its validity end lies after it");
        assertTrue(r.members().contains(cancelledAfter.getUid()),
                "a cancel after the cut-off must not reach back into the issue");
        assertTrue(r.members().contains(undated.getUid()),
                "with nothing dating the withdrawal, publishDateTo alone must decide -- and it says alive");

        List<CriteriaMissVo> misses = r.missesOf(CriteriaMissCode.NOT_ALIVE_AT_CUTOFF);
        assertEquals(1, misses.size(), "exactly the early cancel is a NOT_ALIVE_AT_CUTOFF miss");
        assertEquals(cancelledBefore.getUid(), misses.get(0).messageUid());
        assertEquals(cancelInstant.getTime(), misses.get(0).detail().get("withdrawnAt"),
                "the miss must name the cancel, not the DRAFT or PUBLISHED rows before it");
        assertEquals(validityEnd.getTime(), misses.get(0).detail().get("publishDateTo"));

        Optional<ResolutionWarningVo> warning = r.warning(ResolutionWarningCode.CANCELLED_BUT_DATE_ALIVE);
        assertTrue(warning.isPresent(), "the two withdrawn members must still be flagged");
        assertEquals(Set.of(cancelledAfter.getUid(), undated.getUid()), Set.copyOf(warning.get().messageUids()),
                "the warning names the withdrawn MEMBERS -- the early cancel is a miss, not a warning");

        // The sticky regime never applied the alive clause; the status half is part of it.
        ResolvedCriteria sticky = new ResolvedCriteria(
                TimeRelation.PUBLISHED_IN_INTERVAL, Set.of(series.getSeriesId()), Set.of(), false);
        assertEquals(3, resolver.resolve(sticky, window, null, null).members().size(),
                "aliveAtCutoff = false must ignore the withdrawal instant too");

        // The single-message reader reads the same instant off the loaded entity.
        em.clear();
        Message reloaded = em.createQuery("SELECT m FROM Message m WHERE m.uid = :uid", Message.class)
                .setParameter("uid", cancelledBefore.getUid()).getSingleResult();
        MessageFacts facts = MemberResolutionService.allFactsOf(reloaded);
        assertEquals(cancelInstant, facts.withdrawnAt(), "allFactsOf() must read the cancel off the history");

        Message reloadedUndated = em.createQuery("SELECT m FROM Message m WHERE m.uid = :uid", Message.class)
                .setParameter("uid", undated.getUid()).getSingleResult();
        assertNull(MemberResolutionService.allFactsOf(reloadedUndated).withdrawnAt(),
                "no history row, no instant");
    }

    // ------------------------------------------------------------------ fixtures

    private Message cancelled(MessageSeries series, Date publishedAt, Date validityEnd) {
        Message m = new Message();
        m.setUid(UUID.randomUUID().toString());
        m.setMessageSeries(series);
        m.setMainType(MainType.NM);
        m.setType(Type.PERMANENT_NOTICE);
        m.setStatus(Status.CANCELLED);
        m.setPublishDateFrom(publishedAt);
        m.setPublishDateTo(validityEnd);
        em.persist(m);
        return m;
    }

    private void history(Message m, Status status, Date at) {
        MessageHistory h = new MessageHistory();
        h.setMessage(m);
        h.setStatus(status);
        h.setCreated(at);
        h.setVersion(m.getHistory().size() + 1);
        em.persist(h);
        m.getHistory().add(h);
    }

    private MessageSeries series(String seriesId) {
        MessageSeries ms = new MessageSeries();
        ms.setSeriesId(seriesId);
        ms.setMainType(MainType.NM);
        em.persist(ms);
        return ms;
    }
}
