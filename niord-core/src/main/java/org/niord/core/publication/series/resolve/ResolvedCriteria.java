package org.niord.core.publication.series.resolve;

import org.niord.model.message.MainType;
import org.niord.model.message.Type;

import java.util.Collections;
import java.util.Set;

/**
 * A series' criteria, already resolved -- every macro expanded and every operand
 * in the shape the query and the predicate take it in.
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
 *
 * areaIds and categoryIds hold MRNs, exactly as the message search's parameters
 * of the same names hold whatever stable key the caller supplied: turning one
 * into a row is a database question, it is answered where the query is built,
 * and it can fail -- an MRN naming nothing must refuse rather than drop out of
 * an OR and leave a disjunction over nothing. Keeping them as written also keeps
 * this record portable, which is what lets it be frozen into a published issue's
 * snapshot header and read back years later.
 */
public record ResolvedCriteria(
        TimeRelation timeRelation,
        Set<String> messageSeriesIds,
        Set<Type> types,
        Set<MainType> mainTypes,
        Set<String> areaIds,
        Set<String> categoryIds,
        Set<String> chartNumbers,
        boolean aliveAtCutoff) {

    public ResolvedCriteria {
        if (timeRelation == null) {
            throw new IllegalArgumentException("timeRelation is required");
        }
        messageSeriesIds = frozen(messageSeriesIds);
        types = frozen(types);
        mainTypes = frozen(mainTypes);
        areaIds = frozen(areaIds);
        categoryIds = frozen(categoryIds);
        chartNumbers = frozen(chartNumbers);
    }

    /** The scope-and-type shape, for criteria that select on nothing else. */
    public ResolvedCriteria(TimeRelation timeRelation, Set<String> messageSeriesIds,
                            Set<Type> types, boolean aliveAtCutoff) {
        this(timeRelation, messageSeriesIds, types, Set.of(), Set.of(), Set.of(), Set.of(), aliveAtCutoff);
    }

    private static <T> Set<T> frozen(Set<T> values) {
        return values == null ? Set.of() : Collections.unmodifiableSet(values);
    }

    /**
     * An empty set means "do not filter on this", never "match nothing".
     *
     * A message that HAS no value cannot satisfy an operand that names one, and
     * that is decided here rather than by asking the set: a set built with
     * Set.of() throws on contains(null), so the message with no message series --
     * which the corpus does contain -- would take down the whole resolution
     * depending on which collection the caller happened to build the operands in.
     */
    boolean acceptsSeries(String seriesId) {
        return messageSeriesIds.isEmpty() || (seriesId != null && messageSeriesIds.contains(seriesId));
    }

    boolean acceptsType(Type type) {
        return types.isEmpty() || (type != null && types.contains(type));
    }

    boolean acceptsMainType(MainType mainType) {
        return mainTypes.isEmpty() || (mainType != null && mainTypes.contains(mainType));
    }

    /**
     * Hierarchy-aware, because the facts are: the message's own MRNs and its
     * areas' ancestors arrive in one set, so a criterion naming a parent area
     * matches a message filed under a child by plain intersection.
     */
    boolean acceptsAreas(Set<String> messageAreaMrns) {
        return areaIds.isEmpty() || intersects(areaIds, messageAreaMrns);
    }

    boolean acceptsCategories(Set<String> messageCategoryMrns) {
        return categoryIds.isEmpty() || intersects(categoryIds, messageCategoryMrns);
    }

    boolean acceptsCharts(Set<String> messageChartNumbers) {
        return chartNumbers.isEmpty() || intersects(chartNumbers, messageChartNumbers);
    }

    /** Whether this selects on anything the message row does not carry itself. */
    public boolean readsAreas() {
        return !areaIds.isEmpty();
    }

    public boolean readsCategories() {
        return !categoryIds.isEmpty();
    }

    public boolean readsCharts() {
        return !chartNumbers.isEmpty();
    }

    private static boolean intersects(Set<String> operands, Set<String> facts) {
        for (String value : operands) {
            if (facts.contains(value)) {
                return true;
            }
        }
        return false;
    }
}
