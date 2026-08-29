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

import org.niord.model.IJsonSerializable;

import java.util.ArrayList;
import java.util.List;

/**
 * One series' issues, real and synthesized, in one sequence.
 *
 * The envelope exists because the rows alone cannot be read safely. A caller
 * handed a bare array has no way to tell an archive with no gaps from one that
 * was never examined, and both look like a list with no MISSING rows in it.
 */
public class IssueListResultVo implements IJsonSerializable {

    private String publicationSeriesId;

    /** Real issues and pseudo-rows merged, newest first. */
    private List<SystemPublicationIssueVo> data = new ArrayList<>();

    /**
     * REAL issues only.
     *
     * A pseudo-row describes a period nobody published, so counting it here would
     * report an archive as larger than it is -- and it is this number a pager
     * divides into pages and a heading renders as "23 udgaver".
     */
    private int total;

    /** Always present, so a reader is never left to infer why the rows look the way they do. */
    private GapDetectionVo gapDetection;

    /**
     * The number of MISSING rows -- and ABSENT, not zero, when detection did not run.
     *
     * Zero is a finding: somebody looked at every period and every one had an
     * issue. A closed gate is not a finding, and reporting it as 0 hands a screen
     * a clean bill of health nothing ever checked. Absent forces the caller to
     * read gapDetection, which is the only field that can answer the question.
     *
     * Stated on the field although IJsonSerializable already omits nulls. That
     * policy is a compactness convention; this one is a semantic guarantee a test
     * pins down, and it should not quietly depend on a convention staying put.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer gapCount;

    /**
     * The page size, present only when the caller asked for a page.
     *
     * Absent means the whole archive came back, which is a different statement
     * from "the page size happened to exceed the archive" -- a reader with a
     * `size` in hand can page and one without has nothing to page through.
     */
    private Integer size;

    /** Free text about the query, for a log or a diagnostic. */
    private String description;

    public String getPublicationSeriesId() {
        return publicationSeriesId;
    }

    public void setPublicationSeriesId(String publicationSeriesId) {
        this.publicationSeriesId = publicationSeriesId;
    }

    public List<SystemPublicationIssueVo> getData() {
        return data;
    }

    public void setData(List<SystemPublicationIssueVo> data) {
        this.data = data;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public GapDetectionVo getGapDetection() {
        return gapDetection;
    }

    public void setGapDetection(GapDetectionVo gapDetection) {
        this.gapDetection = gapDetection;
    }

    public Integer getGapCount() {
        return gapCount;
    }

    public void setGapCount(Integer gapCount) {
        this.gapCount = gapCount;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

}
