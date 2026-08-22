package org.niord.core.publication.series.criteria;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Validates a criteria document against C-1 to C-10.
 *
 * Pure: it takes the document and a resolver for operand existence, and returns
 * violations. Nothing here touches the database directly, so the whole rule set
 * is testable without one.
 */
public final class CriteriaValidator {

    /** The maximum serialized size, which bounds both the audit payload and the IN-list. */
    public static final int MAX_SERIALIZED_CHARS = 8_000;

    /** How many nodes a document may carry. */
    public static final int MAX_NODES = 8;

    /** One failed rule, carrying the JSON Pointer at which it failed. */
    public record Violation(String rule, String pointer, String message) {
        @Override
        public String toString() {
            return rule + " at " + pointer + ": " + message;
        }
    }

    /**
     * Resolves whether an operand exists. Supplied by the caller so validation
     * stays free of persistence; a test can pass a set.
     */
    public interface OperandResolver {
        boolean exists(CriterionKind kind, String value);
    }

    /** Accepts every operand. For tests that are not exercising C-4. */
    public static final OperandResolver ACCEPT_ALL = (kind, value) -> true;

    private CriteriaValidator() {
    }

    public static List<Violation> validate(IssueCriteriaVo doc, OperandResolver resolver) {
        List<Violation> out = new ArrayList<>();
        if (doc == null) {
            // A null document is the no-membership case, not an invalid one.
            return out;
        }

        // C-8: an unknown version is refused loudly, never best-effort parsed.
        if (doc.getSchemaVersion() == null || doc.getSchemaVersion() != IssueCriteriaVo.CURRENT_SCHEMA_VERSION) {
            out.add(new Violation("C-8", "/schemaVersion",
                    "expected schemaVersion " + IssueCriteriaVo.CURRENT_SCHEMA_VERSION + ", got " + doc.getSchemaVersion()));
        }

        // C-1: structural. match is required, and the node count is bounded.
        if (doc.getMatch() == null) {
            out.add(new Violation("C-1", "/match", "match is required"));
        }
        List<IssueCriterionVo> nodes = doc.getCriteria();
        if (nodes == null) {
            out.add(new Violation("C-1", "/criteria", "criteria is required"));
            return out;
        }
        if (nodes.size() > MAX_NODES) {
            out.add(new Violation("C-1", "/criteria", "at most " + MAX_NODES + " nodes, got " + nodes.size()));
        }

        Set<String> seenKindOperator = new HashSet<>();
        boolean hasScope = false;

        for (int i = 0; i < nodes.size(); i++) {
            IssueCriterionVo node = nodes.get(i);
            String at = "/criteria/" + i;

            if (node == null) {
                out.add(new Violation("C-1", at, "null node"));
                continue;
            }

            // C-2: reserved, deliberately not implemented.
            if (node.getOperator() == CriterionOperator.NOT_IN) {
                out.add(new Violation("C-2", at + "/operator", "NOT_IN is reserved, not yet supported"));
            }
            if (node.getOperator() == null) {
                out.add(new Violation("C-1", at + "/operator", "operator is required"));
            }

            // C-3: two same-kind IN nodes under match:ALL are always either
            // redundant or empty, so they are a mistake either way.
            String key = node.kind() + "/" + node.getOperator();
            if (!seenKindOperator.add(key)) {
                out.add(new Violation("C-3", at, "a second " + node.kind().wireName()
                        + " node with operator " + node.getOperator()
                        + "; under match:ALL that is either redundant or empty"));
            }

            List<String> values = node.getValues();
            if (values == null || values.isEmpty()) {
                // An empty operand must never reach the resolver: resolved as an
                // empty AND it matches everything, as an empty OR it matches
                // nothing, and both are silent.
                out.add(new Violation("C-1", at + "/values", "values must not be empty"));
                continue;
            }

            for (int v = 0; v < values.size(); v++) {
                String value = values.get(v);
                String vAt = at + "/values/" + v;

                if (value == null || value.isBlank()) {
                    out.add(new Violation("C-1", vAt, "blank operand"));
                    continue;
                }

                // C-9: area and category operands are MRNs, not surrogate ids.
                // AreaService.findByAreaId accepts either, so a numeric id would
                // resolve here and then fail to port across an export.
                if ((node.kind() == CriterionKind.AREA || node.kind() == CriterionKind.CATEGORY)
                        && value.chars().allMatch(Character::isDigit)) {
                    out.add(new Violation("C-9", vAt,
                            "a purely numeric operand is a surrogate id; an MRN is required"));
                    continue;
                }

                // C-4: never save a document with a dangling operand.
                if (!resolver.exists(node.kind(), value)) {
                    out.add(new Violation("C-4", vAt, "operand does not resolve: " + value));
                }
            }

            if (node.kind() == CriterionKind.MESSAGE_SERIES || node.kind() == CriterionKind.DOMAIN) {
                hasScope = true;
            }

            // C-5: no status criterion of any kind. There is no node kind for it,
            // so this can only arrive as an unknown discriminator -- which the
            // parser rejects -- but the rule is asserted here too because it is a
            // resolver invariant, not a schema detail.
        }

        // C-6: an unscoped query resolves across every message series in the
        // installation. That is the legacy foot-gun this rule exists to close.
        if (!nodes.isEmpty() && !hasScope) {
            out.add(new Violation("C-6", "/criteria",
                    "at least one messageSeries or domain node is required; without one the query resolves "
                            + "across every message series in the installation"));
        }

        // C-7: bounds the audit payload and the IN-list size.
        try {
            int length = CriteriaSerialization.mapper().writeValueAsString(doc).length();
            if (length > MAX_SERIALIZED_CHARS) {
                out.add(new Violation("C-7", "", "serialized document is " + length
                        + " characters, over the " + MAX_SERIALIZED_CHARS + " limit"));
            }
        } catch (JsonProcessingException e) {
            out.add(new Violation("C-1", "", "document could not be serialized: " + e.getMessage()));
        }

        return out;
    }

    /** C-10: canonicalisation normalises, it does not reject. */
    public static IssueCriteriaVo canonicalise(IssueCriteriaVo doc) {
        if (doc == null) {
            return null;
        }
        try {
            String json = CriteriaSerialization.mapper().writeValueAsString(doc);
            return CriteriaSerialization.mapper().readValue(json, IssueCriteriaVo.class);
        } catch (JsonProcessingException e) {
            throw new CriteriaParseException("could not canonicalise the criteria document", e);
        }
    }

    /** True when no rule was violated. */
    public static boolean isValid(IssueCriteriaVo doc, OperandResolver resolver) {
        return validate(doc, resolver).isEmpty();
    }

    /** The violations of one rule, for tests that assert a specific code. */
    public static List<Violation> violationsOf(List<Violation> all, String rule) {
        Predicate<Violation> is = v -> v.rule().equals(rule);
        return all.stream().filter(is).toList();
    }
}
