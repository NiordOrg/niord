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
import jakarta.transaction.UserTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.niord.core.area.Area;
import org.niord.core.category.Category;
import org.niord.core.chart.Chart;
import org.niord.core.message.Message;
import org.niord.core.message.MessageSeries;
import org.niord.core.publication.TestIds;
import org.niord.core.publication.series.resolve.Interval;
import org.niord.core.publication.series.resolve.ResolvedCriteria;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.model.message.MainType;
import org.niord.model.message.Status;
import org.niord.model.message.Type;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The four criteria kinds that select on something other than the message row.
 *
 * Each is exercised against a pair of messages built for it: one the criterion
 * must select, one it must not. A test that asserted only the hit would pass on
 * a predicate that had been dropped altogether, which is exactly how these four
 * came to validate without resolving in the first place -- an operand nobody
 * applied looks identical to an operand everybody matches.
 *
 * The area and category pairs are filed under a CHILD node while the criterion
 * names the PARENT, because that is the behaviour the message search has and the
 * one an admin will assume: a criterion naming a sea area is expected to select
 * the messages filed in its sub-areas.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class EntityCriteriaResolutionTest {

    @Inject
    MemberResolutionService resolver;

    @Inject
    EntityManager em;

    @Inject
    UserTransaction tx;

    /** Unique per run, so a shared database cannot make one run depend on another. */
    private String tag;
    private String seriesId;
    private String parentAreaMrn;
    private String otherAreaMrn;
    private String parentCategoryMrn;
    private String otherCategoryMrn;
    private String chartNumber;
    private String otherChartNumber;
    private String matchingUid;
    private String otherUid;

    /** The window every case resolves over: wide enough that only the criteria decide. */
    private final Interval interval = new Interval(new Date(0), new Date());

    // ------------------------------------------------------------------ fixture

    @BeforeEach
    public void seed() throws Exception {
        tx.begin();
        try {
            seedFixture();
            tx.commit();
        } catch (RuntimeException e) {
            tx.rollback();
            throw e;
        }
    }

    private void seedFixture() {
        tag = TestIds.suffix();
        seriesId = "crit-" + tag;

        MessageSeries series = new MessageSeries();
        series.setSeriesId(seriesId);
        series.setMainType(MainType.NM);
        em.persist(series);

        Area parentArea = area("area-parent-" + tag, null);
        Area childArea = area("area-child-" + tag, parentArea);
        Area otherArea = area("area-other-" + tag, null);
        parentAreaMrn = parentArea.getMrn();
        otherAreaMrn = otherArea.getMrn();

        Category parentCategory = category("cat-parent-" + tag, null);
        Category childCategory = category("cat-child-" + tag, parentCategory);
        Category otherCategory = category("cat-other-" + tag, null);
        parentCategoryMrn = parentCategory.getMrn();
        otherCategoryMrn = otherCategory.getMrn();

        Chart chart = chart("CH-" + tag + "-A");
        Chart other = chart("CH-" + tag + "-B");
        chartNumber = chart.getChartNumber();
        otherChartNumber = other.getChartNumber();

        // The message every criterion must select: filed under the CHILD nodes.
        matchingUid = message(series, Type.TEMPORARY_NOTICE, childArea, childCategory, chart);

        // The message every criterion must leave out, and it is a well-formed
        // member of the same series over the same window -- so the only thing that
        // can exclude it is the criterion under test.
        otherUid = message(series, Type.LOCAL_WARNING, otherArea, otherCategory, other);
    }

    private Area area(String name, Area parent) {
        Area a = new Area();
        a.setMrn("urn:mrn:test:area:" + name);
        if (parent != null) {
            parent.addChild(a);
        }
        em.persist(a);
        em.flush();
        a.updateLineage();
        return em.merge(a);
    }

    private Category category(String name, Category parent) {
        Category c = new Category();
        c.setMrn("urn:mrn:test:category:" + name);
        if (parent != null) {
            parent.addChild(c);
        }
        em.persist(c);
        em.flush();
        c.updateLineage();
        return em.merge(c);
    }

    private Chart chart(String number) {
        Chart c = new Chart();
        c.setChartNumber(number);
        em.persist(c);
        em.flush();
        return c;
    }

    private String message(MessageSeries series, Type type, Area area, Category category, Chart chart) {
        Message m = new Message();
        m.setUid(UUID.randomUUID().toString());
        m.setMessageSeries(series);
        m.setType(type);
        m.setStatus(Status.PUBLISHED);
        m.setPublishDateFrom(new Date(interval.cutoff().getTime() - 60_000L));
        m.getAreas().add(area);
        m.getCategories().add(category);
        m.getCharts().add(chart);
        em.persist(m);
        em.flush();
        return m.getUid();
    }

    // ------------------------------------------------------------------ helpers

    private ResolvedCriteria criteria(Set<MainType> mainTypes, Set<String> areaIds,
                                      Set<String> categoryIds, Set<String> chartNumbers) {
        return new ResolvedCriteria(TimeRelation.PUBLISHED_IN_INTERVAL, Set.of(seriesId), Set.of(),
                mainTypes, areaIds, categoryIds, chartNumbers, false);
    }

    private Set<String> membersOf(ResolvedCriteria criteria) {
        return resolver.resolve(criteria, interval).members();
    }

    // ------------------------------------------------------------------ the kinds

    /** A criterion naming a parent area selects the message filed under its child. */
    @Test
    @Transactional
    public void anAreaCriterionSelectsThroughTheHierarchy() {
        assertEquals(Set.of(matchingUid),
                membersOf(criteria(Set.of(), Set.of(parentAreaMrn), Set.of(), Set.of())),
                "an area criterion naming the parent did not select the message filed under its child");
    }

    /** And it leaves out the message filed elsewhere, rather than matching everything. */
    @Test
    @Transactional
    public void anAreaCriterionLeavesOutAMessageInAnotherArea() {
        assertEquals(Set.of(otherUid),
                membersOf(criteria(Set.of(), Set.of(otherAreaMrn), Set.of(), Set.of())));
    }

    @Test
    @Transactional
    public void aCategoryCriterionSelectsThroughTheHierarchy() {
        assertEquals(Set.of(matchingUid),
                membersOf(criteria(Set.of(), Set.of(), Set.of(parentCategoryMrn), Set.of())),
                "a category criterion naming the parent did not select the message filed under its child");
    }

    @Test
    @Transactional
    public void aCategoryCriterionLeavesOutAMessageInAnotherCategory() {
        assertEquals(Set.of(otherUid),
                membersOf(criteria(Set.of(), Set.of(), Set.of(otherCategoryMrn), Set.of())));
    }

    /** Charts match the number itself -- no hierarchy, and no partial matching. */
    @Test
    @Transactional
    public void aChartCriterionSelectsOnlyTheMessageCarryingThatChart() {
        assertEquals(Set.of(matchingUid),
                membersOf(criteria(Set.of(), Set.of(), Set.of(), Set.of(chartNumber))));
        assertEquals(Set.of(otherUid),
                membersOf(criteria(Set.of(), Set.of(), Set.of(), Set.of(otherChartNumber))));
    }

    @Test
    @Transactional
    public void aMainTypeCriterionSplitsTheTwoMessages() {
        assertEquals(Set.of(matchingUid),
                membersOf(criteria(Set.of(MainType.NM), Set.of(), Set.of(), Set.of())));
        assertEquals(Set.of(otherUid),
                membersOf(criteria(Set.of(MainType.NW), Set.of(), Set.of(), Set.of())));
    }

    /** Two kinds at once conjoin: the message has to satisfy both. */
    @Test
    @Transactional
    public void twoEntityCriteriaAreAndedTogether() {
        assertTrue(membersOf(criteria(Set.of(MainType.NM), Set.of(otherAreaMrn), Set.of(), Set.of())).isEmpty(),
                "the NM message is not in the other area, so the conjunction must select nothing");
    }

    // ------------------------------------------------------------------ RI-6

    /**
     * An MRN that names nothing refuses.
     *
     * The alternative -- resolving it to null and filtering it out of the
     * disjunction, which is what the message search does -- turns the criterion
     * into an OR over nothing. The issue publishes empty and reports no problem.
     */
    @Test
    @Transactional
    public void anUnknownAreaMrnRefuses() {
        MemberResolutionService.UnresolvableOperandException e = assertThrows(
                MemberResolutionService.UnresolvableOperandException.class,
                () -> membersOf(criteria(Set.of(), Set.of("urn:mrn:test:area:nothing-" + tag), Set.of(), Set.of())));
        assertTrue(e.getMessage().contains("nothing-" + tag),
                "the refusal does not name the operand that could not be resolved: " + e.getMessage());
    }

    @Test
    @Transactional
    public void anUnknownCategoryMrnRefuses() {
        assertThrows(MemberResolutionService.UnresolvableOperandException.class,
                () -> membersOf(criteria(Set.of(), Set.of(),
                        Set.of("urn:mrn:test:category:nothing-" + tag), Set.of())));
    }

    @Test
    @Transactional
    public void anUnknownChartNumberRefuses() {
        assertThrows(MemberResolutionService.UnresolvableOperandException.class,
                () -> membersOf(criteria(Set.of(), Set.of(), Set.of(), Set.of("CH-nothing-" + tag))));
    }

    /**
     * One resolvable operand does not excuse an unresolvable one beside it.
     *
     * This is the shape that hides the bug: the disjunction still has a term, so
     * the query runs and returns rows, and the only symptom is a member list
     * quietly missing everything the second operand would have added.
     */
    @Test
    @Transactional
    public void oneGoodOperandDoesNotExcuseABadOneBesideIt() {
        assertThrows(MemberResolutionService.UnresolvableOperandException.class,
                () -> membersOf(criteria(Set.of(), Set.of(parentAreaMrn, "urn:mrn:test:area:nothing-" + tag),
                        Set.of(), Set.of())));
    }

    // ------------------------------------------------------- the areas/area trap

    /**
     * Membership reads the area LIST, not the single primary area.
     *
     * A message carries an ordered list of areas and one "area" that exists for
     * sorting. Matching on the sort field would drop every message whose second
     * or third area is the one the criterion names -- and the publish path's
     * ordering query joins that very field, so the two are one careless copy
     * apart.
     */
    @Test
    @Transactional
    public void aSecondaryAreaCountsForMembership() {
        Message m = em.createQuery("SELECT m FROM Message m WHERE m.uid = :uid", Message.class)
                .setParameter("uid", otherUid).getSingleResult();

        Area secondary = em.createQuery("SELECT a FROM Area a WHERE a.mrn = :mrn", Area.class)
                .setParameter("mrn", "urn:mrn:test:area:area-child-" + tag).getSingleResult();

        // Appended, so the primary "area" reference stays what it was.
        m.getAreas().add(secondary);
        em.merge(m);
        em.flush();

        List<Area> areas = m.getAreas();
        assertTrue(areas.size() > 1, "the fixture did not get a second area");

        assertEquals(Set.of(matchingUid, otherUid),
                membersOf(criteria(Set.of(), Set.of(parentAreaMrn), Set.of(), Set.of())),
                "a message whose SECOND area matches the criterion was dropped; membership is reading "
                        + "the primary sort area rather than the area list");
    }
}
