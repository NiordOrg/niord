package org.niord.core.publication.series.legacy;

import org.niord.core.publication.Publication;
import org.niord.core.publication.series.PublicationIssue;
import org.niord.core.publication.series.PublicationSeries;
import org.niord.core.publication.series.criteria.IssueCriteriaVo;
import org.niord.core.publication.series.criteria.IssueCriterionVo;
import org.niord.core.publication.series.criteria.LegacyFilterTranslator;
import org.niord.core.publication.series.criteria.MessageSeriesCriterionVo;
import org.niord.core.publication.series.criteria.MessageTypeCriterionVo;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The per-issue snapshot header, derived from the issue's OWN filter.
 *
 * WHY THIS IS NOT A COPY OF THE SERIES ROW. A series outlives its own filter.
 * Measured on the captured estate, template a8e661ee carries 111 issues from the
 * blank era and 382 from the phase era, so the one series that template becomes
 * has a single aliveAtCutoff answer while its own history needs two. Read the
 * live series flag and 122 tag-carrying blank-era issues get the alive conjunct
 * they never had -- 116 of them the weekly EfS.
 *
 * WHY THAT MATTERS LATER. The historical replay re-resolves every imported issue with a
 * build gate of actualDiffs subset-of manifest. With the conjunct wrongly on,
 * the replay records spurious drops that those issues reproduce exactly with it
 * off. That divergence cannot honestly be added to the expected-diff manifest,
 * so the pressure at that point is to widen the manifest -- which converts a
 * known-correct result into a suppressed one. The defect is planted here, where
 * The publicId assignment is one-way, and surfaces where it cannot be
 * cheaply undone.
 *
 * As originally specified the importer left all nine of these columns NULL on
 * ~1,077 issues, a state the data model reserves for NO_MEMBERSHIP, and no
 * invariant bound it. All three external reviewers converged on it independently.
 */
public final class IssueSnapshotDeriver {

    private IssueSnapshotDeriver() {
    }

    /**
     * Writes the whole header onto the issue.
     *
     * frozenAt is passed in rather than taken as "now" so that one import run
     * stamps every issue with one instant. A per-row now() would leave the run
     * unidentifiable afterwards and make two issues of the same publication look
     * as though they were frozen by different events.
     */
    public static void derive(PublicationIssue issue, Publication legacy, PublicationSeries series,
                              Date frozenAt) {
        LegacyFilterTranslator.Translation t =
                LegacyFilterTranslator.translate(legacy.getMessageTagFilter());

        // The two the review was about. Both come from the translation of THIS
        // publication's filter and from nothing else -- in particular not from
        // series.getTimeRelation() or series.getAliveAtCutoff(), which is the
        // whole point and is asserted in the tests.
        issue.setSnapshotTimeRelation(t.timeRelation());
        issue.setSnapshotAliveAtCutoff(t.aliveAtCutoff());

        issue.setSnapshotIntervalFrom(issue.getIntervalFrom());
        issue.setSnapshotDomainId(legacy.getDomain() == null ? null : legacy.getDomain().getDomainId());
        issue.setSnapshotSeriesIds(seriesIds(legacy));
        issue.setSnapshotSortBy(series == null ? null : series.getMessageSortBy());
        issue.setSnapshotSortOrder(series == null || series.getMessageSortOrder() == null
                ? null : series.getMessageSortOrder().name());
        issue.setCriteriaSnapshot(criteria(t, legacy));
        issue.setSnapshotFrozenAt(frozenAt);
    }

    /**
     * The message series in scope at freeze time, as a stable comma-joined list.
     *
     * A legacy publication has no message-series column: its scope is the domain
     * it belongs to. So this records the domain's series membership AS IT IS AT
     * IMPORT, which is the honest reading -- the frozen header says what the
     * query would have covered, and a later edit to the domain must not silently
     * rewrite a published issue's recorded scope.
     *
     * Null, not the empty string, when there is no domain. Empty would claim the
     * scope was computed and found to be nothing.
     */
    private static String seriesIds(Publication legacy) {
        if (legacy.getDomain() == null || legacy.getDomain().getMessageSeries() == null
                || legacy.getDomain().getMessageSeries().isEmpty()) {
            return null;
        }
        return legacy.getDomain().getMessageSeries().stream()
                .map(s -> s.getSeriesId())
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .collect(Collectors.joining(","));
    }

    /**
     * The criteria document as it stood for this issue.
     *
     * Built from the translated filter, not copied from the series, for the same
     * reason as the two flags above. The status conjunct present in three of the
     * four legacy filters is deliberately NOT translated into a criterion: status
     * is a resolver invariant (RI-1), and storing it would let a later edit
     * weaken it.
     */
    private static IssueCriteriaVo criteria(LegacyFilterTranslator.Translation t, Publication legacy) {
        IssueCriteriaVo doc = new IssueCriteriaVo();
        List<IssueCriterionVo> nodes = new ArrayList<>();

        if (!t.messageTypes().isEmpty()) {
            MessageTypeCriterionVo types = new MessageTypeCriterionVo();
            types.setValues(new ArrayList<>(t.messageTypes()));
            nodes.add(types);
        }

        Set<String> series = legacy.getDomain() == null || legacy.getDomain().getMessageSeries() == null
                ? Set.of()
                : legacy.getDomain().getMessageSeries().stream()
                        .map(s -> s.getSeriesId())
                        .filter(id -> id != null && !id.isBlank())
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!series.isEmpty()) {
            MessageSeriesCriterionVo node = new MessageSeriesCriterionVo();
            node.setValues(new ArrayList<>(series));
            nodes.add(node);
        }

        // An EMPTY criteria list is a legal query meaning "everything in scope",
        // and is a different thing from a null document meaning no query at all.
        // The blank-era issues land here legitimately: their scope came from the
        // series alone, which is exactly a query with no narrowing nodes.
        doc.setCriteria(nodes);
        return doc;
    }
}
