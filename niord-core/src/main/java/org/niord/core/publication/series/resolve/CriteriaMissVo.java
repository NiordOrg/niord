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

package org.niord.core.publication.series.resolve;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One message the criteria did not match, and why.
 *
 * The extra fields are carried per code rather than as a fixed shape, because
 * what makes a miss understandable differs: a date comparison needs both dates,
 * a criterion mismatch needs what was expected against what was found.
 *
 * THREE NAMES, and only one of them is an identity. `messageUid` is the key and
 * is always present. `messageId` is the short id an editor reads and cites --
 * "NM-815-26" -- and it is DISPLAY TEXT: it is not declared unique, nothing stops
 * it being reused, and a draft has none at all, so it is null wherever the message
 * has none. Keying anything on it would let two different messages compare equal.
 * `title` is the message's live title in the language the list was asked for, and
 * it is what makes an UNNUMBERED row readable at all -- a draft omitted from an
 * issue has no short id, and "this uid was left out" is not something a curator
 * can act on. Both are absent from a row until something fills them in, because
 * the resolution decides membership from facts that deliberately do not carry
 * them; see {@link #withName}.
 */
public record CriteriaMissVo(String messageUid, String messageId, String title,
                             CriteriaMissCode code, Map<String, Object> detail) {

    /**
     * The same miss, carrying the names a reader recognises.
     *
     * A copy rather than a setter because the record is a value: the misses are
     * handed around a resolution that several screens read, and a row that could
     * be mutated in place would let one reader's display lookup change what
     * another one already had.
     */
    public CriteriaMissVo withName(String shortId, String title) {
        return new CriteriaMissVo(messageUid, shortId, title, code, detail);
    }

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
                // Both instants, because either can be the one that dropped it: a
                // closed window, or a cancel before the cut-off under a validity end
                // that lies after it. The reader sees which fell short.
                detail.put("publishDateTo", epoch(facts.publishDateTo()));
                detail.put("withdrawnAt", epoch(facts.withdrawnAt()));
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
        // Keyed on uid alone. The short id and the title are display text,
        // resolved later and never keys -- and the facts a decision is taken from
        // do not carry either, so there is nothing to fill them from here even if
        // it were wanted.
        return new CriteriaMissVo(facts.uid(), null, null, code, detail);
    }

    private static Long epoch(java.util.Date d) {
        return d == null ? null : d.getTime();
    }
}
