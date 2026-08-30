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
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.core.user.User;

import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-12. How many issues of a series have been released.
 *
 * The editor locks the citation channel off this number (S-18): once an issue is
 * out, every citation into it lives in whichever message field the series was
 * configured to use, moving the channel makes them unfindable, and re-applying
 * appends a duplicate rather than replacing. Nothing removes a citation, so the
 * lock is the only protection -- and a screen that could not tell whether a
 * series had ever released would either lock every series or none.
 *
 * RETIRED COUNTS. It was published; the citations it wrote are still sitting in
 * the messages. That is the same predicate hasPublishedIssue uses, and the two
 * are asserted together here so a future divergence shows up as a failure rather
 * than as a disabled control beside a count of zero.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class PublishedIssueCountTest {

    @Inject
    PublicationSeriesService seriesService;

    @Inject
    IssueLifecycleService lifecycle;

    @Inject
    IssuePublishService publishService;

    @Inject
    EntityManager em;

    private PublicationSeries series() {
        PublicationCategory c = new PublicationCategory();
        c.setCategoryId(TestIds.category());
        c.setPriority(100);
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
        s.setPublicAuthority(PublicAuthority.LEGACY);
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
        em.flush();
        return s;
    }

    private User user() {
        User u = new User();
        u.setUsername(TestIds.user());
        em.persist(u);
        return u;
    }

    private PublicationIssue publishedIssue(PublicationSeries s, Date from, Date stamp) {
        PublicationIssue i = lifecycle.create(s, from, IntervalBoundSource.STAMPED, user());
        em.flush();
        previewFor(i);
        publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(false,
                        IssuePublishService.PublishRequest.ALL_WARNINGS, user(), stamp));
        em.flush();
        return em.find(PublicationIssue.class, i.getId());
    }

    /** A series with nothing released counts zero -- and hands the channel back. */
    @Test
    @Transactional
    public void aSeriesWithNoReleasedIssueCountsZero() {
        PublicationSeries s = series();

        assertEquals(0, seriesService.publishedIssueCount(s));
        assertEquals(0, PublicationSeriesService.publishedIssueCountOf(
                        seriesService.publishedIssueCounts(), s.getSeriesId()),
                "a series absent from the grouped result has released nothing, which is 0 rather "
                        + "than 'not asked'");
        assertTrue(!seriesService.hasPublishedIssue(s));
    }

    /** An OPEN issue is not a release: nothing has been cited yet. */
    @Test
    @Transactional
    public void anOpenIssueDoesNotCount() {
        PublicationSeries s = series();
        lifecycle.create(s, new Date(1_699_000_000_000L), IntervalBoundSource.STAMPED, user());
        em.flush();

        assertEquals(0, seriesService.publishedIssueCount(s));
        assertTrue(!seriesService.hasPublishedIssue(s), "and the boolean agrees");
    }

    /** Each published issue counts once. */
    @Test
    @Transactional
    public void publishedIssuesCount() {
        PublicationSeries s = series();
        publishedIssue(s, new Date(1_699_000_000_000L), new Date(1_700_000_000_000L));
        publishedIssue(s, new Date(1_700_000_000_000L), new Date(1_700_600_000_000L));

        assertEquals(2, seriesService.publishedIssueCount(s));
        assertTrue(seriesService.hasPublishedIssue(s));
    }

    /**
     * A RETIRED issue still counts.
     *
     * Retiring withdraws a document from the workflow, not from history: the
     * citations it wrote are still in the messages, so the channel stays locked.
     */
    @Test
    @Transactional
    public void aRetiredIssueStillCounts() {
        PublicationSeries s = series();
        PublicationIssue issue = publishedIssue(s, new Date(1_699_000_000_000L),
                new Date(1_700_000_000_000L));

        lifecycle.retire(issue, user(), "superseded by a corrected edition");
        em.flush();

        assertEquals(IssueStatus.RETIRED, em.find(PublicationIssue.class, issue.getId()).getStatus());
        assertEquals(1, seriesService.publishedIssueCount(s),
                "it was published; retiring it does not un-cite it");
        assertTrue(seriesService.hasPublishedIssue(s));
    }

    /**
     * The list answer and the single answer are the same answer.
     *
     * The grouped query exists so a list of fifty series is one query rather than
     * fifty, and the moment it disagrees with the per-series count the editor
     * shows one number in the table and locks a different control in the form.
     */
    @Test
    @Transactional
    public void theGroupedCountAgreesWithThePerSeriesCount() {
        PublicationSeries empty = series();
        PublicationSeries released = series();
        publishedIssue(released, new Date(1_699_000_000_000L), new Date(1_700_000_000_000L));
        PublicationIssue retired = publishedIssue(released, new Date(1_700_000_000_000L),
                new Date(1_700_600_000_000L));
        lifecycle.retire(retired, user(), "withdrawn while a correction was prepared");
        em.flush();

        Map<String, Integer> grouped = seriesService.publishedIssueCounts();

        assertEquals(seriesService.publishedIssueCount(empty),
                PublicationSeriesService.publishedIssueCountOf(grouped, empty.getSeriesId()));
        assertEquals(2, PublicationSeriesService.publishedIssueCountOf(grouped, released.getSeriesId()),
                "one published and one retired: both were released");
        assertEquals(seriesService.publishedIssueCount(released),
                PublicationSeriesService.publishedIssueCountOf(grouped, released.getSeriesId()));
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
