/*
 * Copyright 2026 Danish Maritime Authority.
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
 * One cell of a series' recent-periods strip.
 *
 * A period, not an issue: the cells that matter most are the ones with no issue
 * behind them. `publicId` is absent on those, which is how a client tells a real
 * row from a synthesized one -- the same rule the issue list's pseudo-rows
 * follow, because they are produced by the same synthesizer and a second
 * producer of the same fact drifts from the first.
 */
public class IssueTimelineRowVo implements IJsonSerializable {

    /**
     * What to write in the cell.
     *
     * A real issue is called what it is called -- somebody may have renamed it.
     * A synthesized cell is named the way the series would name that period,
     * derived from the week the period CLOSES in, never the week it opens in:
     * every weekly period runs across two ISO weeks, so deriving from the start
     * would label every cell as a double week.
     */
    private String label;

    private Integer week;

    private Integer year;

    /** OPEN, PUBLISHED or RETIRED for a real row; MISSING or UPCOMING for a synthesized one. */
    private String computedStatus;

    /**
     * The member count, where the issue has membership semantics.
     *
     * Null on a synthesized cell -- a period nobody published has no membership,
     * and a zero would read as "resolved, and empty", which is a different claim.
     * Null too for a publication that resolves nothing at all.
     */
    private Integer memberCount;

    /** Absent on a synthesized cell. Its absence is what identifies one. */
    private String publicId;

    private Date intervalFrom;

    private Date intervalTo;

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Integer getWeek() {
        return week;
    }

    public void setWeek(Integer week) {
        this.week = week;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public String getComputedStatus() {
        return computedStatus;
    }

    public void setComputedStatus(String computedStatus) {
        this.computedStatus = computedStatus;
    }

    public Integer getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(Integer memberCount) {
        this.memberCount = memberCount;
    }

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    public Date getIntervalFrom() {
        return intervalFrom;
    }

    public void setIntervalFrom(Date intervalFrom) {
        this.intervalFrom = intervalFrom;
    }

    public Date getIntervalTo() {
        return intervalTo;
    }

    public void setIntervalTo(Date intervalTo) {
        this.intervalTo = intervalTo;
    }
}
