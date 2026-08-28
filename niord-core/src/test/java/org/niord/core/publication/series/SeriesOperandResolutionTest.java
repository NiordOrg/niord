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
import org.niord.core.area.Area;
import org.niord.core.category.Category;
import org.niord.core.chart.Chart;
import org.niord.core.domain.Domain;
import org.niord.core.message.MessageSeries;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.series.criteria.AreaCriterionVo;
import org.niord.core.publication.series.criteria.CategoryCriterionVo;
import org.niord.core.publication.series.criteria.ChartCriterionVo;
import org.niord.core.publication.series.criteria.CriterionKind;
import org.niord.core.publication.series.criteria.DomainCriterionVo;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.criteria.IssueCriterionVo;
import org.niord.core.publication.series.criteria.MessageSeriesCriterionVo;
import org.niord.core.publication.series.criteria.PublicationOperandResolver;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.model.message.MainType;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C-4 on the series save path: an operand that names nothing is refused here,
 * while there is still a form to correct it in.
 *
 * Before this the series form validated its criteria against a resolver that
 * accepted every operand, so a mistyped area MRN or a message-series id that had
 * been renamed was stored without complaint and only discovered at resolve time.
 * For a query-backed series that is INSIDE the publish transaction, after the
 * cut-off has been stamped -- and the two ways out from there are a series that
 * cannot be released or a release with a member list nobody meant.
 *
 * The domain node is the sharpest case and it is the one that was already
 * covered: it is a macro for the message series a domain publishes, so a domain
 * expanding to nothing narrows the query to NOTHING and the issue publishes empty
 * rather than failing. The point of this class is that the other four kinds are
 * now asked the same question.
 */
@QuarkusTest
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class SeriesOperandResolutionTest {

    @Inject
    PublicationOperandResolver operands;

    @Inject
    EntityManager em;

    /** Unique per run, so a shared database cannot make one run depend on another. */
    private final String tag = UUID.randomUUID().toString().substring(0, 8);

    // ------------------------------------------------------------------ fixtures

    private MessageSeries messageSeries() {
        MessageSeries ms = new MessageSeries();
        ms.setSeriesId("ms-" + tag);
        ms.setMainType(MainType.NM);
        em.persist(ms);
        em.flush();
        return ms;
    }

    private Area area() {
        Area a = new Area();
        a.setMrn("urn:mrn:test:area:" + tag);
        em.persist(a);
        em.flush();
        a.updateLineage();
        return em.merge(a);
    }

    private Category category() {
        Category c = new Category();
        c.setMrn("urn:mrn:test:category:" + tag);
        em.persist(c);
        em.flush();
        c.updateLineage();
        return em.merge(c);
    }

    private Chart chart() {
        Chart c = new Chart();
        c.setChartNumber("CH-" + tag);
        em.persist(c);
        em.flush();
        return c;
    }

    private Domain domain(MessageSeries carried) {
        Domain d = new Domain();
        d.setDomainId("dom-" + tag);
        d.setActive(true);
        d.setTimeZone("Europe/Copenhagen");
        if (carried != null) {
            d.getMessageSeries().add(carried);
        }
        em.persist(d);
        em.flush();
        return d;
    }

    /**
     * A query-backed series carrying the given criteria.
     *
     * Everything else is set so the only rule that can fail is the one under
     * test: a fixture that trips S-1 or S-11 as well would pass this test for
     * the wrong reason.
     */
    private PublicationSeries seriesWith(IssueCriteriaVo criteria) {
        PublicationCategory pc = new PublicationCategory();
        pc.setCategoryId("cat-" + UUID.randomUUID().toString().substring(0, 8));
        pc.setPriority(100);
        em.persist(pc);

        PublicationSeries s = new PublicationSeries();
        s.setSeriesId("s-" + UUID.randomUUID().toString().substring(0, 8));
        s.setStatus(SeriesStatus.DRAFT);
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
        s.setCategory(pc);
        s.getLanguages().add("da");
        s.createDesc("da").setName("Test series");
        s.setCriteria(criteria);

        // The rest of the shape, so the ONLY rule that can fail is C-4. A fixture
        // that also trips S-4, S-5, S-9 or S-20 would let this test pass while
        // saying nothing about the operands.
        s.setFirstIssueStartsAt(new Date(1_700_000_000_000L));
        s.setNominalCutoffDay(CutoffDay.WEDNESDAY);
        s.setNominalCutoffTime("12:00");
        s.setCutoffDefault(CutoffDefault.RELEASE_MOMENT);
        s.setPageSize(PageSize.A4);
        s.setPageOrientation(PageOrientation.PORTRAIT);
        s.setMapThumbnails(Boolean.TRUE);
        Domain own = new Domain();
        own.setDomainId("sd-" + UUID.randomUUID().toString().substring(0, 8));
        own.setActive(true);
        own.setTimeZone("Europe/Copenhagen");
        em.persist(own);
        s.setDomain(own);
        return s;
    }

    private static IssueCriteriaVo document(IssueCriterionVo... nodes) {
        IssueCriteriaVo doc = new IssueCriteriaVo();
        for (IssueCriterionVo node : nodes) {
            doc.getCriteria().add(node);
        }
        return doc;
    }

    private static IssueCriterionVo messageSeriesNode(String... values) {
        MessageSeriesCriterionVo node = new MessageSeriesCriterionVo();
        node.setValues(new ArrayList<>(List.of(values)));
        return node;
    }

    // ============================================================ the resolver

    /** Each kind, resolved against the row it names. */
    @Test
    @Transactional
    public void everyKindResolvesAgainstTheRowItNames() {
        MessageSeries ms = messageSeries();
        Area a = area();
        Category c = category();
        Chart ch = chart();
        Domain d = domain(ms);

        assertTrue(operands.exists(CriterionKind.MESSAGE_SERIES, ms.getSeriesId()));
        assertTrue(operands.exists(CriterionKind.AREA, a.getMrn()));
        assertTrue(operands.exists(CriterionKind.CATEGORY, c.getMrn()));
        assertTrue(operands.exists(CriterionKind.CHART, ch.getChartNumber()));
        assertTrue(operands.exists(CriterionKind.DOMAIN, d.getDomainId()));

        assertFalse(operands.exists(CriterionKind.MESSAGE_SERIES, "ms-nothing-" + tag));
        assertFalse(operands.exists(CriterionKind.AREA, "urn:mrn:test:area:nothing-" + tag));
        assertFalse(operands.exists(CriterionKind.CATEGORY, "urn:mrn:test:category:nothing-" + tag));
        assertFalse(operands.exists(CriterionKind.CHART, "CH-nothing-" + tag));
        assertFalse(operands.exists(CriterionKind.DOMAIN, "dom-nothing-" + tag));
    }

    /**
     * A domain that exists but carries no message series still fails.
     *
     * The stricter question, kept: a domain node stands for the series that domain
     * publishes, so one that expands to nothing narrows the query to nothing and
     * the issue publishes EMPTY rather than failing -- the failure that looks like
     * success.
     */
    @Test
    @Transactional
    public void aDomainCarryingNoMessageSeriesDoesNotResolve() {
        Domain empty = domain(null);
        assertFalse(operands.exists(CriterionKind.DOMAIN, empty.getDomainId()));
    }

    /** The two enum kinds have no row to look up and are the validator's own business. */
    @Test
    @Transactional
    public void theEnumKindsAreLeftToTheValidator() {
        assertTrue(operands.exists(CriterionKind.MESSAGE_TYPE, "TEMPORARY_NOTICE"));
        assertTrue(operands.exists(CriterionKind.MESSAGE_MAIN_TYPE, "NM"));
    }

    // ========================================================== the save path

    /** A document whose every operand names a row is accepted. */
    @Test
    @Transactional
    public void aSeriesWhoseOperandsAllResolveIsAccepted() {
        MessageSeries ms = messageSeries();
        Area a = area();
        Chart ch = chart();

        AreaCriterionVo areaNode = new AreaCriterionVo();
        areaNode.setValues(new ArrayList<>(List.of(a.getMrn())));
        ChartCriterionVo chartNode = new ChartCriterionVo();
        chartNode.setValues(new ArrayList<>(List.of(ch.getChartNumber())));

        PublicationSeries s = seriesWith(document(
                messageSeriesNode(ms.getSeriesId()), areaNode, chartNode));

        assertTrue(SeriesValidator.danglingOperands(s, operands).isEmpty(),
                "a series whose operands all resolve was refused: "
                        + SeriesValidator.danglingOperands(s, operands));
        assertTrue(SeriesValidator.validateForActivation(s, null, operands).isEmpty(),
                "the series is not otherwise clean, so this test would pass for the wrong reason: "
                        + SeriesValidator.validateForActivation(s, null, operands));
    }

    /** And one naming an area that does not exist is refused, by name. */
    @Test
    @Transactional
    public void aDanglingAreaOperandIsRefusedAndNamed() {
        MessageSeries ms = messageSeries();

        AreaCriterionVo areaNode = new AreaCriterionVo();
        areaNode.setValues(new ArrayList<>(List.of("urn:mrn:test:area:nothing-" + tag)));

        PublicationSeries s = seriesWith(document(messageSeriesNode(ms.getSeriesId()), areaNode));

        List<SeriesValidator.FieldError> dangling = SeriesValidator.danglingOperands(s, operands);
        assertEquals(1, dangling.size(), "expected exactly one dangling operand, got " + dangling);
        assertEquals("C-4", dangling.get(0).rule());
        assertTrue(dangling.get(0).message().contains("nothing-" + tag),
                "the refusal does not name the operand that could not be resolved: " + dangling);
        assertTrue(dangling.get(0).field().startsWith("criteria/criteria/"),
                "the field does not point at the node that failed: " + dangling.get(0).field());
    }

    /** The same for a message series, which nothing else could ever have caught. */
    @Test
    @Transactional
    public void aDanglingMessageSeriesOperandIsRefused() {
        PublicationSeries s = seriesWith(document(messageSeriesNode("ms-nothing-" + tag)));

        List<SeriesValidator.FieldError> dangling = SeriesValidator.danglingOperands(s, operands);
        assertEquals(1, dangling.size(), "expected exactly one dangling operand, got " + dangling);
        assertTrue(dangling.get(0).message().contains("ms-nothing-" + tag));
    }

    /** And for a domain, which was the one kind already asked. */
    @Test
    @Transactional
    public void aDanglingDomainOperandIsRefused() {
        DomainCriterionVo domainNode = new DomainCriterionVo();
        domainNode.setValues(new ArrayList<>(List.of("dom-nothing-" + tag)));

        PublicationSeries s = seriesWith(document(domainNode));

        List<SeriesValidator.FieldError> dangling = SeriesValidator.danglingOperands(s, operands);
        assertEquals(1, dangling.size(), "expected exactly one dangling operand, got " + dangling);
        assertTrue(dangling.get(0).message().contains("dom-nothing-" + tag));
    }

    /** A dangling category is refused too, and it is reported per operand. */
    @Test
    @Transactional
    public void aDanglingCategoryOperandIsRefused() {
        MessageSeries ms = messageSeries();
        Category real = category();

        CategoryCriterionVo node = new CategoryCriterionVo();
        node.setValues(new ArrayList<>(List.of(
                real.getMrn(), "urn:mrn:test:category:nothing-" + tag)));

        PublicationSeries s = seriesWith(document(messageSeriesNode(ms.getSeriesId()), node));

        List<SeriesValidator.FieldError> dangling = SeriesValidator.danglingOperands(s, operands);
        assertEquals(1, dangling.size(),
                "the one that resolves should not be reported, and the one that does not should be: "
                        + dangling);
        assertTrue(dangling.get(0).message().contains("nothing-" + tag));
    }

    /**
     * The old signature still accepts everything, and that is the point of keeping it.
     *
     * Callers with no persistence context -- the pure rule tests, the batch import
     * processor -- go on validating the SHAPE of a document without needing a
     * database to say whether its operands name rows.
     */
    @Test
    @Transactional
    public void theResolverlessSignatureStillAcceptsEveryOperand() {
        PublicationSeries s = seriesWith(document(messageSeriesNode("ms-nothing-" + tag)));
        assertTrue(SeriesValidator.validate(s, null).stream().noneMatch(e -> "C-4".equals(e.rule())),
                "the resolver-free signature refused an operand it cannot look up");
    }
}
