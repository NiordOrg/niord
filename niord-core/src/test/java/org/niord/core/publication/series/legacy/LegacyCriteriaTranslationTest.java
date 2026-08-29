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

package org.niord.core.publication.series.legacy;

import org.junit.jupiter.api.Test;
import org.niord.core.publication.series.criteria.CriteriaValidator;
import org.niord.core.publication.series.criteria.CriterionKind;
import org.niord.core.publication.series.criteria.CriterionOperator;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.criteria.IssueCriterionVo;
import org.niord.core.publication.series.criteria.JpaCriteriaAttributeConverter;
import org.niord.core.publication.series.criteria.LegacyFilterTranslator;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The criteria document translated for an imported series.
 *
 * It matters more than most translations because it decides what goes INTO a
 * publication. A wrong document produces confident wrong comparisons; a missing
 * one is merely a series that refuses to activate. That asymmetry is why the
 * no-evidence case returns null rather than something plausible.
 *
 * The scope operand is EVIDENCE -- the message series the archive actually drew
 * from. Measured on the test estate 2026-08-25: every sampled member of both
 * weekly-ntm and weekly-ntm-p-t belongs to `dma-nm`, and their types are mixed
 * (PERMANENT / TEMPORARY / MISCELLANEOUS) for the EfS and TEMPORARY-only for the
 * P&T, which is exactly what the two filters say.
 */
public class LegacyCriteriaTranslationTest {

    private static final Set<String> DMA_NM = Set.of("dma-nm");

    private static IssueCriterionVo nodeOf(IssueCriteriaVo doc, CriterionKind kind) {
        return doc.getCriteria().stream().filter(n -> n.kind() == kind).findFirst().orElse(null);
    }

    // ------------------------------------------------------------- the four shapes

    /**
     * The blank filter scopes on the series alone: no type node at all.
     *
     * The blank era is the "sticky" regime, where the filter selects on nothing
     * and the scope comes from what the recorder was pointed at. A type node here
     * would narrow an issue that never was narrowed.
     */
    @Test
    public void theBlankFilterProducesScopeAndNothingElse() {
        IssueCriteriaVo doc = LegacyCriteriaTranslation.translate(
                LegacyFilterTranslator.translate(""), DMA_NM);

        assertNotNull(doc);
        assertEquals(1, doc.getCriteria().size(), "scope only");
        assertSame(CriterionKind.MESSAGE_SERIES, doc.getCriteria().get(0).kind());
        assertEquals(List.of("dma-nm"), doc.getCriteria().get(0).getValues());
    }

    /**
     * The phase filter likewise: the guard is a recorder trigger, not membership.
     *
     * It says WHEN the tag was written, not WHICH messages belong, so translating
     * it into a criterion would filter on something that was never a filter.
     */
    @Test
    public void thePhaseGuardContributesNoCriterion() {
        IssueCriteriaVo doc = LegacyCriteriaTranslation.translate(
                LegacyFilterTranslator.translate(
                        "data.phase == 'msg-status-change' && msg.status == Status.PUBLISHED"),
                DMA_NM);

        assertEquals(1, doc.getCriteria().size(),
                "the phase guard is a recorder trigger and must not become a membership node");
        assertSame(CriterionKind.MESSAGE_SERIES, doc.getCriteria().get(0).kind());
    }

    /** The status-only filter is the in-force regime; status itself is never stored. */
    @Test
    public void theStatusFilterContributesNoCriterionBecauseStatusIsAnInvariant() {
        IssueCriteriaVo doc = LegacyCriteriaTranslation.translate(
                LegacyFilterTranslator.translate("msg.status == Status.PUBLISHED"), DMA_NM);

        assertEquals(1, doc.getCriteria().size(),
                "status is a resolver invariant (RI-1, C-5); storing it would let an edit weaken it");
    }

    /**
     * The P&T filter's disjunction becomes ONE set-valued messageType node.
     *
     * Two nodes of the same kind under match:ALL would be either redundant or
     * empty, which C-3 rejects outright — so the disjunction has to collapse into
     * a single node's values, not into two nodes.
     */
    @Test
    public void thePtDisjunctionBecomesOneSetValuedTypeNode() {
        IssueCriteriaVo doc = LegacyCriteriaTranslation.translate(
                LegacyFilterTranslator.translate(
                        "(msg.type == Type.TEMPORARY_NOTICE || msg.type == Type.PRELIMINARY_NOTICE) "
                                + "&& msg.status == Status.PUBLISHED"),
                DMA_NM);

        assertEquals(2, doc.getCriteria().size(), "scope plus one type node");
        IssueCriterionVo types = nodeOf(doc, CriterionKind.MESSAGE_TYPE);
        assertNotNull(types);
        assertEquals(List.of("TEMPORARY_NOTICE", "PRELIMINARY_NOTICE"), types.getValues());
        assertSame(CriterionOperator.IN, types.getOperator());
    }

    // ------------------------------------------------------------------ the scope

    /**
     * With no evidence there is NO document, rather than an unscoped one.
     *
     * C-6 requires a messageSeries or domain node because a document without one
     * resolves over every message in the system. An issue that silently contains
     * everything is far worse than a series that refuses to activate until
     * somebody looks at it, and refusing at import is where the report can name
     * it.
     */
    @Test
    public void noEvidenceProducesNoDocumentRatherThanAnUnscopedOne() {
        assertNull(LegacyCriteriaTranslation.translate(
                LegacyFilterTranslator.translate(""), Set.of()));
        assertNull(LegacyCriteriaTranslation.translate(
                LegacyFilterTranslator.translate(""), List.of("", "  ")));
        assertNull(LegacyCriteriaTranslation.translate(
                LegacyFilterTranslator.translate(""), null));
    }

    /**
     * The scope is sorted and de-duplicated.
     *
     * The document is diffed and reviewed by people. A set whose order depends on
     * the query plan makes two identical imports produce two different-looking
     * documents, and the reviewer cannot tell that from a real change.
     */
    @Test
    public void theScopeIsStableAcrossImports() {
        IssueCriteriaVo doc = LegacyCriteriaTranslation.translate(
                LegacyFilterTranslator.translate(""),
                List.of("dma-nw", "dma-nm", "dma-nw", "dma-nm"));

        assertEquals(List.of("dma-nm", "dma-nw"), doc.getCriteria().get(0).getValues());
    }

    // -------------------------------------------------------------- it must validate

    /**
     * Every translated document passes the validator that gates activation.
     *
     * The point of writing one at all is that the series can be activated after
     * review. A document that cannot pass C-1..C-10 leaves the series exactly
     * where a null one did, having merely looked like progress.
     *
     * ACCEPT_ALL for the operand resolver: C-4 checks that operands resolve
     * against live data, which is a property of the estate rather than of the
     * translation, and the shadow diff is what tests it against reality.
     */
    @Test
    public void everyTranslatedDocumentPassesTheActivationValidator() {
        for (String filter : List.of(
                "",
                "data.phase == 'msg-status-change' && msg.status == Status.PUBLISHED",
                "msg.status == Status.PUBLISHED",
                "(msg.type == Type.TEMPORARY_NOTICE || msg.type == Type.PRELIMINARY_NOTICE) "
                        + "&& msg.status == Status.PUBLISHED")) {

            IssueCriteriaVo doc = LegacyCriteriaTranslation.translate(
                    LegacyFilterTranslator.translate(filter), DMA_NM);

            List<CriteriaValidator.Violation> violations =
                    CriteriaValidator.validate(doc, CriteriaValidator.ACCEPT_ALL);

            assertTrue(violations.isEmpty(),
                    "the document for [" + filter + "] does not validate: " + violations
                            + " -- a document that cannot pass leaves the series exactly where a "
                            + "null one did, having merely looked like progress");
        }
    }

    /**
     * A document survives the JPA converter round trip AS AN EQUAL VALUE.
     *
     * Not tidiness. The criteria column is a converted attribute: Hibernate decides
     * whether the row is dirty by comparing the loaded snapshot against the current
     * value, and the converter deserializes a fresh object every time. Without value
     * equality the two are different instances of an identical document, so every
     * flush writes a spurious UPDATE and bumps the version -- and a bulk delete
     * followed by that flush fails outright.
     *
     * That is how it surfaced: the undo could not delete a series it had just read,
     * with an optimistic-lock error naming it. It would also have shown up as
     * intermittent lock conflicts between two people editing different series.
     */
    @Test
    public void adocumentSurvivesTheConverterRoundTripAsAnEqualValue() {
        JpaCriteriaAttributeConverter converter = new JpaCriteriaAttributeConverter();

        for (String filter : List.of(
                "",
                "(msg.type == Type.TEMPORARY_NOTICE || msg.type == Type.PRELIMINARY_NOTICE) "
                        + "&& msg.status == Status.PUBLISHED")) {

            IssueCriteriaVo original = LegacyCriteriaTranslation.translate(
                    LegacyFilterTranslator.translate(filter), DMA_NM);

            IssueCriteriaVo reloaded = converter.convertToEntityAttribute(
                    converter.convertToDatabaseColumn(original));

            assertNotSame(original, reloaded, "the fixture is pointless if it is the same object");
            assertEquals(original, reloaded,
                    "a reloaded document must equal the one stored, or every flush marks the "
                            + "series dirty and rewrites a row nothing changed");
            assertEquals(original.hashCode(), reloaded.hashCode());
        }
    }

    /** And two genuinely different documents stay different. */
    @Test
    public void documentsThatDifferAreNotEqual() {
        IssueCriteriaVo efs = LegacyCriteriaTranslation.translate(
                LegacyFilterTranslator.translate(""), DMA_NM);
        IssueCriteriaVo pt = LegacyCriteriaTranslation.translate(
                LegacyFilterTranslator.translate(
                        "(msg.type == Type.TEMPORARY_NOTICE || msg.type == Type.PRELIMINARY_NOTICE) "
                                + "&& msg.status == Status.PUBLISHED"),
                DMA_NM);
        IssueCriteriaVo otherScope = LegacyCriteriaTranslation.translate(
                LegacyFilterTranslator.translate(""), Set.of("dma-fa"));

        org.junit.jupiter.api.Assertions.assertNotEquals(efs, pt, "a type node is a difference");
        org.junit.jupiter.api.Assertions.assertNotEquals(efs, otherScope, "so is the scope");
    }

    /** And the estate's four filters are the only ones translated; a fifth still refuses. */
    @Test
    public void anUnknownFilterStillRefusesRatherThanProducingAnEmptyDocument() {
        org.junit.jupiter.api.Assertions.assertThrows(
                LegacyFilterTranslator.UnknownLegacyFilterException.class,
                () -> LegacyCriteriaTranslation.translate(
                        LegacyFilterTranslator.translate("msg.type == Type.SOMETHING_NEW"), DMA_NM));
    }
}
