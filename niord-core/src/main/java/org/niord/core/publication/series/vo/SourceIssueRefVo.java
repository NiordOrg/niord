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
 * Which issue of the source series printed this member.
 *
 * The compilation's member list is drawn grouped by it -- a heading per source
 * issue, its rows beneath in the order that issue printed them -- so the row has
 * to carry enough to draw the heading and link to the issue behind it, and it
 * has to carry it without a lookup per row on a list that runs past a thousand.
 *
 * ONLY THE PUBLIC ID IS FROZEN. Everything else here is read live off the source
 * issue, because everything else can move: a weekly can be renamed, and its week
 * numbers are corrected during an import. When the source issue is gone
 * altogether -- a retired one is deletable -- the publicId stands alone and the
 * rest is null, which is the honest answer: the compilation still printed that
 * issue's rows, and nothing can say any more what it was called.
 */
public class SourceIssueRefVo implements IJsonSerializable {

    /** The frozen half: the issue this row was compiled from. */
    private String publicId;

    /** The series it belongs to, which is what a link to it is built from. */
    private String seriesId;

    private Integer week;

    private Integer weekTo;

    private Integer year;

    /**
     * The source issue's effective cut-off.
     *
     * On the heading beside the week number, because the week number alone does
     * not say which of two years an issue at a new-year boundary belongs to --
     * and the cut-off is the only thing that decides which annual compiled it.
     */
    private Date cutoff;

    /** The source issue's name in the language the list was asked for. */
    private String name;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
