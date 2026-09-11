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
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.criteria.MessageSeriesCriterionVo;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.vo.MessagePublication;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A publication whose issues OVERLAP still comes out on a schedule.
 *
 * An IN_FORCE_AT_CUTOFF issue says what stood at one instant, so it carries one
 * bound and its periods do not tile. That was read for a long time as "there is
 * no next period", and two consequences followed, both of them wrong and both of
 * them visible in the estate:
 *
 * The series opened nothing when it published. The largest in-force weekly in
 * the estate has 503 issues and ten years of unbroken history, and after a
 * release it held no open issue, no forecast row and no automation that would
 * make one -- so a person had to notice, every week, that next week's issue did
 * not exist yet.
 *
 * And it could never say it covered two weeks. A double week is one whose period
 * swallowed a week no issue closed, and the signal for that was read off the
 * issue's own lower bound -- which an in-force issue does not have. Every such
 * issue was numbered for the week it closed in while its title, typed by hand
 * each time it happened, said two.
 *
 * Both are one fact: the period an in-force issue covers runs from wherever the
 * previous issue closed, and that instant was computed and thrown away.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class InForceCadenceTest {

    private static final ZoneId CPH = ZoneId.of("Europe/Copenhagen");
    private static final long WEEK = 7 * 24 * 3600_000L;

    @org.junit.jupiter.api.AfterEach
    public void restoreTheRenderer() {
        StubIssueRenderService.reset();
    }

    @Inject
    IssuePublishService publishService;

    @Inject
    EntityManager em;

    // ------------------------------------------------------------------ fixtures

    /** Noon Copenhagen on a given day -- the shape the weekly cut-off has. */
    private static Date noon(int year, int month, int day) {
        return Date.from(ZonedDateTime.of(year, month, day, 12, 0, 0, 0, CPH).toInstant());
    }

    /** A weekly publication whose issues overlap rather than tile. */
    private PublicationSeries inForceWeekly(NextIssueCreation next, SeriesStatus status) {
        return series(SeriesCadence.WEEKLY, TimeRelation.IN_FORCE_AT_CUTOFF, next, status);
    }

    private PublicationSeries series(SeriesCadence cadence, TimeRelation relation,
                                     NextIssueCreation next, SeriesStatus status) {
        PublicationCategory c = new PublicationCategory();
        c.setCategoryId(TestIds.category());
        c.setPriority(100);
        c.setPublish(true);
        em.persist(c);

        PublicationSeries s = new PublicationSeries();
        s.setSeriesId(TestIds.series());
        s.setStatus(status);
        s.setContentMode(ContentMode.GENERATED_FROM_QUERY);
        s.setReportId("some-report");
        s.setCadence(cadence);
        s.setTimeRelation(relation);
        s.setAliveAtCutoff(relation == TimeRelation.IN_FORCE_AT_CUTOFF);
        s.setReleaseMode(ReleaseMode.MANUAL_GATE);
        s.setNextIssueCreation(next);
        s.setPublicAuthority(PublicAuthority.LEGACY);
        s.setMessagePublication(MessagePublication.NONE);
        s.setNumberingScheme(NumberingScheme.ISO_WEEK_YEAR);
        s.setCategory(c);
        s.setDomain(TestOwnerDomain.of(em));
        s.getLanguages().add("da");

        IssueCriteriaVo doc = new IssueCriteriaVo();
        MessageSeriesCriterionVo node = new MessageSeriesCriterionVo();
        node.setValues(new ArrayList<>(List.of("dma-nm")));
        doc.getCriteria().add(node);
        s.setCriteria(doc);

        PublicationSeriesDesc d = s.createDesc("da");
        d.setName("Test series");
        em.persist(s);
        return s;
    }

    /** An issue of an in-force series: ONE bound, and it is not written yet. */
    private PublicationIssue openIssue(PublicationSeries s) {
        PublicationIssue i = new PublicationIssue();
        i.setSeries(s);
        i.setPublicId(UUID.randomUUID().toString());
        i.setRepoPath("publications/" + i.getPublicId());
        i.setStatus(IssueStatus.OPEN);
        PublicationIssueDesc d = i.createDesc("da");
        d.setName("Test issue");
        em.persist(i);
        return i;
    }

    /** A released neighbour, planted directly so a period has something to run from. */
    private PublicationIssue publishedAt(PublicationSeries s, Date cutoff) {
        PublicationIssue i = openIssue(s);
        i.setStatus(IssueStatus.PUBLISHED);
        i.setCutoffStampedAt(cutoff);
        i.setPublishedAt(cutoff);
        i.setPublicFrom(cutoff);
        em.merge(i);
        return i;
    }

    private IssuePublishService.PublishResult publish(PublicationIssue issue, Date stamp) {
        return publishService.publish(issue.getId(),
                new IssuePublishService.PublishRequest(
                        IssuePublishService.PublishRequest.ALL_WARNINGS, null, stamp));
    }

    // ------------------------------------------------- the successor that was missing

    /**
     * The successor of an in-force issue: no start, and a close one period on.
     *
     * NO START is the whole point and not an omission -- a lower bound would make
     * the resolver ask for messages published in a window this publication does
     * not have, which is why the create path refuses one. The close is derived
     * from the cut-off just stamped, so the chain hangs off a recorded instant
     * exactly as a tiling series' does.
     */
    @Test
    @Transactional
    public void aninForceSeriesOpensItsNextIssueWhenOnePublishes() {
        Date stamp = noon(2026, 1, 14);
        PublicationSeries s = inForceWeekly(NextIssueCreation.AUTO_ON_PUBLISH, SeriesStatus.ACTIVE);
        PublicationIssue i = openIssue(s);
        em.flush();

        var result = publish(i, stamp);
        assertNotNull(result.successorId(),
                "a weekly in-force series published and opened nothing; every next issue of it would "
                        + "have to be created by hand, forever");

        em.flush();
        em.clear();
        PublicationIssue successor = em.find(PublicationIssue.class, result.successorId());

        assertEquals(IssueStatus.OPEN, successor.getStatus());
        assertNull(successor.getIntervalFrom(),
                "an in-force issue has ONE bound; a lower bound would ask for messages published in a "
                        + "window this publication does not have");
        assertNull(successor.getIntervalFromSource(),
                "a bound source beside a null bound describes a decision about nothing");
        assertEquals(new Date(stamp.getTime() + WEEK), successor.getIntervalTo(),
                "the successor does not close one cadence period after the cut-off just stamped");
        assertEquals(IntervalBoundSource.NOMINAL, successor.getIntervalToSource());
        assertEquals(successor.getIntervalTo(), successor.effectiveCutoff(),
                "with no stamp the nominal close IS the effective cut-off, and without one the issue "
                        + "sorts below every dated row in its own series");

        assertEquals(s.getLanguages().size(), successor.getDescs().size());
        for (PublicationIssueDesc d : successor.getDescs()) {
            assertNotNull(d.getName(), "no name for " + d.getLang());
            assertFalse(d.getName().isBlank(), "a blank name for " + d.getLang());
        }
    }

    /**
     * The guard against minting a second one is untouched.
     *
     * Publishing a recovered issue while the real current one is still open would
     * otherwise put a second OPEN issue beside it, and the editor's publication
     * panel then reports every message published since as a live member of a
     * period that closed long ago.
     */
    @Test
    @Transactional
    public void nothingIsOpenedWhileAnotherIssueIsStillOpen() {
        Date stamp = noon(2026, 1, 14);
        PublicationSeries s = inForceWeekly(NextIssueCreation.AUTO_ON_PUBLISH, SeriesStatus.ACTIVE);
        PublicationIssue recovered = openIssue(s);
        openIssue(s);
        em.flush();

        assertNull(publish(recovered, stamp).successorId(),
                "a successor was minted beside an issue that is still open");
    }

    /** And the series has to be one that is still running. */
    @Test
    @Transactional
    public void aretiredInForceSeriesOpensNothing() {
        Date stamp = noon(2026, 1, 14);
        PublicationSeries s = inForceWeekly(NextIssueCreation.AUTO_ON_PUBLISH, SeriesStatus.RETIRED);
        PublicationIssue i = openIssue(s);
        em.flush();

        assertNull(publish(i, stamp).successorId());
    }

    /**
     * A YEARLY in-force series is scheduled too, one year on.
     *
     * The four-clause gate refused this one as well, and an annual edition is the
     * clearest case that "does not tile" was never an answer about whether
     * anything was due: the next edition of a yearly list is as certain as next
     * week's notices.
     */
    @Test
    @Transactional
    public void anannualInForceSeriesOpensItsNextEdition() {
        Date stamp = noon(2026, 1, 14);
        PublicationSeries s = series(SeriesCadence.YEARLY, TimeRelation.IN_FORCE_AT_CUTOFF,
                NextIssueCreation.AUTO_ON_PUBLISH, SeriesStatus.ACTIVE);
        PublicationIssue i = openIssue(s);
        em.flush();

        var result = publish(i, stamp);
        assertNotNull(result.successorId());

        em.flush();
        em.clear();
        PublicationIssue successor = em.find(PublicationIssue.class, result.successorId());
        assertNull(successor.getIntervalFrom());
        assertEquals(noon(2027, 1, 14), successor.getIntervalTo(),
                "an annual edition's successor closes a year on, not a week on");
    }

    /**
     * The close is one period after the STAMP, never after whatever row happens
     * to sort newest.
     *
     * A tiling successor pins itself by writing the stamp as its lower bound. An
     * in-force one has no lower bound to write, so if the close were left to be
     * derived it would be measured from the newest sibling with a date -- and a
     * row that was opened for a later period and then withdrawn carries no stamp
     * and a nominal close that sits after this one. It is neither open nor
     * stamped ahead, so the guard above lets the publish through, and the
     * successor would then close a month out on a weekly publication.
     */
    @Test
    @Transactional
    public void theSuccessorIsMeasuredFromTheCutOffJustStamped() {
        Date stamp = noon(2026, 1, 14);
        PublicationSeries s = inForceWeekly(NextIssueCreation.AUTO_ON_PUBLISH, SeriesStatus.ACTIVE);
        PublicationIssue i = openIssue(s);

        PublicationIssue withdrawn = openIssue(s);
        withdrawn.setStatus(IssueStatus.RETIRED);
        withdrawn.setIntervalTo(new Date(stamp.getTime() + 3 * WEEK));
        withdrawn.setIntervalToSource(IntervalBoundSource.NOMINAL);
        em.merge(withdrawn);
        em.flush();

        var result = publish(i, stamp);
        assertNotNull(result.successorId(),
                "a withdrawn row is neither an open issue nor a stamp ahead of this one");

        em.flush();
        em.clear();
        PublicationIssue successor = em.find(PublicationIssue.class, result.successorId());
        assertEquals(new Date(stamp.getTime() + WEEK), successor.getIntervalTo(),
                "the successor closes one period after a row nothing released, instead of one "
                        + "period after the cut-off this release recorded");
    }

    /**
     * A cadenced publication that is a LINK rather than a document is scheduled
     * too.
     *
     * Content mode and cadence are separate questions: a list published every
     * week off-site comes out every week. Such a series names no time relation at
     * all -- there is no query for a period to narrow -- so it takes the same
     * shape an in-force successor does, which is one bound and no window.
     */
    @Test
    @Transactional
    public void alinkBackedWeeklyOpensItsNextIssueToo() {
        Date stamp = noon(2026, 1, 14);
        PublicationSeries s = series(SeriesCadence.WEEKLY, null,
                NextIssueCreation.AUTO_ON_PUBLISH, SeriesStatus.ACTIVE);
        s.setContentMode(ContentMode.EXTERNAL_LINK);
        s.setReportId(null);
        em.merge(s);

        PublicationIssue i = openIssue(s);
        for (PublicationIssueDesc d : i.getDescs()) {
            d.setLink("https://example.org/weekly-list.pdf");
        }
        em.merge(i);
        em.flush();

        var result = publish(i, stamp);
        assertNotNull(result.successorId(),
                "a weekly publication opens next week's issue whether it renders a document or "
                        + "points at one");

        em.flush();
        em.clear();
        PublicationIssue successor = em.find(PublicationIssue.class, result.successorId());
        assertNull(successor.getIntervalFrom(),
                "a series that selects nothing by query has no window to open");
        assertEquals(new Date(stamp.getTime() + WEEK), successor.getIntervalTo());
        assertEquals(IntervalBoundSource.NOMINAL, successor.getIntervalToSource());
    }

    // ------------------------------------------------------- the double week

    /**
     * An in-force issue that covered a week nobody published carries both weeks.
     *
     * The period it covers runs from where the previous issue closed, and here
     * that is a fortnight back: week 2 went out and week 3 did not, so the issue
     * released in week 4 is the issue for weeks 3 and 4. This is the number the
     * archive already carries in its titles -- "Aktive P&T uge 15-16 - 2019" and
     * its siblings -- typed by hand every single time, because the columns beside
     * them said one week.
     */
    @Test
    @Transactional
    public void anissueCoveringTwoPeriodsCarriesBothWeeks() {
        PublicationSeries s = inForceWeekly(NextIssueCreation.MANUAL, SeriesStatus.ACTIVE);
        publishedAt(s, noon(2025, 12, 31));
        PublicationIssue late = openIssue(s);
        em.flush();

        publish(late, noon(2026, 1, 14));

        em.flush();
        em.clear();
        PublicationIssue published = em.find(PublicationIssue.class, late.getId());
        assertEquals(2, published.getWeek(),
                "the first week this issue closed opens the range");
        assertEquals(3, published.getWeekTo(),
                "an in-force issue that swallowed a whole period says so; it could not before, "
                        + "because the span was read off a bound this kind of issue never has");
        assertEquals(2026, published.getYear());
    }

    /**
     * And it PRINTS both weeks, with nothing typed anywhere for it to do so.
     *
     * The whole point of deriving the second week: ${weekTo} expands to it, so
     * the title comes out "Uge 2+3, 2026" off the arithmetic alone. An edition
     * whose columns said two weeks while its cover said one is what forced the
     * pair to be typed by hand every time a week was skipped, in every language.
     */
    @Test
    @Transactional
    public void thedoubleWeekIsPrintedFromTheNumbersAlone() {
        PublicationSeries s = inForceWeekly(NextIssueCreation.MANUAL, SeriesStatus.ACTIVE);
        s.getDescs().get(0).setNameSuggestionPattern("Uge ${week}+${weekTo}, ${year}");
        em.merge(s);
        publishedAt(s, noon(2025, 12, 31));
        PublicationIssue late = openIssue(s);
        em.flush();

        publish(late, noon(2026, 1, 14));

        em.flush();
        em.clear();
        PublicationIssue published = em.find(PublicationIssue.class, late.getId());

        assertEquals("Uge 2+3, 2026", published.getDescs().get(0).getName(),
                "the second week did not reach the title, so the edition is called one week while "
                        + "the columns beside it say two");
        assertEquals("2", PrintedNumbering.printedWeek(published));
        assertEquals("3", PrintedNumbering.printedWeekTo(published),
                "the closing week is the derived number and prints as itself");
        assertFalse(PrintedNumbering.isOverridden(published),
                "the pair came out of the arithmetic; nothing was written down to produce it");
    }

    /** The ordinary week is still one week, and is not a range. */
    @Test
    @Transactional
    public void anordinaryWeekIsNotADoubleWeek() {
        PublicationSeries s = inForceWeekly(NextIssueCreation.MANUAL, SeriesStatus.ACTIVE);
        publishedAt(s, noon(2026, 1, 7));
        PublicationIssue next = openIssue(s);
        em.flush();

        publish(next, noon(2026, 1, 14));

        em.flush();
        em.clear();
        PublicationIssue published = em.find(PublicationIssue.class, next.getId());
        assertEquals(3, published.getWeek(), "an issue is named for the week it closed in");
        assertNull(published.getWeekTo(),
                "one period is not a range, however many ISO weeks the days between two cut-offs touch");
    }

    /**
     * The FIRST issue of an in-force series has no predecessor and no range.
     *
     * There is nothing to measure a span against, and inventing one from the
     * series' declared start would name the first edition for every week since.
     */
    @Test
    @Transactional
    public void thefirstIssueOfASeriesIsASingleWeek() {
        PublicationSeries s = inForceWeekly(NextIssueCreation.MANUAL, SeriesStatus.ACTIVE);
        PublicationIssue first = openIssue(s);
        em.flush();

        publish(first, noon(2026, 1, 14));

        em.flush();
        em.clear();
        PublicationIssue published = em.find(PublicationIssue.class, first.getId());
        assertEquals(3, published.getWeek());
        assertNull(published.getWeekTo());
    }

    /**
     * A YEARLY in-force series is never a double WEEK.
     *
     * The multi-period test downstream counts weeks, so handing it a predecessor
     * a year back would report every ordinary annual edition as spanning
     * fifty-two periods and renumber it for the week before the one it closes in
     * -- in its title, its file name and therefore the link it is cited by.
     */
    @Test
    @Transactional
    public void anannualEditionIsNotRenumberedByTheWeeksSinceTheLastOne() {
        PublicationSeries s = series(SeriesCadence.YEARLY, TimeRelation.IN_FORCE_AT_CUTOFF,
                NextIssueCreation.MANUAL, SeriesStatus.ACTIVE);
        publishedAt(s, noon(2025, 1, 14));
        PublicationIssue edition = openIssue(s);
        em.flush();

        publish(edition, noon(2026, 1, 14));

        em.flush();
        em.clear();
        PublicationIssue published = em.find(PublicationIssue.class, edition.getId());
        assertEquals(3, published.getWeek(), "the edition is numbered by the week it closes in");
        assertNull(published.getWeekTo(),
                "a year is one period of a yearly publication, and it is not fifty-two weeks of one");
    }
}
