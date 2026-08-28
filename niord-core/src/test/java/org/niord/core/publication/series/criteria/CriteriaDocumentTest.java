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

package org.niord.core.publication.series.criteria;

import org.junit.jupiter.api.Test;
import org.niord.core.publication.series.BindsRule;
import org.niord.core.publication.series.resolve.ResolvedCriteria;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.model.message.MainType;
import org.niord.model.message.Type;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The criteria document: serialization, validation, resolution, and the legacy
 * translation table.
 *
 * No database. The whole point of resolving to a plain envelope is that this can
 * be exercised without one.
 */
public class CriteriaDocumentTest {

    private static final JpaCriteriaAttributeConverter CONVERTER = new JpaCriteriaAttributeConverter();

    // ------------------------------------------------------------- builders

    private static IssueCriteriaVo doc(IssueCriterionVo... nodes) {
        IssueCriteriaVo d = new IssueCriteriaVo();
        d.setCriteria(new java.util.ArrayList<>(List.of(nodes)));
        return d;
    }

    private static MessageSeriesCriterionVo series(String... ids) {
        MessageSeriesCriterionVo n = new MessageSeriesCriterionVo();
        n.setValues(new java.util.ArrayList<>(List.of(ids)));
        return n;
    }

    private static MessageTypeCriterionVo types(String... names) {
        MessageTypeCriterionVo n = new MessageTypeCriterionVo();
        n.setValues(new java.util.ArrayList<>(List.of(names)));
        return n;
    }

    private static IssueCriterionVo node(CriterionKind kind, String... values) {
        IssueCriterionVo n = switch (kind) {
            case MESSAGE_SERIES -> new MessageSeriesCriterionVo();
            case MESSAGE_TYPE -> new MessageTypeCriterionVo();
            case MESSAGE_MAIN_TYPE -> new MessageMainTypeCriterionVo();
            case DOMAIN -> new DomainCriterionVo();
            case AREA -> new AreaCriterionVo();
            case CATEGORY -> new CategoryCriterionVo();
            case CHART -> new ChartCriterionVo();
        };
        n.setValues(new java.util.ArrayList<>(List.of(values)));
        return n;
    }

    /** A value of the right shape for each kind, so a document can be built for any of them. */
    private static String sampleValue(CriterionKind kind) {
        return switch (kind) {
            case MESSAGE_SERIES -> "dma-nm";
            case MESSAGE_TYPE -> "TEMPORARY_NOTICE";
            case MESSAGE_MAIN_TYPE -> "NM";
            case DOMAIN -> "niord-nm";
            case AREA -> "urn:mrn:iala:aton:dk:area:1";
            case CATEGORY -> "urn:mrn:iala:aton:dk:category:1";
            case CHART -> "101";
        };
    }

    // ------------------------------------------------ the four production shapes

    /**
     * The four worked production documents plus the fifth no-membership shape,
     * each through serialize, parse, validate and resolve.
     */
    @Test
    public void theFourProductionShapesRoundTripAndResolve() {
        // 5.5.1 blank filter, the sticky regime -- scope only.
        assertRoundTrips(doc(series("dma-nm")), TimeRelation.PUBLISHED_IN_INTERVAL, false,
                Set.of("dma-nm"), Set.of());

        // 5.5.2 phase guard plus status -- neither is stored; scope only.
        assertRoundTrips(doc(series("dma-nm")), TimeRelation.PUBLISHED_IN_INTERVAL, false,
                Set.of("dma-nm"), Set.of());

        // 5.5.3 in force at the cut-off.
        assertRoundTrips(doc(series("dma-fa")), TimeRelation.IN_FORCE_AT_CUTOFF, true,
                Set.of("dma-fa"), Set.of());

        // 5.5.4 the P&T series: the disjunction lives inside one set-valued node.
        assertRoundTrips(doc(series("dma-nm"), types("TEMPORARY_NOTICE", "PRELIMINARY_NOTICE")),
                TimeRelation.PUBLISHED_IN_INTERVAL, false,
                Set.of("dma-nm"), Set.of(Type.TEMPORARY_NOTICE, Type.PRELIMINARY_NOTICE));

        // 5.5.5 no membership at all: a NULL column, which is not an empty document.
        assertNull(CONVERTER.convertToDatabaseColumn(null));
        assertNull(CONVERTER.convertToEntityAttribute(null));
    }

    private void assertRoundTrips(IssueCriteriaVo d, TimeRelation relation, boolean alive,
                                  Set<String> expectSeries, Set<Type> expectTypes) {
        String once = CONVERTER.convertToDatabaseColumn(d);
        IssueCriteriaVo parsed = CONVERTER.convertToEntityAttribute(once);
        String twice = CONVERTER.convertToDatabaseColumn(parsed);

        assertEquals(once, twice, "serialize(parse(serialize(doc))) is not byte-identical to serialize(doc)");

        assertTrue(CriteriaValidator.isValid(parsed, CriteriaValidator.ACCEPT_ALL),
                "a production shape failed validation: " + CriteriaValidator.validate(parsed, CriteriaValidator.ACCEPT_ALL));

        ResolvedCriteria resolved = CriteriaResolver.resolve(parsed, relation, alive, CriteriaResolver.NO_DOMAINS);
        assertEquals(expectSeries, resolved.messageSeriesIds());
        assertEquals(expectTypes, resolved.types());
        assertEquals(relation, resolved.timeRelation());
        assertEquals(alive, resolved.aliveAtCutoff());
    }

    /** Field order must not depend on Java declaration order or on insertion order. */
    @BindsRule({"C-10"})
    @Test
    public void serializationIsCanonicalAndStable() {
        IssueCriteriaVo d = doc(types("TEMPORARY_NOTICE"), series("dma-nm"));
        String a = CONVERTER.convertToDatabaseColumn(d);
        String b = CONVERTER.convertToDatabaseColumn(CriteriaValidator.canonicalise(d));
        assertEquals(a, b, "canonicalising changed the bytes");

        // Properties sorted alphabetically: kind before operator before values.
        assertTrue(a.indexOf("\"kind\"") < a.indexOf("\"operator\""), "properties are not sorted: " + a);
        assertTrue(a.indexOf("\"operator\"") < a.indexOf("\"values\""), "properties are not sorted: " + a);
        assertFalse(a.contains("\n"), "output is indented; it is a column value, not a document");
    }

    // ------------------------------------------------- the converter must throw

    /**
     * The existing converters in this repo log and return null. For print
     * settings that degrades a PDF; for criteria it degrades to "no criteria at
     * all", which resolves as a different query entirely.
     */
    @Test
    public void aMalformedDocumentThrowsRatherThanReturningNull() {
        CriteriaParseException e = assertThrows(CriteriaParseException.class,
                () -> CONVERTER.convertToEntityAttribute("{ this is not json"));
        assertTrue(e.getMessage().contains("could not parse"), e.getMessage());

        // An unknown node kind is malformed, not ignorable.
        assertThrows(CriteriaParseException.class, () -> CONVERTER.convertToEntityAttribute(
                "{\"schemaVersion\":1,\"match\":\"ALL\",\"criteria\":[{\"kind\":\"messageStatus\",\"values\":[\"PUBLISHED\"]}]}"));
    }

    /** C-5 in its practical form: there is no way to express a status criterion. */
    @BindsRule({"C-5"})
    @Test
    public void noStatusCriterionCanBeExpressed() {
        assertThrows(CriteriaParseException.class, () -> CONVERTER.convertToEntityAttribute(
                "{\"schemaVersion\":1,\"match\":\"ALL\",\"criteria\":[{\"kind\":\"status\",\"values\":[\"PUBLISHED\"]}]}"));
        for (CriterionKind k : CriterionKind.values()) {
            assertFalse(k.wireName().toLowerCase().contains("status"),
                    k + " looks like a status criterion; the conjunct is a resolver invariant");
        }
    }

    // ------------------------------------------- the whole vocabulary resolves

    /**
     * No kind in the vocabulary refuses to resolve.
     *
     * Four of them used to throw, which meant a series could pass /validate and
     * then fail at publish -- the one step with no way back. Driven off the enum
     * rather than a list, so a kind added later is covered by this the day it
     * appears, and the criteria probe the editor calls on every edit cannot 500
     * on a document the same editor is allowed to save.
     */
    @Test
    public void everyKindInTheVocabularyResolves() {
        for (CriterionKind kind : CriterionKind.values()) {
            IssueCriteriaVo d = kind == CriterionKind.MESSAGE_SERIES
                    ? doc(node(kind, sampleValue(kind)))
                    : doc(series("dma-nm"), node(kind, sampleValue(kind)));

            ResolvedCriteria resolved = CriteriaResolver.resolve(d,
                    TimeRelation.PUBLISHED_IN_INTERVAL, false,
                    domainId -> Set.of("dma-nm"));

            assertNotNull(resolved, kind.wireName() + " resolved to nothing");
        }
    }

    /** Each of the four operands lands in its own field, as written. */
    @Test
    public void theFourEntityKindsResolveIntoTheirOwnOperands() {
        IssueCriteriaVo d = doc(
                series("dma-nm"),
                node(CriterionKind.MESSAGE_MAIN_TYPE, "NM"),
                node(CriterionKind.AREA, "urn:mrn:iala:aton:dk:area:kattegat"),
                node(CriterionKind.CATEGORY, "urn:mrn:iala:aton:dk:category:firing"),
                node(CriterionKind.CHART, "101", "102"));

        ResolvedCriteria r = CriteriaResolver.resolve(d, TimeRelation.PUBLISHED_IN_INTERVAL, false,
                CriteriaResolver.NO_DOMAINS);

        assertEquals(Set.of(MainType.NM), r.mainTypes());
        assertEquals(Set.of("urn:mrn:iala:aton:dk:area:kattegat"), r.areaIds());
        assertEquals(Set.of("urn:mrn:iala:aton:dk:category:firing"), r.categoryIds());
        assertEquals(Set.of("101", "102"), r.chartNumbers());
    }

    /**
     * The MRN is carried as written rather than expanded here.
     *
     * Expanding it would need a database, and this envelope is what a published
     * issue freezes to record what it selected -- a row id frozen years ago is
     * not something a reader can check, and an MRN is.
     */
    @Test
    public void anAreaOperandIsCarriedAsTheMrnItWasWrittenAs() {
        ResolvedCriteria r = CriteriaResolver.resolve(
                doc(series("dma-nm"), node(CriterionKind.AREA, "urn:mrn:iala:aton:dk:area:7")),
                TimeRelation.PUBLISHED_IN_INTERVAL, false, CriteriaResolver.NO_DOMAINS);

        assertTrue(r.readsAreas(), "the resolved criteria do not report that they select on area");
        assertEquals("urn:mrn:iala:aton:dk:area:7", r.areaIds().iterator().next());
    }

    // ---------------------------------------------------------- C-1 .. C-10

    @BindsRule({"C-1", "C-2", "C-3", "C-4", "C-6", "C-7", "C-8", "C-9"})

    @Test
    public void everyValidationRuleRejectsItsOwnFailure() {
        // C-2 NOT_IN reserved
        MessageSeriesCriterionVo notIn = series("dma-nm");
        notIn.setOperator(CriterionOperator.NOT_IN);
        assertRule("C-2", doc(notIn), "reserved");

        // C-3 two same-kind IN nodes
        assertRule("C-3", doc(series("dma-nm"), series("dma-nw")), "second messageSeries");

        // C-4 dangling operand
        List<CriteriaValidator.Violation> dangling =
                CriteriaValidator.validate(doc(series("no-such-series")), (kind, value) -> false);
        assertFalse(CriteriaValidator.violationsOf(dangling, "C-4").isEmpty(), "C-4 did not fire");

        // C-6 unscoped query
        assertRule("C-6", doc(types("TEMPORARY_NOTICE")), "messageSeries or domain");

        // C-7 oversized
        String[] many = new String[900];
        java.util.Arrays.fill(many, "a-fairly-long-message-series-identifier");
        assertRule("C-7", doc(series(many)), "over the");

        // C-8 unknown schema version
        IssueCriteriaVo wrongVersion = doc(series("dma-nm"));
        wrongVersion.setSchemaVersion(2);
        assertRule("C-8", wrongVersion, "expected schemaVersion 1");

        // C-9 numeric area operand
        AreaCriterionVo numeric = new AreaCriterionVo();
        numeric.setValues(new java.util.ArrayList<>(List.of("12345")));
        assertRule("C-9", doc(series("dma-nm"), numeric), "surrogate id");

        // C-1 empty operand list
        assertRule("C-1", doc(series("dma-nm"), types()), "must not be empty");

        // C-4, for the kinds that resolve to an enum constant with no lookup.
        assertRule("C-4", doc(series("dma-nm"), types("NOT_A_TYPE")), "is not a message type");
        assertRule("C-4", doc(series("dma-nm"), node(CriterionKind.MESSAGE_MAIN_TYPE, "NX")),
                "is not a main type");
    }

    private void assertRule(String rule, IssueCriteriaVo d, String messageFragment) {
        List<CriteriaValidator.Violation> all = CriteriaValidator.validate(d, CriteriaValidator.ACCEPT_ALL);
        List<CriteriaValidator.Violation> hits = CriteriaValidator.violationsOf(all, rule);
        assertFalse(hits.isEmpty(), rule + " did not fire; violations were " + all);
        assertTrue(hits.stream().anyMatch(v -> v.message().contains(messageFragment)),
                rule + " fired with an unexpected message: " + hits);
        assertTrue(hits.stream().allMatch(v -> v.pointer() != null),
                rule + " reported no JSON Pointer");
    }

    // ----------------------------------------------------- the two nulls

    /**
     * A null column means no query at all. An empty document is a query with no
     * predicates -- which matches every message in the installation -- and it is
     * REFUSED, at the save and at the resolve alike.
     *
     * The two nulls are what has to stay apart: a link-only one-off carries no
     * document, and reading that as "an empty query" would turn it into a series
     * that resolves the entire corpus. An empty document is not the way to say
     * "no membership"; the column being null is.
     */
    @Test
    public void aNullDocumentIsNotAnEmptyDocument() {
        assertNull(CONVERTER.convertToDatabaseColumn(null));

        IssueCriteriaVo empty = doc();
        String json = CONVERTER.convertToDatabaseColumn(empty);
        assertTrue(json.contains("\"criteria\":[]"), "an empty document must still serialize its empty list: " + json);

        // A null document has no resolved form at all -- callers must branch first.
        assertThrows(IllegalArgumentException.class,
                () -> CriteriaResolver.resolve(null, TimeRelation.PUBLISHED_IN_INTERVAL, false, CriteriaResolver.NO_DOMAINS));

        // Validation treats null as the no-membership case, not as invalid.
        assertTrue(CriteriaValidator.validate(null, CriteriaValidator.ACCEPT_ALL).isEmpty());

        // An EMPTY one is invalid, and it is C-6 that says so: no node means no
        // scope, and an unscoped query resolves across every message series.
        assertRule("C-6", empty, "messageSeries or domain");

        // And it never reaches the query, whether or not it passed a save.
        assertThrows(IllegalArgumentException.class,
                () -> CriteriaResolver.resolve(empty, TimeRelation.PUBLISHED_IN_INTERVAL, false,
                        CriteriaResolver.NO_DOMAINS),
                "an empty document resolved instead of refusing; it matches the whole corpus");
    }

    // --------------------------------------------------------- RI-6, RI-8

    @Test
    public void anEmptyOperandRaisesRatherThanResolving() {
        IssueCriteriaVo d = doc(series("dma-nm"));
        d.getCriteria().get(0).setValues(new java.util.ArrayList<>());

        CriteriaResolver.EmptyOperandException e = assertThrows(CriteriaResolver.EmptyOperandException.class,
                () -> CriteriaResolver.resolve(d, TimeRelation.PUBLISHED_IN_INTERVAL, false, CriteriaResolver.NO_DOMAINS));
        assertEquals(CriterionKind.MESSAGE_SERIES, e.kind());
    }

    @BindsRule({"RI-8"})

    @Test
    public void aDomainNodeIsAMacroExpandedBeforeTheQuery() {
        DomainCriterionVo domain = new DomainCriterionVo();
        domain.setValues(new java.util.ArrayList<>(List.of("niord-nm")));

        ResolvedCriteria resolved = CriteriaResolver.resolve(doc(domain),
                TimeRelation.PUBLISHED_IN_INTERVAL, false,
                domainId -> "niord-nm".equals(domainId) ? Set.of("dma-nm", "dma-nw") : Set.of());

        assertEquals(Set.of("dma-nm", "dma-nw"), resolved.messageSeriesIds(),
                "the domain macro did not expand into the message-series scope");

        // A domain that expands to nothing is an empty operand by another route.
        assertThrows(CriteriaResolver.EmptyOperandException.class,
                () -> CriteriaResolver.resolve(doc(domain), TimeRelation.PUBLISHED_IN_INTERVAL, false,
                        CriteriaResolver.NO_DOMAINS));
    }

    // The legacy translation table is asserted in LegacyFilterTableTest, which
    // drives it from fixtures/legacy-estate/message-tag-filters.json -- the bytes
    // the estate actually stores.
    //
    // The tests that used to sit here typed the filter strings by hand, using the
    // consent document's shorthand (msg.type==T), which is what the code matched.
    // Both were wrong in the same direction, so the tests passed on an estate that
    // would have failed 917 of 1,077 rows. A test that shares the code's assumption
    // cannot test the assumption, so it was moved to the data rather than fixed.
}
