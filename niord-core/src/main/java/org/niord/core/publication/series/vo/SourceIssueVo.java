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

import org.niord.model.IJsonSerializable;

import java.util.Date;

/**
 * One source-series issue as the compilation's period sees it.
 *
 * The sources panel is a compilation's counterpart to the omissions panel: a
 * query-backed issue is asked what its criteria rejected, and a compilation has
 * nothing to reject -- the question that matters to it is whether the period it
 * covers is actually finished. So the panel lists every issue of the source
 * series that falls inside the period, and marks the two kinds that make the
 * answer "not yet".
 *
 * THREE KINDS OF ROW, told apart by {@link #status}: a PUBLISHED issue, whose
 * rows are in the union; an OPEN one, which has no frozen rows at all and
 * therefore contributes nothing; and the synthesised `MISSING` stretch, which
 * names no issue because there is none -- its interval is the part of the period
 * nothing covers. A retired issue is not listed: withdrawing it said its
 * contents should not stand, and the compilation is not short of anything while
 * its replacement is published.
 */
public class SourceIssueVo implements IJsonSerializable {

    /** Null on a synthesised MISSING row: there is no issue to name. */
    private String publicId;

    /** The source series, so a row can be linked to the issue behind it. */
    private String seriesId;

    private Integer week;

    private Integer weekTo;

    private Integer year;

    /**
     * The effective cut-off, which is what decides the row's place in the period.
     *
     * On a MISSING row it is where the uncovered stretch ends -- the next
     * published issue's period start -- so the panel reads in one order whatever
     * kind of row it is showing.
     */
    private Date cutoff;

    /** PUBLISHED, OPEN, or MISSING for a synthesised stretch. */
    private String status;

    /** What the source issue printed; null on an OPEN or MISSING row. */
    private Integer memberCount;

    /** The issue's name in the language the screen was asked for. */
    private String name;

    /** Where the row's own period opens; on a MISSING row, where the gap opens. */
    private Date intervalFrom;

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    public String getSeriesId() {
        return seriesId;
    }

    public void setSeriesId(String seriesId) {
        this.seriesId = seriesId;
    }

    public Integer getWeek() {
        return week;
    }

    public void setWeek(Integer week) {
        this.week = week;
    }

    public Integer getWeekTo() {
        return weekTo;
    }

    public void setWeekTo(Integer weekTo) {
        this.weekTo = weekTo;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public Date getCutoff() {
        return cutoff;
    }

    public void setCutoff(Date cutoff) {
        this.cutoff = cutoff;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(Integer memberCount) {
        this.memberCount = memberCount;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Date getIntervalFrom() {
        return intervalFrom;
    }

    public void setIntervalFrom(Date intervalFrom) {
        this.intervalFrom = intervalFrom;
    }
}
