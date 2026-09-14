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

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.message.Message;
import org.niord.core.message.MessageSeries;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.TestIds;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.model.message.MainType;
import org.niord.model.message.Status;
import org.niord.model.message.Type;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How long a release of a full-sized compiled annual takes, and where it goes.
 *
 * NOT A TEST AND DELIBERATELY NOT NAMED LIKE ONE. Nothing here is a rule, so
 * nothing here should be able to turn a build red: it is the fixture the timing
 * line is read from, run by name when somebody is measuring
 * ({@code -Dtest=IssueAmendScaleBench}). The assertions it does make are about
 * the fixture itself -- a run that compiled nine members instead of nine hundred
 * would print a timing line that means nothing, and that is worth catching
 * before the numbers are believed.
 *
 * The shape is the live annual's: some hundreds of frozen rows drawn from a few
 * dozen published source weeks, in two languages. The renderer is stubbed in
 * this module, which is the point rather than a limitation -- the render already
 * reports its own phases, and what this exists to expose is everything around
 * it.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class IssueAmendScaleBench {

    /** Source weeks, and members in each: the product is what the annual carries. */
    private static final int WEEKS = 30;

    private static final int MEMBERS_PER_WEEK = 30;

    private static final long T0 = 1_699_000_000_000L;

    private static final long WEEK = 7 * 24 * 3600_000L;

    @Inject
    IssuePublishService publishService;

    @Inject
    EntityManager em;

    /** The bench's own message series, made once and never the corpus's. */
    private MessageSeries messageSeries;

    @org.junit.jupiter.api.AfterEach
    public void restoreTheRenderer() {
        StubIssueRenderService.reset();
    }

    /**
     * ROLLED BACK, and for this fixture that is not tidiness.
     *
     * The database these tests run against is a long-lived container that keeps
     * whatever they commit, and the ordinary fixtures leave a handful of rows
     * behind. This one makes nine hundred messages that are alive at every later
     * cut-off, and every criteria-backed test in the module resolves over the
     * same corpus -- three committed runs of it put an in-force series past the
     * thousand-member limit, which fails tests that have nothing to do with this
     * one and cannot be explained from their own code. The message series is the
     * bench's own for the same reason: a run that somehow did commit would still
     * be invisible to anything that selects by series.
     */
    @Test
    @TestTransaction
    public void acompiledAnnualIsPublishedAndThenAmended() {
        PublicationSeries source = series(SeriesCadence.WEEKLY, TimeRelation.PUBLISHED_IN_INTERVAL);
        PublicationSeries annual = series(SeriesCadence.YEARLY, TimeRelation.COMPILED_FROM_SOURCE);
        annual.setCriteria(null);
        annual.setAliveAtCutoff(null);
        annual.setSourceSeries(source);
        em.merge(annual);

        for (int w = 0; w < WEEKS; w++) {
            frozenWeek(source, new Date(T0 + w * WEEK), new Date(T0 + (w + 1) * WEEK), w);
        }

        PublicationIssue issue = issue(annual, new Date(T0 - WEEK));
        em.flush();

        var published = publishService.publish(issue.getId(),
                new IssuePublishService.PublishRequest(IssuePublishService.PublishRequest.ALL_WARNINGS,
                        null, new Date(T0 + (WEEKS + 1) * WEEK)));
        assertEquals(WEEKS * MEMBERS_PER_WEEK, published.memberCount(),
                "the fixture did not compile the member set the timing line is supposed to describe");

        var amended = publishService.amend(issue.getId(),
                new IssuePublishService.AmendRequest(IssuePublishService.PublishRequest.ALL_WARNINGS,
                        null, "measuring where the seconds of an amend go"));
        assertEquals(WEEKS * MEMBERS_PER_WEEK, amended.memberCount());

        // AND THE OTHER END OF THE RANGE. The amend above re-froze rows that had
        // nothing to say differently, which is the cheapest an amend can be. This
        // one moves a frozen fact on every member first, so every row really does
        // have to be written -- the honest worst case, and the one a correction to
        // the corpus actually produces.
        em.createQuery("UPDATE Message m SET m.publishDateTo = :to WHERE m.uid IN "
                        + "(SELECT im.messageUid FROM IssueMember im WHERE im.issue.id = :issue)")
                .setParameter("to", new Date(T0 + 99 * WEEK))
                .setParameter("issue", issue.getId())
                .executeUpdate();
        em.clear();

        var reamended = publishService.amend(issue.getId(),
                new IssuePublishService.AmendRequest(IssuePublishService.PublishRequest.ALL_WARNINGS,
                        null, "measuring an amend where every frozen row really did change"));
        assertEquals(WEEKS * MEMBERS_PER_WEEK, reamended.memberCount());
    }

    // ------------------------------------------------------------------ fixture

    private PublicationSeries series(SeriesCadence cadence, TimeRelation relation) {
        PublicationCategory c = new PublicationCategory();
        c.setCategoryId(TestIds.category());
        c.setPriority(100);
        em.persist(c);

        PublicationSeries s = new PublicationSeries();
        s.setSeriesId(TestIds.series());
        s.setStatus(SeriesStatus.ACTIVE);
        s.setContentMode(ContentMode.GENERATED_FROM_QUERY);
        s.setReportId("some-report");
        s.setCadence(cadence);
        s.setTimeRelation(relation);
        s.setAliveAtCutoff(Boolean.FALSE);
        s.setReleaseMode(ReleaseMode.MANUAL_GATE);
        s.setNextIssueCreation(NextIssueCreation.MANUAL);
        s.setMessagePublication(MessagePublication.NONE);
        s.setNumberingScheme(NumberingScheme.NONE);
        s.setCategory(c);
        s.setDomain(TestOwnerDomain.of(em));
        // TWO LANGUAGES, because the live annual has two and the renders run side
        // by side: a one-language fixture measures a different wait.
        s.getLanguages().add("da");
        s.getLanguages().add("en");
        s.createDesc("da").setName("Bench " + cadence);
        s.createDesc("en").setName("Bench " + cadence);
        em.persist(s);
        return s;
    }

    private PublicationIssue issue(PublicationSeries s, Date intervalFrom) {
        PublicationIssue i = new PublicationIssue();
        i.setSeries(s);
        i.setPublicId(UUID.randomUUID().toString());
        i.setRepoPath("publications/" + i.getPublicId());
        i.setStatus(IssueStatus.OPEN);
        i.setIntervalFrom(intervalFrom);
        i.setIntervalFromSource(IntervalBoundSource.STAMPED);
        i.createDesc("da").setName("Bench issue");
        i.createDesc("en").setName("Bench issue");
        em.persist(i);
        return i;
    }

    /** A published source week with its member rows already frozen, in print order. */
    private void frozenWeek(PublicationSeries s, Date from, Date stamp, int week) {
        PublicationIssue i = issue(s, from);
        i.setStatus(IssueStatus.PUBLISHED);
        i.setCutoffStampedAt(stamp);
        i.setPublishedAt(stamp);
        i.setMemberCount(MEMBERS_PER_WEEK);
        em.merge(i);

        List<Message> messages = new ArrayList<>();
        for (int n = 0; n < MEMBERS_PER_WEEK; n++) {
            messages.add(message("NM-" + week + "-" + n, from));
        }
        int sortIndex = 0;
        for (Message m : messages) {
            IssueMember row = new IssueMember();
            row.setIssue(i);
            row.setMessageUid(m.getUid());
            row.setMessage(m);
            row.setSortIndex(sortIndex++);
            row.setFrozenShortId(m.getShortId());
            row.setFrozenMainType(m.getMainType().name());
            row.setFrozenType(m.getType().name());
            row.setFrozenStatus(m.getStatus().name());
            row.setFrozenPublishDateFrom(m.getPublishDateFrom());
            row.setSource(MemberSource.CRITERIA);
            em.persist(row);
        }
    }

    private Message message(String shortId, Date publishedAt) {
        Message m = new Message();
        m.setUid(UUID.randomUUID().toString());
        m.setMessageSeries(messageSeries());
        m.setShortId(shortId);
        m.setMainType(MainType.NM);
        m.setType(Type.TEMPORARY_NOTICE);
        m.setStatus(Status.PUBLISHED);
        m.setPublishDateFrom(publishedAt);
        em.persist(m);
        return m;
    }

    private MessageSeries messageSeries() {
        if (messageSeries == null) {
            messageSeries = new MessageSeries();
            messageSeries.setSeriesId(TestIds.id("bench-ms-"));
            messageSeries.setMainType(MainType.NM);
            em.persist(messageSeries);
        }
        return messageSeries;
    }
}
