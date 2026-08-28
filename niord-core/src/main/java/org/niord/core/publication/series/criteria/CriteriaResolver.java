package org.niord.core.publication.series.criteria;

import org.niord.core.publication.series.PublicationException;
import org.niord.core.publication.series.resolve.ResolvedCriteria;
import org.niord.core.publication.series.resolve.TimeRelation;
import org.niord.model.message.MainType;
import org.niord.model.message.Type;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns a stored criteria document into the resolved envelope the predicate takes.
 *
 * Resolution happens here rather than inside the predicate because it can FAIL,
 * and failing is the correct outcome for an empty operand. Left to the predicate
 * it would have to choose an identity element, and both choices are silently
 * wrong -- an empty AND matches everything, an empty OR matches nothing.
 */
public final class CriteriaResolver {

    /** Expands a domain to the message series it contains. */
    public interface DomainExpander {
        /** The seriesIds of the given domain, or an empty set if the domain is unknown. */
        Set<String> seriesIdsOf(String domainId);
    }

    /** For documents with no domain node, and for tests that are not exercising the macro. */
    public static final DomainExpander NO_DOMAINS = domainId -> Set.of();

    private CriteriaResolver() {
    }

    /**
     * Resolves a document against a time relation.
     *
     * @param doc          the stored document; null means no membership at all
     * @param timeRelation the series' declared relation
     * @param aliveAtCutoff whether the series filters on liveness at the cut-off
     * @param expander     expands any domain node
     */
    public static ResolvedCriteria resolve(IssueCriteriaVo doc,
                                           TimeRelation timeRelation,
                                           boolean aliveAtCutoff,
                                           DomainExpander expander) {
        if (timeRelation == null) {
            throw new IllegalArgumentException("a series must declare a time relation to resolve criteria");
        }
        if (doc == null) {
            throw new IllegalArgumentException(
                    "a null criteria document means NO MEMBERSHIP, which has no resolved form. "
                            + "Callers must branch on contentMode before reaching here.");
        }

        // C-6, at the last gate before the query. A document that exists and
        // carries no node selects on nothing, and "select on nothing" is a query
        // with no predicates -- every message in the installation. It is refused at
        // save; refused here too because a document can reach a resolve without
        // passing a save, and matching the whole corpus is not a failure anybody
        // would notice from the outside.
        if (doc.getCriteria() == null || doc.getCriteria().isEmpty()) {
            throw new IllegalArgumentException(
                    "a criteria document with no nodes selects on nothing, which resolves across every "
                            + "message series in the installation. NO membership is a null document; "
                            + "this one is a query with no predicates.");
        }

        Set<String> seriesIds = new LinkedHashSet<>();
        Set<Type> types = new LinkedHashSet<>();
        Set<MainType> mainTypes = new LinkedHashSet<>();
        Set<String> areaIds = new LinkedHashSet<>();
        Set<String> categoryIds = new LinkedHashSet<>();
        Set<String> chartNumbers = new LinkedHashSet<>();

        for (IssueCriterionVo node : doc.getCriteria()) {
            List<String> values = node.getValues();

            // RI-6. An empty operand raises rather than resolving to an identity.
            if (values == null || values.isEmpty()) {
                throw new EmptyOperandException(node.kind());
            }

            switch (node.kind()) {
                case MESSAGE_SERIES -> seriesIds.addAll(values);

                // RI-8. A domain node is a MACRO, expanded to a message-series set
                // before the query and frozen into the snapshot. It is not a
                // predicate: MessageSearchParams.domain is never read by the search,
                // so applying the series' own domain as one double-counts the scope
                // silently.
                case DOMAIN -> {
                    for (String domainId : values) {
                        Set<String> expanded = expander.seriesIdsOf(domainId);
                        if (expanded.isEmpty()) {
                            throw new EmptyOperandException(CriterionKind.DOMAIN,
                                    "domain " + domainId + " expands to no message series");
                        }
                        seriesIds.addAll(expanded);
                    }
                }

                case MESSAGE_TYPE -> {
                    for (String v : values) {
                        types.add(Type.valueOf(v));
                    }
                }

                case MESSAGE_MAIN_TYPE -> {
                    for (String v : values) {
                        mainTypes.add(MainType.valueOf(v));
                    }
                }

                // The three operands that name a row somewhere else. They are
                // carried as written -- MRNs and chart numbers -- and turned into
                // a query where the query is built: that lookup needs a database,
                // it can fail, and a failure has to refuse rather than quietly
                // shrink the disjunction. Expanding them here would also make this
                // envelope unportable, and it is the thing a published issue
                // freezes to say what it selected.
                case AREA -> areaIds.addAll(values);

                case CATEGORY -> categoryIds.addAll(values);

                case CHART -> chartNumbers.addAll(values);

                default -> throw new IllegalStateException("unhandled criterion kind: " + node.kind());
            }
        }

        return new ResolvedCriteria(timeRelation, seriesIds, types, mainTypes,
                areaIds, categoryIds, chartNumbers, aliveAtCutoff);
    }

    /** An operand list that would have resolved to an identity element. */
    public static class EmptyOperandException extends PublicationException {

        private final CriterionKind kind;

        public EmptyOperandException(CriterionKind kind) {
            this(kind, "an empty operand cannot resolve: as an AND it matches everything, as an OR nothing");
        }

        public EmptyOperandException(CriterionKind kind, String message) {
            super("EMPTY_OPERAND", kind.wireName() + ": " + message);
            this.kind = kind;
        }

        public CriterionKind kind() {
            return kind;
        }
    }
}
