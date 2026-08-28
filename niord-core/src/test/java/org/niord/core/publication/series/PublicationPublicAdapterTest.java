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
import org.niord.core.publication.Publication;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.core.publication.vo.PublicationMainType;
import org.niord.core.publication.vo.PublicationStatus;

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

    // ============================================ the null-parameter semantics

    /**
     * A null from or to means NO bound on that side -- it does not mean now.
     *
     * CriteriaHelper.overlaps emits one predicate per non-null parameter, so
     * ?from=<t> alone has no upper bound at all. Defaulting the missing side to
     * "now" reads like a harmless convenience and is not one: it hides every
     * publication whose window has not opened yet from a caller who asked only
     * for a lower bound.
     */
    @Test
    @Transactional
    public void aMissingBoundIsNoBoundRatherThanNow() {
        Date now = new Date(1_700_000_000_000L);
        Date nextYear = new Date(now.getTime() + 365L * 24 * 3600_000L);

        Publication future = publishingLegacy(UUID.randomUUID().toString(), nextYear);
        em.persist(future);
        Publication past = publishingLegacy(UUID.randomUUID().toString(),
                new Date(now.getTime() - 365L * 24 * 3600_000L));
        past.setPublishDateTo(new Date(now.getTime() - 300L * 24 * 3600_000L));
        em.persist(past);
        em.flush();
        em.clear();

        List<String> lowerBoundOnly = adapter.list(now, null).stream()
                .map(PublicationPublicAdapter.PublicPublication::publicationId).toList();
        assertTrue(lowerBoundOnly.contains(future.getPublicationId()),
                "?from= alone acquired an upper bound of now, so a publication whose window opens "
                        + "later vanished");

        List<String> upperBoundOnly = adapter.list(null, now).stream()
                .map(PublicationPublicAdapter.PublicPublication::publicationId).toList();
        assertTrue(upperBoundOnly.contains(past.getPublicationId()),
                "?to= alone acquired a lower bound of now, so an already-expired publication vanished");
    }

    // ============================================ the cutover window

    /**
     * An imported issue sitting at OPEN does not remove its legacy row from the list.
     *
     * newHalf only emits PUBLISHED issues. If the exclusion subquery is ungated,
     * the legacy row is taken out while nothing is put in its place, and the
     * publication simply disappears from the public site for the whole window
     * between import and first publish.
     */
    @Test
    @Transactional
    public void anUnpublishedImportedIssueDoesNotHideItsLegacyRow() {
        Date stamp = new Date(1_700_000_000_000L);
        Date now = new Date(stamp.getTime() + 3600_000L);

        Publication legacy = publishingLegacy(UUID.randomUUID().toString(), stamp);
        String sharedId = legacy.getPublicationId();
        em.persist(legacy);

        PublicationSeries cutOver = series(PublicAuthority.NEW);
        PublicationIssue imported = lifecycle.create(cutOver,
                new Date(stamp.getTime() - 86_400_000L), IntervalBoundSource.RECOVERED, null);
        imported.setLegacyPublicationId(sharedId);
        em.flush();
        em.clear();

        List<String> ids = adapter.list(now, now).stream()
                .map(PublicationPublicAdapter.PublicPublication::publicationId).toList();

        assertTrue(ids.contains(sharedId),
                "the legacy row was excluded by an issue that has not been published, so the "
                        + "publication is on neither half of the union and is simply gone");
    }

    // ============================================ the value-object list

    /** listVo is what serves the endpoint, so it carries the language and the mapping. */
    @Test
    @Transactional
    public void theValueObjectListCarriesTheLanguageAndTheWindow() {
        Date stamp = new Date(1_700_000_000_000L);
        Date now = new Date(stamp.getTime() + 3600_000L);

        PublicationSeries s = series(PublicAuthority.NEW);
        PublicationIssue issue = publishAt(s, new Date(stamp.getTime() - 86_400_000L), stamp);
        em.flush();
        em.clear();

        var vo = adapter.listVo(now, now, "da").stream()
                .filter(v -> v.getPublicationId().equals(issue.getPublicId()))
                .findFirst().orElse(null);

        assertNotNull(vo, "the published issue is missing from the value-object list");
        assertEquals(1, vo.getDescs().size(), "the language filter did not reach the descs");
        assertEquals("da", vo.getDescs().get(0).getLang());
        assertEquals(issue.getCutoffStampedAt(), vo.getPublishDateFrom(),
                "publishDateFrom must be the stamped cut-off, not the interval start");
    }

    /**
     * Retiring an IMPORTED issue does not resurrect the legacy row it replaced.
     *
     * The exclusion used to key on status = PUBLISHED. newHalf also emits only
     * PUBLISHED issues, so retiring an imported issue dropped it from the new half
     * AND lapsed the exclusion -- and the ACTIVE legacy row returned to the public
     * list with its original, uncapped window. No duplicate id, no ERROR log, and
     * the existing retired-issue test uses fixtures with no legacyPublicationId,
     * so it passed throughout.
     *
     * Retiring withdraws a document. It must not republish the one it replaced.
     */
    @Test
    @Transactional
    public void retiringAnImportedIssueDoesNotResurrectItsLegacyRow() {
        Date stamp = new Date(1_700_000_000_000L);
        Date now = new Date(stamp.getTime() + 3600_000L);

        Publication legacy = publishingLegacy(UUID.randomUUID().toString(), stamp);
        String sharedId = legacy.getPublicationId();
        em.persist(legacy);

        PublicationSeries cutOver = series(PublicAuthority.NEW);
        PublicationIssue imported = lifecycle.create(cutOver,
                new Date(stamp.getTime() - 86_400_000L), IntervalBoundSource.RECOVERED, null);
        imported.setLegacyPublicationId(sharedId);
        em.flush();
        previewFor(imported);
        publishService.publish(imported.getId(),
                new IssuePublishService.PublishRequest(false, Set.of(), null, stamp));
        em.flush();

        assertFalse(adapter.list(now, now).stream()
                        .anyMatch(p -> p.publicationId().equals(sharedId) && "LEGACY".equals(p.source())),
                "the legacy row is served while its imported issue is PUBLISHED");

        lifecycle.retire(em.find(PublicationIssue.class, imported.getId()), null, "withdrawn");
        em.flush();
        em.clear();

        assertFalse(adapter.list(now, now).stream()
                        .anyMatch(p -> p.publicationId().equals(sharedId)),
                "retiring the imported issue brought its legacy row back to the public list, "
                        + "carrying the window the issue had replaced");
    }

    /**
     * A cut-over series in a non-publishing category stays off the public list.
     *
     * legacyHalf has always applied the category publish flag. Omitting it from
     * newHalf means flipping publicAuthority publishes issues whose legacy rows
     * were correctly hidden -- the flip itself becomes a disclosure.
     */
    @Test
    @Transactional
    public void aCutOverSeriesInANonPublishingCategoryIsNotServed() {
        Date stamp = new Date(1_700_000_000_000L);
        Date now = new Date(stamp.getTime() + 3600_000L);

        PublicationSeries internal = series(PublicAuthority.NEW);
        internal.getCategory().setPublish(false);
        em.flush();

        PublicationIssue issue = publishAt(internal, new Date(stamp.getTime() - 86_400_000L), stamp);
        em.flush();
        em.clear();

        assertFalse(adapter.list(now, now).stream()
                        .anyMatch(p -> p.publicationId().equals(issue.getPublicId())),
                "an issue of a series in a non-publishing category reached the public list");
    }

    // ------------------------------------------------------------------ fixtures

    private PublicationSeries series(PublicAuthority authority) {
        PublicationCategory c = new PublicationCategory();
        c.setCategoryId("cat-" + UUID.randomUUID().toString().substring(0, 8));
        // publish defaults to FALSE. Until newHalf read the flag this fixture was
        // silently modelling an INTERNAL category, and every window and ordering
        // test above was asserting against a series that should never have been
        // public at all. aCutOverSeriesInANonPublishingCategoryIsNotServed covers
        // the false case deliberately.
        c.setPublish(true);
        c.setPriority(100);
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
        s.setPublicAuthority(authority);
        s.setMessagePublication(MessagePublication.NONE);
        s.setNumberingScheme(NumberingScheme.ISO_WEEK_YEAR);
        s.setCategory(c);
        s.getLanguages().add("da");
        s.createDesc("da").setName("Test series");
        em.persist(s);
        return s;
    }

    /** A legacy publication the public list would actually serve. */
    private Publication publishingLegacy(String publicationId, Date from) {
        PublicationCategory c = new PublicationCategory();
        c.setCategoryId("legacy-" + UUID.randomUUID().toString().substring(0, 8));
        c.setPublish(true);
        c.setPriority(50);
        em.persist(c);

        Publication legacy = new Publication();
        legacy.setPublicationId(publicationId);
        legacy.setStatus(PublicationStatus.ACTIVE);
        legacy.setMainType(PublicationMainType.PUBLICATION);
        legacy.setCategory(c);
        legacy.setPublishDateFrom(from);
        legacy.setPublishDateTo(new Date(from.getTime() + 7 * 24 * 3600_000L));
        return legacy;
    }

    private PublicationIssue publishAt(PublicationSeries s, Date intervalFrom, Date stamp) {
        PublicationIssue i = lifecycle.create(s, intervalFrom, IntervalBoundSource.STAMPED, null);
        em.flush();
        previewFor(i);
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
    @BindsRule({"I-18"})
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
        //
        // ACTIVE, PUBLICATION and a publishing category on purpose: the legacy
        // half applies the filter the public list has always applied, so a DRAFT
        // fixture would be excluded for the wrong reason and this test would pass
        // without exercising the exclusion subquery at all.
        String sharedId = UUID.randomUUID().toString();
        em.persist(publishingLegacy(sharedId, stamp));

        PublicationSeries cutOver = series(PublicAuthority.NEW);
        PublicationIssue imported = lifecycle.create(cutOver,
                new Date(stamp.getTime() - 86_400_000L), IntervalBoundSource.RECOVERED, null);
        imported.setLegacyPublicationId(sharedId);
        em.flush();
        previewFor(imported);
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
    @BindsRule({"X-4"})
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

    // ==================================================== the emitted order

    /**
     * CATEGORY PRIORITY orders the list, before the window.
     *
     * The legacy public list has always ordered by category priority ascending
     * and publish date descending, and the sections of the public page are built
     * from that order. Sorting by date alone reshuffles every section -- a
     * regression that looks like nothing at all in a diff.
     */
    @Test
    @Transactional
    public void categoryPriorityOrdersBeforeTheWindow() {
        Date stamp = new Date(1_700_000_000_000L);
        Date now = new Date(stamp.getTime() + 3600_000L);
        long week = 7L * 24 * 3600_000L;

        // The OLDER issue is in the higher-priority category, so date ordering
        // alone would put it second.
        PublicationSeries high = series(PublicAuthority.NEW);
        high.getCategory().setPriority(1);
        PublicationSeries low = series(PublicAuthority.NEW);
        low.getCategory().setPriority(99);
        em.flush();

        PublicationIssue older = publishAt(high, new Date(stamp.getTime() - 2 * week),
                new Date(stamp.getTime() - week));
        PublicationIssue newer = publishAt(low, new Date(stamp.getTime() - week), stamp);
        em.flush();
        em.clear();

        List<String> ids = adapter.list(now, now).stream()
                .map(PublicationPublicAdapter.PublicPublication::publicationId)
                .filter(id -> id.equals(older.getPublicId()) || id.equals(newer.getPublicId()))
                .toList();

        assertEquals(List.of(older.getPublicId(), newer.getPublicId()), ids,
                "the list is not ordered by category priority first; the public page builds its "
                        + "sections from that order");
    }

    /**
     * A legacy publication with an open-ended window is still served.
     *
     * The legacy helper treats a null bound as "no bound", so a publication with
     * no publishDateFrom is on the public list today. Requiring the bound here --
     * which reads like tightening a query -- would silently remove live rows from
     * the public site.
     */
    @Test
    @Transactional
    public void aLegacyPublicationWithAnOpenEndedWindowIsStillServed() {
        Date stamp = new Date(1_700_000_000_000L);
        Date now = new Date(stamp.getTime() + 3600_000L);

        Publication noStart = publishingLegacy(UUID.randomUUID().toString(), stamp);
        noStart.setPublishDateFrom(null);
        em.persist(noStart);

        Publication noEnd = publishingLegacy(UUID.randomUUID().toString(), stamp);
        noEnd.setPublishDateTo(null);
        em.persist(noEnd);
        em.flush();
        em.clear();

        List<String> ids = adapter.list(now, now).stream()
                .map(PublicationPublicAdapter.PublicPublication::publicationId).toList();

        assertTrue(ids.contains(noStart.getPublicationId()),
                "a publication with no publishDateFrom vanished from the public list");
        assertTrue(ids.contains(noEnd.getPublicationId()),
                "a publication with no publishDateTo vanished from the public list");
    }

    // ==================================================== the value-object list

    /** listVo emits the same ids, in the same order, as list. */
    @Test
    @Transactional
    public void theValueObjectListMatchesTheRecordList() {
        Date stamp = new Date(1_700_000_000_000L);
        Date now = new Date(stamp.getTime() + 3600_000L);

        PublicationSeries s = series(PublicAuthority.NEW);
        publishAt(s, new Date(stamp.getTime() - 86_400_000L), stamp);
        em.flush();
        em.clear();

        List<String> records = adapter.list(now, now).stream()
                .map(PublicationPublicAdapter.PublicPublication::publicationId).toList();
        List<String> vos = adapter.listVo(now, now, "da").stream()
                .map(org.niord.model.publication.PublicationVo::getPublicationId).toList();

        assertEquals(records, vos,
                "the record list and the value-object list disagree; they are supposed to be one "
                        + "union expressed twice, not two unions");
    }

    /**
     * A DRAFT legacy publication is not on the public list.
     *
     * The adapter now backs /public/v1/publications, so its legacy half has to
     * apply the same status, main-type and category-publish filter the endpoint
     * has always applied. Without it, taking over the endpoint would put every
     * DRAFT publication on the public site.
     */
    @Test
    @Transactional
    public void aDraftLegacyPublicationIsNotServed() {
        Date stamp = new Date(1_700_000_000_000L);
        Date now = new Date(stamp.getTime() + 3600_000L);

        Publication draft = publishingLegacy(UUID.randomUUID().toString(), stamp);
        draft.setStatus(PublicationStatus.DRAFT);
        em.persist(draft);

        Publication internal = publishingLegacy(UUID.randomUUID().toString(), stamp);
        internal.getCategory().setPublish(false);
        em.persist(internal);

        Publication served = publishingLegacy(UUID.randomUUID().toString(), stamp);
        em.persist(served);
        em.flush();
        em.clear();

        List<String> ids = adapter.list(now, now).stream()
                .map(PublicationPublicAdapter.PublicPublication::publicationId).toList();

        assertTrue(ids.contains(served.getPublicationId()), "an ACTIVE publication was not served");
        assertFalse(ids.contains(draft.getPublicationId()),
                "a DRAFT publication reached the public list");
        assertFalse(ids.contains(internal.getPublicationId()),
                "a publication in a non-publishing category reached the public list");
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
