package org.niord.core.publication.series.criteria;

import org.junit.jupiter.api.Test;
import org.niord.core.publication.series.resolve.ResolvedCriteria;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.model.message.Type;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    @Test
    public void noStatusCriterionCanBeExpressed() {
        assertThrows(CriteriaParseException.class, () -> CONVERTER.convertToEntityAttribute(
                "{\"schemaVersion\":1,\"match\":\"ALL\",\"criteria\":[{\"kind\":\"status\",\"values\":[\"PUBLISHED\"]}]}"));
        for (CriterionKind k : CriterionKind.values()) {
            assertFalse(k.wireName().toLowerCase().contains("status"),
                    k + " looks like a status criterion; the conjunct is a resolver invariant");
        }
    }

    // ---------------------------------------------------------- C-1 .. C-10

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
     * A null column means no query at all. An empty document is a legal query
     * meaning everything in scope. Confusing them turns a link-only one-off into
     * a series that resolves the entire corpus.
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

    // ------------------------------------------- the legacy translation table

    /**
     * Exactly four distinct filter strings exist across the estate, plus the
     * no-membership case. A fifth means either new data or a wrong assumption,
     * and both need a human -- so it fails rather than being guessed at.
     */
    @Test
    public void theLegacyTableMapsFourStringsAndRefusesAFifth() {
        LegacyFilterTranslator.Translation blank = LegacyFilterTranslator.translate("");
        assertTrue(blank.hasMembership());
        assertFalse(blank.aliveAtCutoff());
        assertTrue(blank.messageTypes().isEmpty());

        LegacyFilterTranslator.Translation phase =
                LegacyFilterTranslator.translate("data.phase=='msg-status-change' && msg.status==PUBLISHED");
        assertFalse(phase.aliveAtCutoff());
        assertTrue(phase.note().contains("recorder trigger"),
                "the phase guard must be recorded as a trigger, not as membership");

        LegacyFilterTranslator.Translation inForce = LegacyFilterTranslator.translate("msg.status == PUBLISHED");
        assertTrue(inForce.aliveAtCutoff(), "the in-force regime must set the alive clause");

        LegacyFilterTranslator.Translation pt =
                LegacyFilterTranslator.translate("(msg.type==T || msg.type==P) && msg.status==PUBLISHED");
        assertEquals(Set.of("TEMPORARY_NOTICE", "PRELIMINARY_NOTICE"), pt.messageTypes());

        // Whitespace variants of the same filter are the same filter.
        assertEquals(inForce.aliveAtCutoff(), LegacyFilterTranslator.translate("msg.status==PUBLISHED").aliveAtCutoff());

        LegacyFilterTranslator.UnknownLegacyFilterException e =
                assertThrows(LegacyFilterTranslator.UnknownLegacyFilterException.class,
                        () -> LegacyFilterTranslator.translate("msg.status==DRAFT && msg.type==X"));
        assertTrue(e.getMessage().contains("refusing to guess"), e.getMessage());
    }

    /** No translated filter smuggles a status conjunct into the stored document. */
    @Test
    public void theStatusConjunctIsDroppedDeliberately() {
        for (String f : List.of("", "data.phase=='msg-status-change' && msg.status==PUBLISHED",
                "msg.status == PUBLISHED", "(msg.type==T || msg.type==P) && msg.status==PUBLISHED")) {
            LegacyFilterTranslator.Translation t = LegacyFilterTranslator.translate(f);
            assertTrue(t.messageTypes().stream().noneMatch(v -> v.contains("PUBLISHED")),
                    "a status value leaked into the translated criteria for [" + f + "]");
        }
    }
}
