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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.niord.model.IJsonSerializable;

/**
 * The PUBLIC face of an issue: what a citation resolves to and what the
 * download site shows.
 */
public class PublicationIssueVo implements IJsonSerializable {

    private String publicId;

    private String seriesId;

    private Date created;

    private Date updated;

    private Date publicFrom;

    private Date publicTo;

    private Integer week;

    private Integer weekTo;

    private Integer year;

    private String edition;

    private List<PublicationIssueDescVo> descs = new ArrayList<>();

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

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public Date getUpdated() {
        return updated;
    }

    public void setUpdated(Date updated) {
        this.updated = updated;
    }

    public Date getPublicFrom() {
        return publicFrom;
    }

    public void setPublicFrom(Date publicFrom) {
        this.publicFrom = publicFrom;
    }

    public Date getPublicTo() {
        return publicTo;
    }

    public void setPublicTo(Date publicTo) {
        this.publicTo = publicTo;
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

    public String getEdition() {
        return edition;
    }

    public void setEdition(String edition) {
        this.edition = edition;
    }

    public List<PublicationIssueDescVo> getDescs() {
        return descs;
    }

    public void setDescs(List<PublicationIssueDescVo> descs) {
        this.descs = descs;
    }

}
