package org.niord.core.publication.series;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.core.user.User;

import java.util.Date;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Creating a one-off and publishing it in ONE request.
 *
 * The one-off endpoint does create-series, create-issue, attach the link,
 * activate and publish in a single transaction, so that an admin publishing one
 * PDF makes one request instead of four in an order that cannot be got wrong.
 * This is that sequence, in that order, against the real services -- the endpoint
 * itself lives in niord-web, which has no container test harness, and the part
 * that breaks is the INTERACTION rather than anything the endpoint does alone.
 *
 * THE FLUSH IS THE POINT. IssuePublishService re-reads the issue with
 * LockModeType.PESSIMISTIC_WRITE. Taking a row lock on an issue this same
 * transaction has only just persisted, whose descs still hold unflushed changes,
 * fails with "Row was updated or deleted by another transaction" -- naming a
 * conflict with a transaction that does not exist. Measured against the deployed
 * API twice before the cause was found.
 *
 * Remove the em.flush() below and this test reproduces that failure.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class OneOffCreateSequenceTest {

    @Inject
    PublicationSeriesService seriesService;

    @Inject
    IssueLifecycleService lifecycle;

    @Inject
    IssuePublishService publishService;

    @Inject
    EntityManager em;

    private User user() {
        User u = new User();
        u.setUsername("u-" + UUID.randomUUID().toString().substring(0, 8));
        em.persist(u);
        return u;
    }

    /** A one-off exactly as the endpoint builds one: link-backed, no query. */
    private PublicationSeries oneOff() {
        PublicationCategory c = new PublicationCategory();
        c.setCategoryId("cat-" + UUID.randomUUID().toString().substring(0, 8));
        c.setPriority(100);
        em.persist(c);

        PublicationSeries s = new PublicationSeries();
        s.setSeriesId("s-" + UUID.randomUUID().toString().substring(0, 8));
        s.setStatus(SeriesStatus.DRAFT);
        s.setKind(SeriesKind.ONE_OFF);
        s.setCadence(SeriesCadence.NONE);
        s.setContentMode(ContentMode.EXTERNAL_LINK);
        // S-1 and S-2: no query, so no time relation, no criteria, no liveness filter.
        s.setTimeRelation(null);
        s.setCriteria(null);
        s.setAliveAtCutoff(null);
        s.setNumberingScheme(NumberingScheme.NONE);
        s.setNextIssueCreation(NextIssueCreation.MANUAL);
        s.setReleaseMode(ReleaseMode.MANUAL_GATE);
        s.setMessagePublication(MessagePublication.NONE);
        s.setPublicAuthority(PublicAuthority.LEGACY);
        s.setCategory(c);
        s.getLanguages().add("da");
        s.createDesc("da").setName("One-off probe");
        return s;
    }

    /**
     * The whole sequence, in one transaction, ending PUBLISHED.
     *
     * What this pins is that an admin filling in the small form once gets a live
     * publication -- not a half-made one that reached the list unactivatable, and
     * not a 500 naming a Hibernate conflict.
     */
    @Test
    @Transactional
    public void aoneOffIsCreatedActivatedAndPublishedInOneGo() {
        PublicationSeries saved = seriesService.create(oneOff());

        PublicationIssue issue = lifecycle.create(saved, new Date(), IntervalBoundSource.MANUAL, user());

        // The link is attached to the desc the lifecycle already wrote. Creating a
        // second desc for the language violates UNIQUE (entity_id, lang).
        for (PublicationIssueDesc d : issue.getDescs()) {
            d.setLink("https://example.invalid/one-off-probe");
        }
        assertEquals(1, issue.getDescs().size(),
                "the lifecycle should have written exactly one desc for the one language");

        saved.setStatus(SeriesStatus.ACTIVE);
        seriesService.update(saved);

        // WITHOUT THIS the publish below fails: it takes a pessimistic row lock on
        // an issue that has not been written yet.
        em.flush();

        previewFor(issue);
        publishService.publish(issue.getId(),
                new IssuePublishService.PublishRequest(false, Set.of(), null, null));
        em.flush();

        PublicationIssue reloaded = em.find(PublicationIssue.class, issue.getId());
        assertNotNull(reloaded);
        assertEquals(IssueStatus.PUBLISHED, reloaded.getStatus(),
                "the issue did not reach PUBLISHED, so the single-request create did not finish");
        assertEquals("https://example.invalid/one-off-probe", reloaded.getDescs().get(0).getLink(),
                "the link was lost on the way through publish");
    }

    /**
     * A publication with NO document publishes immediately.
     *
     * Three of the five one-offs in the estate are contentMode NONE -- they are
     * citation vocabulary, cited from the message editor, with no file and no
     * link. There is nothing to attach, so nothing to wait for.
     */
    @Test
    @Transactional
    public void acontentlessOneOffPublishesWithNothingAttached() {
        PublicationSeries series = oneOff();
        series.setContentMode(ContentMode.NONE);
        PublicationSeries saved = seriesService.create(series);

        PublicationIssue issue = lifecycle.create(saved, new Date(), IntervalBoundSource.MANUAL, user());
        saved.setStatus(SeriesStatus.ACTIVE);
        seriesService.update(saved);
        em.flush();

        previewFor(issue);
        publishService.publish(issue.getId(),
                new IssuePublishService.PublishRequest(false, Set.of(), null, null));
        em.flush();

        assertEquals(IssueStatus.PUBLISHED, em.find(PublicationIssue.class, issue.getId()).getStatus());
    }

    /**
     * An UPLOADED one is created and activated but left OPEN until it has bytes.
     *
     * The endpoint does not attempt to publish it: the checklist would refuse,
     * and refusing at save would turn "you still need to attach the file" into an
     * error on a form that was filled in correctly. It reaches the list ready to
     * receive its file.
     */
    @Test
    @Transactional
    public void anuploadedOneOffIsActivatedButLeftOpenUntilItHasBytes() {
        PublicationSeries series = oneOff();
        series.setContentMode(ContentMode.UPLOADED_FILE);
        PublicationSeries saved = seriesService.create(series);

        PublicationIssue issue = lifecycle.create(saved, new Date(), IntervalBoundSource.MANUAL, user());
        saved.setStatus(SeriesStatus.ACTIVE);
        seriesService.update(saved);
        em.flush();

        PublicationIssue reloaded = em.find(PublicationIssue.class, issue.getId());
        assertEquals(IssueStatus.OPEN, reloaded.getStatus(),
                "an uploaded publication with no bytes must wait for them, not fail the save");
        assertEquals(SeriesStatus.ACTIVE, reloaded.getSeries().getStatus(),
                "and its series must be ACTIVE, or the file upload has nowhere to publish to");
    }

    /**
     * Editing one afterwards works, including clearing the domain.
     *
     * The second half of the same interaction: the endpoint updates the series,
     * writes the link onto the desc the lifecycle made, and may activate. Null
     * domain means visible from every domain, which four of these publications
     * need.
     */
    @Test
    @Transactional
    public void aoneOffCanBeEditedAfterwards() {
        PublicationSeries saved = seriesService.create(oneOff());
        PublicationIssue issue = lifecycle.create(saved, new Date(), IntervalBoundSource.MANUAL, user());
        em.flush();

        // The EXISTING desc, as updateFromVo does: PublicationSeries.createDesc
        // always makes a new row, so calling it for a language that already has
        // one violates UNIQUE (entity_id, lang) -- the same trap as on the issue.
        saved.getDescs().get(0).setName("Renamed probe");
        saved.setDomain(null);
        PublicationSeries updated = seriesService.update(saved);
        for (PublicationIssueDesc d : issue.getDescs()) {
            d.setLink("https://example.invalid/renamed");
        }
        em.flush();

        PublicationSeries reloaded = seriesService.findBySeriesId(updated.getSeriesId());
        assertNotNull(reloaded);
        assertEquals("Renamed probe", reloaded.getDescs().get(0).getName());
        assertEquals(null, reloaded.getDomain(), "the domain could not be cleared back to global");
        assertEquals("https://example.invalid/renamed",
                em.find(PublicationIssue.class, issue.getId()).getDescs().get(0).getLink());
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
