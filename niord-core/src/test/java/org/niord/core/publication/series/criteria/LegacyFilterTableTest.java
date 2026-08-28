package org.niord.core.publication.series.criteria;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The translation table matches the bytes the estate actually stores.
 *
 * Every string in this class comes from message-tag-filters.json, which is
 * generated from the captured production estate. Nothing here is hand-typed,
 * and that is the entire point: the previous version of this table matched the
 * abbreviations the consent document uses (msg.type==T) against an estate that
 * stores msg.type == Type.TEMPORARY_NOTICE. All three non-blank keys missed, so
 * the import would have failed on 917 of 1,077 publications -- and the unit
 * tests passed, because they typed the same abbreviations the code did.
 *
 * A test that shares the code's assumption cannot test the assumption. This one
 * reads the data instead.
 *
 * No database and no Quarkus.
 */
public class LegacyFilterTableTest {

    private static final String RESOURCE = "/fixtures/legacy-estate/message-tag-filters.json";

    /** The declared inventory, from the plan and from the capture; they agree. */
    private static final int ESTATE = 1077;

    private static JsonNode capture() throws Exception {
        try (InputStream in = LegacyFilterTableTest.class.getResourceAsStream(RESOURCE)) {
            assertNotNull(in, RESOURCE + " is missing. It is generated from the captured estate and "
                    + "committed, because the import matches stored bytes and nothing else can prove "
                    + "which bytes those are.");
            return new ObjectMapper().readTree(in);
        }
    }

    /** filter value (null for the NULL column) -> occurrences. */
    private static Map<String, Integer> counts(JsonNode doc) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (JsonNode v : doc.get("values")) {
            JsonNode f = v.get("filter");
            out.put(f.isNull() ? null : f.asText(), v.get("count").asInt());
        }
        return out;
    }

    /**
     * The inventory is four shapes in five column values, summing to the estate.
     *
     * The fifth value is the empty string on one live ACTIVE publication. It is a
     * distinct column value from NULL and belongs in the blank bucket; keyed on
     * exact equality it would be an unknown sixth case that fails the import on a
     * publication that is in use today.
     */
    @Test
    public void theCaptureHoldsFourShapesInFiveColumnValuesSummingToTheEstate() throws Exception {
        JsonNode doc = capture();
        Map<String, Integer> counts = counts(doc);

        assertEquals(5, counts.size(),
                "five distinct column values are expected: four filter shapes, with the blank shape "
                        + "present as both NULL and the empty string. Found: " + counts.keySet());

        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(ESTATE, total, "the filter counts must account for every publication in the estate");
        assertEquals(ESTATE, doc.get("total").asInt(), "the recorded total disagrees with its own values");

        int blank = counts.getOrDefault(null, 0) + counts.getOrDefault("", 0);
        assertEquals(160, blank, "the blank shape is 159 NULL plus 1 empty string");
    }

    /**
     * Every stored value translates, under the declared normalisation and
     * nothing else: trim, collapse whitespace runs, case-sensitive.
     */
    @Test
    public void everyStoredFilterTranslatesVerbatim() throws Exception {
        Map<String, Integer> counts = counts(capture());
        List<String> failures = new ArrayList<>();

        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            try {
                LegacyFilterTranslator.Translation t = LegacyFilterTranslator.translate(e.getKey());
                assertNotNull(t.shape());
                assertNotNull(t.timeRelation());
            } catch (LegacyFilterTranslator.UnknownLegacyFilterException ex) {
                failures.add(e.getValue() + " publication(s) carry an untranslatable filter: ["
                        + e.getKey() + "]");
            }
        }

        assertTrue(failures.isEmpty(), "the translation table does not match the bytes the estate stores. "
                + "This is the failure this table exists to prevent -- an abbreviation in it means every publication "
                + "carrying the spelled-out form fails the import: " + failures);
    }

    /**
     * The shape each stored value maps to, and how many publications ride on it.
     *
     * Asserted as counts per shape rather than as "it translates", because the
     * damage from a mis-mapping is proportional to the count and a table that
     * maps everything onto one shape would pass the previous test.
     */
    @Test
    public void theShapeDistributionMatchesTheEstate() throws Exception {
        Map<String, Integer> counts = counts(capture());
        Map<LegacyFilterTranslator.Shape, Integer> byShape = new LinkedHashMap<>();

        counts.forEach((filter, n) -> byShape.merge(
                LegacyFilterTranslator.translate(filter).shape(), n, Integer::sum));

        assertEquals(501, byShape.get(LegacyFilterTranslator.Shape.TYPE_AND_STATUS));
        assertEquals(387, byShape.get(LegacyFilterTranslator.Shape.PHASE));
        assertEquals(160, byShape.get(LegacyFilterTranslator.Shape.BLANK));
        assertEquals(29, byShape.get(LegacyFilterTranslator.Shape.STATUS));
        assertEquals(ESTATE, byShape.values().stream().mapToInt(Integer::intValue).sum());
    }

    /**
     * The abbreviations must NOT translate.
     *
     * This is the regression guard. If someone re-adds the consent document's
     * shorthand to the table "to be safe", the table starts accepting two
     * spellings of the same thing, and the next person cannot tell which one the
     * estate actually uses. The abbreviation is not in the data; it must not be
     * in the table.
     */
    @Test
    public void theConsentDocumentAbbreviationsAreNotInTheTable() {
        for (String abbreviated : List.of(
                "msg.status == PUBLISHED",
                "msg.status==PUBLISHED",
                "(msg.type==T || msg.type==P) && msg.status==PUBLISHED",
                "data.phase=='msg-status-change' && msg.status==PUBLISHED")) {
            assertThrows(LegacyFilterTranslator.UnknownLegacyFilterException.class,
                    () -> LegacyFilterTranslator.translate(abbreviated),
                    "[" + abbreviated + "] is the consent document's shorthand, not a value any "
                            + "publication stores. Accepting it hides which spelling is real.");
        }
    }

    /** Whitespace variants of a stored filter are the same filter; case is not. */
    @Test
    public void normalisationIsWhitespaceOnlyAndCaseSensitive() throws Exception {
        String stored = counts(capture()).keySet().stream()
                .filter(f -> f != null && !f.isBlank()).findFirst().orElseThrow();

        assertEquals(LegacyFilterTranslator.translate(stored).shape(),
                LegacyFilterTranslator.translate("  " + stored.replace(" ", "   ") + "  ").shape(),
                "trim and whitespace-run collapse are the declared normalisation");

        assertThrows(LegacyFilterTranslator.UnknownLegacyFilterException.class,
                () -> LegacyFilterTranslator.translate(stored.toLowerCase()),
                "the match is case-sensitive; lower-casing is fuzzy matching by another name");
    }

    /** A shape outside the four fails, naming the filter it refused. */
    @Test
    public void anUnknownShapeFailsLoudlyAndNamesItself() {
        LegacyFilterTranslator.UnknownLegacyFilterException e =
                assertThrows(LegacyFilterTranslator.UnknownLegacyFilterException.class,
                        () -> LegacyFilterTranslator.translate("msg.status == Status.DRAFT"));
        assertTrue(e.getMessage().contains("refusing to guess"), e.getMessage());
        assertTrue(e.getMessage().contains("msg.status == Status.DRAFT"),
                "the refusal must name the filter it refused, or an admin cannot act on it");
    }

    /** No translated filter smuggles a status conjunct into the stored document. */
    @Test
    public void theStatusConjunctIsDroppedDeliberately() throws Exception {
        for (String f : counts(capture()).keySet()) {
            LegacyFilterTranslator.Translation t = LegacyFilterTranslator.translate(f);
            assertTrue(t.messageTypes().stream().noneMatch(v -> v.contains("PUBLISHED")),
                    "a status value leaked into the translated criteria for [" + f + "]");
        }
    }
}
