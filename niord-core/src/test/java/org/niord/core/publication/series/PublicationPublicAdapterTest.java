package org.niord.core.publication.series;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.publication.Publication;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.vo.MessagePublication;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The public adapter: the window mapping, the eligibility predicate, and the
 * transition union.
 *
 * The window test is the important one. The ledger flags that failure twice,
 * and it is invisible in code review because the wrong mapping is the one the
 * field names suggest.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class PublicationPublicAdapterTest {

    @Inject
    PublicationPublicAdapter adapter;

    @Inject
    IssueLifecycleService lifecycle;

    @Inject
    IssuePublishService publishService;

    @Inject
    EntityManager em;

    // ------------------------------------------------------------------ fixtures

    private PublicationSeries series(PublicAuthority authority) {
        PublicationCategory c = new PublicationCategory();
        c.setCategoryId("cat-" + UUID.randomUUID().toString().substring(0, 8));
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
        s.setPublicAuthority(authority);
        s.setMessagePublication(MessagePublication.NONE);
        s.setNumberingScheme(NumberingScheme.ISO_WEEK_YEAR);
        s.setCategory(c);
        s.getLanguages().add("da");
        s.createDesc("da").setName("Test series");
        em.persist(s);
        return s;
    }

    private PublicationIssue publishAt(PublicationSeries s, Date intervalFrom, Date stamp) {
        PublicationIssue i = lifecycle.create(s, intervalFrom, IntervalBoundSource.STAMPED, null);
        em.flush();
        publishService.publish(i.getId(),
                new IssuePublishService.PublishRequest(false, Set.of(), null, stamp));
        em.flush();
        return em.find(PublicationIssue.class, i.getId());
    }

    // ================================================== the one-period offset

    /**
     * After publishing issue N+1, the public list with no window returns N+1.
     *
     * This is the failure the ledger flags twice. The public window is offset
     * from the CONTENT interval by a whole period: an issue covering week 33 goes
     * public when week 33 closes. Mapping intervalFrom/intervalTo onto the
     * publish dates -- the mapping the field names invite -- gives every issue
     * the previous period's window, so the newest issue is never current and the
     * site shows last week's EfS from the moment of cutover.
     */
    @Test
    @Transactional
    public void theNewestPublishedIssueIsTheCurrentOne() {
        PublicationSeries s = series(PublicAuthority.NEW);

        long week = 7L * 24 * 3600_000L;
        Date firstInterval = new Date(1_700_000_000_000L);
        Date firstStamp = new Date(firstInterval.getTime() + week);

        PublicationIssue first = publishAt(s, firstInterval, firstStamp);
        PublicationIssue second = publishAt(s, firstStamp, new Date(firstStamp.getTime() + week));
        em.flush();
        em.clear();

        // "now" inside the second issue's window.
        Date now = new Date(second.getCutoffStampedAt().getTime() + 3600_000L);

        // Scoped to THIS series. Quarkus @Transactional on a test commits rather
        // than rolling back, so the global list also holds every issue the other
        // tests published -- and counting those would be measuring the fixtures,
        // not the mapping.
        List<PublicationPublicAdapter.PublicPublication> current = adapter.list(now, now).stream()
                .filter(pp -> s.getSeriesId().equals(pp.seriesId())).toList();

        assertEquals(1, current.size(),
                "expected exactly one current issue for this series, got " + current.size()
                        + "; two means the predecessor was not capped");
        assertEquals(second.getPublicId(), current.get(0).publicationId(),
                "the public list returned the PREVIOUS issue; that is the one-period offset, and it means "
                        + "the site shows last week's publication from the moment of cutover");

        // And the mapping really is the window, not the interval.
        assertEquals(second.getCutoffStampedAt(), current.get(0).publishDateFrom(),
                "publishDateFrom must be the stamped cut-off, not the interval start");
        assertFalse(second.getIntervalFrom().equals(current.get(0).publishDateFrom()),
                "publishDateFrom equals intervalFrom, which is exactly the wrong mapping");
    }

    /** The minus one millisecond: no instant has two current issues. */
    @Test
    @Transactional
    public void noInstantHasTwoCurrentIssues() {
        PublicationSeries s = series(PublicAuthority.NEW);
        long week = 7L * 24 * 3600_000L;
        Date firstStamp = new Date(1_700_000_000_000L);

        publishAt(s, new Date(firstStamp.getTime() - week), firstStamp);
        PublicationIssue second = publishAt(s, firstStamp, new Date(firstStamp.getTime() + week));
        em.flush();
        em.clear();

        // Exactly on the boundary -- where a closed-at-both-ends overlap would
        // return both.
        Date boundary = second.getCutoffStampedAt();
        long currentForThisSeries = adapter.list(boundary, boundary).stream()
                .filter(pp -> s.getSeriesId().equals(pp.seriesId())).count();
        assertEquals(1, currentForThisSeries,
                "two issues of one series were publicly current at the same instant; the cap needs the "
                        + "minus one millisecond because the legacy overlap helper is closed at both ends");
    }

    // ============================================== the eligibility predicate

    /** A retired ISSUE leaves the listing; a retired SERIES keeps serving its issues. */
    @Test
    @Transactional
    public void aRetiredSeriesKeepsServingWhileARetiredIssueDoesNot() {
        long week = 7L * 24 * 3600_000L;
        Date stamp = new Date(1_700_000_000_000L);

        // A retired SERIES. Its published issues must stay visible, or every
        // citation into that back catalogue goes dark.
        PublicationSeries retiredSeries = series(PublicAuthority.NEW);
        PublicationIssue stillVisible = publishAt(retiredSeries, new Date(stamp.getTime() - week), stamp);
        retiredSeries.setStatus(SeriesStatus.RETIRED);
        em.merge(retiredSeries);
        em.flush();

        Date now = new Date(stamp.getTime() + 3600_000L);
        assertTrue(adapter.list(now, now).stream()
                        .anyMatch(p -> p.publicationId().equals(stillVisible.getPublicId())),
                "a retired SERIES stopped serving its published issues; every citation into its back "
                        + "catalogue would go dark");

        // A retired ISSUE, on the other hand, is withdrawn from the listing.
        PublicationSeries liveSeries = series(PublicAuthority.NEW);
        PublicationIssue withdrawn = publishAt(liveSeries, new Date(stamp.getTime() - week), stamp);
        lifecycle.retire(withdrawn, null, "withdrawn");
        em.flush();

        assertFalse(adapter.list(now, now).stream()
                        .anyMatch(p -> p.publicationId().equals(withdrawn.getPublicId())),
                "a retired ISSUE is still being listed");
    }

    // ==================================================== the transition union

    /** A series still on LEGACY is served from the legacy table, not the new one. */
    @Test
    @Transactional
    public void aSeriesBeforeCutoverIsNotServedFromTheNewModel() {
        PublicationSeries notCutOver = series(PublicAuthority.LEGACY);
        Date stamp = new Date(1_700_000_000_000L);
        PublicationIssue issue = publishAt(notCutOver, new Date(stamp.getTime() - 86_400_000L), stamp);
        em.flush();

        Date now = new Date(stamp.getTime() + 3600_000L);
        assertFalse(adapter.list(now, now).stream()
                        .anyMatch(p -> p.publicationId().equals(issue.getPublicId())),
                "an issue of a series that has not cut over was served from the new model; the flag flip "
                        + "is what makes rollback a no-data-change operation");
    }

    /**
     * The union does not emit the same publication twice.
     *
     * An imported issue REUSES the legacy id as its publicId, so a naive union
     * shows two identical rows on the public site.
     */
    @Test
    @Transactional
    public void anImportedIssueDoesNotAppearTwice() {
        Date stamp = new Date(1_700_000_000_000L);
        Date now = new Date(stamp.getTime() + 3600_000L);

        // A legacy row, and a cut-over issue that took it over.
        Publication legacy = new Publication();
        String sharedId = UUID.randomUUID().toString();
        legacy.setPublicationId(sharedId);
        legacy.setPublishDateFrom(stamp);
        legacy.setPublishDateTo(new Date(stamp.getTime() + 7 * 24 * 3600_000L));
        em.persist(legacy);

        PublicationSeries cutOver = series(PublicAuthority.NEW);
        PublicationIssue imported = lifecycle.create(cutOver,
                new Date(stamp.getTime() - 86_400_000L), IntervalBoundSource.RECOVERED, null);
        imported.setLegacyPublicationId(sharedId);
        em.flush();
        publishService.publish(imported.getId(),
                new IssuePublishService.PublishRequest(false, Set.of(), null, stamp));
        em.flush();
        em.clear();

        List<PublicationPublicAdapter.PublicPublication> listed = adapter.list(now, now);
        long occurrences = listed.stream().filter(p -> p.publicationId().equals(sharedId)).count();

        assertTrue(occurrences <= 1,
                "the legacy row and its imported issue were both emitted; the site would show two "
                        + "identical rows");

        long distinct = listed.stream().map(PublicationPublicAdapter.PublicPublication::publicationId)
                .distinct().count();
        assertEquals(listed.size(), distinct, "the public list contains duplicate ids");
    }

    /** The emitted order is total, so two calls cannot disagree. */
    @Test
    @Transactional
    public void theEmittedOrderIsTotal() {
        Date stamp = new Date(1_700_000_000_000L);

        // Twins: two series publishing at the very same instant, as EfS and P&T do.
        PublicationSeries a = series(PublicAuthority.NEW);
        PublicationSeries b = series(PublicAuthority.NEW);
        publishAt(a, new Date(stamp.getTime() - 86_400_000L), stamp);
        publishAt(b, new Date(stamp.getTime() - 86_400_000L), stamp);
        em.flush();
        em.clear();

        Date now = new Date(stamp.getTime() + 3600_000L);
        List<String> first = adapter.list(now, now).stream()
                .map(PublicationPublicAdapter.PublicPublication::publicationId).toList();
        List<String> second = adapter.list(now, now).stream()
                .map(PublicationPublicAdapter.PublicPublication::publicationId).toList();

        assertEquals(first, second,
                "two identical calls returned different orders; the twins share a window to the "
                        + "millisecond, so without the id tiebreak the order is whatever the database chose");
    }

    // ==================================================== citation resolution

    /** A citation resolves to the new issue first, then to a legacy row. */
    @Test
    @Transactional
    public void aCitationResolvesTheNewIssueBeforeTheLegacyRow() {
        Date stamp = new Date(1_700_000_000_000L);
        PublicationSeries s = series(PublicAuthority.NEW);
        PublicationIssue issue = publishAt(s, new Date(stamp.getTime() - 86_400_000L), stamp);
        em.flush();

        PublicationPublicAdapter.PublicPublication resolved = adapter.resolve(issue.getPublicId());
        assertNotNull(resolved, "a published issue did not resolve");
        assertEquals("NEW", resolved.source());

        assertNull(adapter.resolve("no-such-publication-id"),
                "an unknown id resolved to something");
    }
}
