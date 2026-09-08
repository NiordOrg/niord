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

package org.niord.core.publication.series.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import org.niord.core.publication.series.PublishChecklistService;

import java.util.Map;

/**
 * One release-rail row on the wire.
 *
 * It exists because the rail row had been mapped into the response by hand, key
 * by key, in the endpoint -- so a field added to the record was carried by
 * nothing until somebody remembered to add a line, and the endpoint went on
 * answering one field short with no failure anywhere to say so. A client reading
 * the missing key gets `undefined`, which for `applicable` means every row counts
 * and for `passed` means every row fails.
 *
 * DELIBERATELY NOT an {@code IJsonSerializable}. That interface suppresses null
 * properties, and `acknowledgeCode` is null on thirteen of the fourteen rows: the
 * client reads it as a value -- "this row cannot be acknowledged" -- and dropping
 * the key would change the wire shape the publish dialog is built on. The order
 * is written out for the same reason: it is the order the hand-built map emitted.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonPropertyOrder({"code", "severity", "passed", "applicable", "acknowledgeable",
        "acknowledgeCode", "detail", "detailCode", "detailParams"})
public class PublishCheckRowVo {

    private String code;
    private String severity;
    private boolean passed;

    /**
     * Whether this issue can be in the condition the row describes at all.
     *
     * Every row is still sent -- a client that renders only what it received
     * cannot tell "passed" from "does not exist" -- but a check this issue does
     * not raise is not one of the answers the caller is counting.
     */
    private boolean applicable;

    private boolean acknowledgeable;

    /**
     * The warning code the publish gate compares against, said by the row rather
     * than mapped by every client. The rail names a condition and the
     * acknowledgement travels as the resolver's warning code, and the two are
     * deliberately different strings -- a client translating one into the other by
     * hand gets a refusal for a code nobody ticked.
     */
    private String acknowledgeCode;

    /**
     * The English sentence, for readers with nobody to translate for them.
     *
     * Kept beside {@link #detailCode}, not replaced by it. An API caller and a log
     * have no dictionary, and the publish gate builds its refusal sentences out of
     * this string directly -- so removing it would turn a readable refusal into a
     * bare code at the one moment somebody is trying to release.
     */
    private String detail;

    /**
     * The same statement as a key a client translates.
     *
     * ONE PER SENTENCE VARIANT, not one per row. A row says several different
     * things depending on what it found -- MEMBER_LIMIT reports a count against a
     * ceiling, or says the question does not arise -- and "the MEMBER_LIMIT row"
     * is not a sentence anybody can translate. Rows that do not apply carry
     * {@code NOT_APPLICABLE.<REASON>}, so the reason is translated too rather than
     * sitting in English under a translated heading.
     */
    private String detailCode;

    /**
     * The values the sentence interpolates, typed and unformatted.
     *
     * Counts as numbers, instants as epoch milliseconds with the zone beside them
     * -- never a rendered date. The client formats every other instant on the
     * screen in the session's own zone and locale, and a date pre-rendered here
     * would be the one that does not match. Empty rather than absent where the
     * sentence takes no values, so a client can interpolate unconditionally.
     */
    private Map<String, Object> detailParams = Map.of();

    /** The record as the client reads it, component for component. */
    public static PublishCheckRowVo of(PublishChecklistService.CheckRow row) {
        PublishCheckRowVo vo = new PublishCheckRowVo();
        vo.setCode(row.code());
        vo.setSeverity(row.severity() == null ? null : row.severity().name());
        vo.setPassed(row.passed());
        vo.setApplicable(row.applicable());
        vo.setAcknowledgeable(row.acknowledgeable());
        vo.setAcknowledgeCode(row.acknowledgeCode());
        vo.setDetail(row.detail());
        vo.setDetailCode(row.detailCode());
        vo.setDetailParams(row.detailParams() == null ? Map.of() : row.detailParams());
        return vo;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public boolean isPassed() {
        return passed;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    public boolean isApplicable() {
        return applicable;
    }

    public void setApplicable(boolean applicable) {
        this.applicable = applicable;
    }

    public boolean isAcknowledgeable() {
        return acknowledgeable;
    }

    public void setAcknowledgeable(boolean acknowledgeable) {
        this.acknowledgeable = acknowledgeable;
    }

    public String getAcknowledgeCode() {
        return acknowledgeCode;
    }

    public void setAcknowledgeCode(String acknowledgeCode) {
        this.acknowledgeCode = acknowledgeCode;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getDetailCode() {
        return detailCode;
    }

    public void setDetailCode(String detailCode) {
        this.detailCode = detailCode;
    }

    public Map<String, Object> getDetailParams() {
        return detailParams;
    }

    public void setDetailParams(Map<String, Object> detailParams) {
        this.detailParams = detailParams == null ? Map.of() : detailParams;
    }
}
