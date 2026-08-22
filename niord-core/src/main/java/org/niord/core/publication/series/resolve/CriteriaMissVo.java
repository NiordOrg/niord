package org.niord.core.publication.series.resolve;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One message the criteria did not match, and why.
 *
 * The extra fields are carried per code rather than as a fixed shape, because
 * what makes a miss understandable differs: a date comparison needs both dates,
 * a criterion mismatch needs what was expected against what was found.
 */
public record CriteriaMissVo(String messageUid, CriteriaMissCode code, Map<String, Object> detail) {

    public static CriteriaMissVo of(MessageFacts facts, MembershipReason reason, Interval interval) {
        CriteriaMissCode code = CriteriaMissCode.of(reason);
        Map<String, Object> detail = new LinkedHashMap<>();

        switch (code) {
            case BEFORE_INTERVAL -> {
                detail.put("publishDateFrom", epoch(facts.publishDateFrom()));
                detail.put("intervalFrom", epoch(interval.previousCutoff()));
            }
            case AFTER_CUTOFF -> {
                detail.put("publishDateFrom", epoch(facts.publishDateFrom()));
                detail.put("cutoff", epoch(interval.cutoff()));
            }
            case NOT_ALIVE_AT_CUTOFF -> {
                detail.put("publishDateTo", epoch(facts.publishDateTo()));
                detail.put("cutoff", epoch(interval.cutoff()));
            }
            case STATUS_NOT_PUBLIC -> detail.put("status", facts.status() == null ? null : facts.status().name());
            case CRITERION_MISMATCH -> {
                // Which criterion missed is decided by the caller, which holds the
                // resolved criteria; the shape is fixed here so it cannot drift.
                detail.put("kind", null);
                detail.put("operator", "IN");
                detail.put("expected", java.util.List.of());
                detail.put("actual", null);
            }
            case NO_PUBLISH_DATE -> {
                // Nothing to carry: the absence IS the fact.
            }
        }
        // Keyed on uid alone. shortId is display text, resolved later and never a key.
        return new CriteriaMissVo(facts.uid(), code, detail);
    }

    private static Long epoch(java.util.Date d) {
        return d == null ? null : d.getTime();
    }
}
