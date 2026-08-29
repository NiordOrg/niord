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

import org.junit.jupiter.api.Test;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.criteria.IssueCriterionVo;
import org.niord.core.publication.series.criteria.MessageSeriesCriterionVo;
import org.niord.core.publication.series.resolve.TimeRelation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which document decides an issue, in one place.
 *
 * The rule is two lines long and it is read by four separate resolution sites --
 * publish, the publish checklist, the message-detail panel and the snapshot
 * header. What makes it worth a test of its own is that "the same rule at four
 * sites" is only true while nobody writes it out by hand at a fifth.
 *
 * No database. The whole decision is three fields of two entities.
 */
public class EffectiveCriteriaTest {

    // ------------------------------------------------------------------ builders

    private static IssueCriteriaVo doc(String... messageSeriesIds) {
        MessageSeriesCriterionVo node = new MessageSeriesCriterionVo();
        node.setValues(new ArrayList<>(List.of(messageSeriesIds)));
        IssueCriteriaVo d = new IssueCriteriaVo();
        d.setCriteria(new ArrayList<IssueCriterionVo>(List.of(node)));
        return d;
    }

    private static PublicationSeries series(IssueCriteriaVo criteria) {
        PublicationSeries s = new PublicationSeries();
        s.setSeriesId("dma-efs");
        s.setContentMode(ContentMode.GENERATED_FROM_QUERY);
        s.setTimeRelation(TimeRelation.PUBLISHED_IN_INTERVAL);
        s.setAliveAtCutoff(Boolean.FALSE);
        s.setCriteria(criteria);
        return s;
    }

    private static PublicationIssue issue(PublicationSeries series, IssueStatus status) {
        PublicationIssue i = new PublicationIssue();
        i.setSeries(series);
        i.setStatus(status);
        return i;
    }

    // --------------------------------------------------------------- the document

    /** With no override, the issue is its series. */
    @Test
    public void anopenIssueInheritsTheSeries() {
        IssueCriteriaVo seriesDoc = doc("dma-nm");
        PublicationIssue i = issue(series(seriesDoc), IssueStatus.OPEN);

        assertSame(seriesDoc, EffectiveCriteria.documentOf(i));
        assertFalse(EffectiveCriteria.isOverridden(i));
    }

    /** With one, the override decides. */
    @Test
    public void anopenIssueWithAnOverrideSelectsByIt() {
        IssueCriteriaVo override = doc("dma-nm", "dma-fa");
        PublicationIssue i = issue(series(doc("dma-nm")), IssueStatus.OPEN);
        i.setCriteriaOverride(override);

        assertSame(override, EffectiveCriteria.documentOf(i));
        assertTrue(EffectiveCriteria.isOverridden(i));
        assertEquals(Set.of("dma-nm", "dma-fa"),
                EffectiveCriteria.resolvedFor(i).messageSeriesIds());
    }

    /**
     * A published issue answers from its snapshot, not from its series.
     *
     * The series' criteria stay editable and the override is not frozen anywhere
     * else, so this is the only truthful answer to "what did this issue select".
     * Editing the series after publication must not retroactively change what a
     * published document is recorded as having contained.
     */
    @Test
    public void apublishedIssueAnswersFromItsSnapshot() {
        PublicationSeries s = series(doc("dma-nm"));
        PublicationIssue i = issue(s, IssueStatus.PUBLISHED);
        IssueCriteriaVo snapshot = doc("dma-nm", "dma-fa");
        i.setCriteriaSnapshot(snapshot);

        // The series changes afterwards; the published issue does not.
        s.setCriteria(doc("dma-pt"));

        assertSame(snapshot, EffectiveCriteria.documentOf(i));
        assertTrue(EffectiveCriteria.isOverridden(i));
    }

    /**
     * A snapshot equal to the series' criteria is not an override.
     *
     * Every published issue has a snapshot, so flagging on its presence would
     * label the entire archive as tailored -- and the shadow diff, which skips
     * overridden issues, would then skip every week there is.
     */
    @Test
    public void apublishedIssueMatchingItsSeriesIsNotOverridden() {
        PublicationSeries s = series(doc("dma-nm"));
        PublicationIssue i = issue(s, IssueStatus.PUBLISHED);
        i.setCriteriaSnapshot(doc("dma-nm"));

        assertFalse(EffectiveCriteria.isOverridden(i),
                "compared by value: a snapshot equal to the series' criteria is no deviation, "
                        + "however it came to be taken");
    }

    /**
     * A published issue with no snapshot falls back rather than answering nothing.
     *
     * Rows written before the snapshot existed, and rows written by something
     * other than the publish path. Falling back keeps them explainable; answering
     * null would make them look like publications that selected nothing.
     */
    @Test
    public void apublishedIssueWithNoSnapshotFallsBackToTheSeries() {
        IssueCriteriaVo seriesDoc = doc("dma-nm");
        PublicationIssue i = issue(series(seriesDoc), IssueStatus.PUBLISHED);

        assertSame(seriesDoc, EffectiveCriteria.documentOf(i));
    }

    /** A series with no criteria and no override selects nothing, which is not empty. */
    @Test
    public void noDocumentAnywhereResolvesToNull() {
        PublicationIssue i = issue(series(null), IssueStatus.OPEN);

        assertNull(EffectiveCriteria.documentOf(i));
        assertNull(EffectiveCriteria.resolvedFor(i),
                "a null document means NO QUERY, and resolving an empty one would either raise "
                        + "or match the whole corpus");
    }

    /**
     * An override overrides the DOCUMENT, and nothing else.
     *
     * timeRelation and aliveAtCutoff describe how the series relates its issues to
     * time -- whether they tile or overlap -- and an issue whose relation differed
     * from its siblings would not be an issue of that series in any useful sense.
     */
    @Test
    public void anoverrideDoesNotChangeTheTimeRelation() {
        PublicationSeries s = series(doc("dma-nm"));
        s.setTimeRelation(TimeRelation.IN_FORCE_AT_CUTOFF);
        s.setAliveAtCutoff(Boolean.TRUE);

        PublicationIssue i = issue(s, IssueStatus.OPEN);
        i.setCriteriaOverride(doc("dma-fa"));

        var resolved = EffectiveCriteria.resolvedFor(i);
        assertEquals(TimeRelation.IN_FORCE_AT_CUTOFF, resolved.timeRelation());
        assertTrue(resolved.aliveAtCutoff());
        assertEquals(Set.of("dma-fa"), resolved.messageSeriesIds());
    }

    /**
     * "Selects something else" and "somebody tailored it" are DIFFERENT questions.
     *
     * An imported issue's snapshot differs from its series' criteria as a matter
     * of course: the importer records what each release actually selected, and a
     * series spanning two legacy filter eras carries one setting while 122 of its
     * issues need the other. Nobody tailored those.
     *
     * Conflating the two made the shadow diff skip the entire imported estate --
     * every release "overridden", nothing compared, and the cutover evidence
     * silently stopped accumulating. The replay test caught it; this pins it.
     */
    @Test
    public void animportedSnapshotIsNotAHumanDecision() {
        PublicationSeries s = series(doc("dma-nm"));
        PublicationIssue imported = issue(s, IssueStatus.PUBLISHED);
        imported.setCriteriaSnapshot(doc("dma-nm", "dma-fa"));

        assertTrue(EffectiveCriteria.isOverridden(imported),
                "it did select something other than the series says, and the label should say so");
        assertFalse(EffectiveCriteria.hasOwnCriteria(imported),
                "but nobody tailored it, so anything treating it as a human decision -- the "
                        + "shadow diff's skip in particular -- must not fire");
    }

    /** A tailored issue answers yes to both. */
    @Test
    public void atailoredIssueIsBothOverriddenAndOwned() {
        PublicationIssue i = issue(series(doc("dma-nm")), IssueStatus.OPEN);
        i.setCriteriaOverride(doc("dma-fa"));

        assertTrue(EffectiveCriteria.isOverridden(i));
        assertTrue(EffectiveCriteria.hasOwnCriteria(i));
    }

    /** Nulls are answered, not thrown at. */
    @Test
    public void thenullCasesAreAnswered() {
        assertNull(EffectiveCriteria.documentOf(null));
        assertFalse(EffectiveCriteria.isOverridden(null));
        assertNull(EffectiveCriteria.resolvedFor(null));

        PublicationIssue orphan = new PublicationIssue();
        orphan.setStatus(IssueStatus.OPEN);
        assertNull(EffectiveCriteria.documentOf(orphan));
        assertFalse(EffectiveCriteria.isOverridden(orphan));
    }
}
