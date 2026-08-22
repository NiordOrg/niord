package org.niord.core.publication.series.resolve;

import org.niord.model.message.Type;

import java.util.Collections;
import java.util.Set;

/**
 * A series' criteria, already resolved -- every operand expanded, nothing left
 * to look up.
 *
 * The predicate takes this rather than a criteria document on purpose: operand
 * resolution can fail, and an empty operand must RAISE rather than resolve. Left
 * to the predicate it would have to pick an identity element, and both choices
 * are silently wrong -- an empty AND matches everything, an empty OR matches
 * nothing. Resolution therefore happens before this record exists.
 *
 * aliveAtCutoff carries the one clause with a three-valued-logic hazard in it,
 * and it is a declared flag rather than something inferred, because "this series
 * does not filter on liveness" and "this series filters and everything passed"
 * are different facts that produce the same member list.
 */
public record ResolvedCriteria(
        TimeRelation timeRelation,
        Set<String> messageSeriesIds,
        Set<Type> types,
        boolean aliveAtCutoff) {

    public ResolvedCriteria {
        if (timeRelation == null) {
            throw new IllegalArgumentException("timeRelation is required");
        }
        messageSeriesIds = messageSeriesIds == null ? Set.of() : Collections.unmodifiableSet(messageSeriesIds);
        types = types == null ? Set.of() : Collections.unmodifiableSet(types);
    }

    /** An empty set means "do not filter on this", never "match nothing". */
    boolean acceptsSeries(String seriesId) {
        return messageSeriesIds.isEmpty() || messageSeriesIds.contains(seriesId);
    }

    boolean acceptsType(Type type) {
        return types.isEmpty() || types.contains(type);
    }
}
