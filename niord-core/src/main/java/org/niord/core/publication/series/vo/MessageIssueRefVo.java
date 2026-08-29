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

import org.niord.core.publication.series.IssueStatus;
import org.niord.model.IJsonSerializable;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One issue a message appears in, as the message editor needs to show it.
 *
 * A deliberately narrow shape rather than a reuse of the issue VOs. Those carry
 * the editing surface -- name-suggestion patterns, file-name patterns, reference
 * formats -- and this row is read by anyone who may open a message, which is a
 * wider audience than the people who administer series. Sending a series' naming
 * patterns to a message editor gives away part of the admin surface for nothing:
 * none of it can be rendered here.
 *
 * ONE ROW IS ONE SERIES on one basis, not one issue -- see issueCount. The issue
 * fields describe the MOST RECENT issue in that group, which is the one an editor
 * asking "has this gone out" means.
 *
 * MEMBERSHIP is the field that carries the meaning. FROZEN is a fact recorded at
 * publication; LIVE is what an open issue would contain if it closed now. A row
 * that did not say which one it was would let an editor read a prediction as a
 * record, which is precisely the mistake this screen exists to prevent. It is
 * also why membership is part of the grouping key rather than collapsed into the
 * series: a weekly series normally holds both at once -- published in last week's
 * issue, pending in this week's -- and merging them would drop one of the two
 * facts an editor came for.
 *
 * Names are per language rather than resolved server-side, matching how every
 * other localizable payload in this API works: one response serves every UI
 * language, and the client picks. The issue's own name is usually the whole
 * label an editor needs -- "EfS uge 29, 2026" -- but the series name is carried
 * too, because a series whose pattern renders only a period leaves the issue
 * name unable to say what it is an issue OF.
 */
public class MessageIssueRefVo implements IJsonSerializable {

    /** How the message came to be in the issue. */
    public enum Membership {
        /** Recorded at publication. */
        FROZEN,
        /** Resolved at read time: what this open issue would contain if it closed now. */
        LIVE
    }

    private String issuePublicId;

    private String seriesId;

    private IssueStatus status;

    private Membership membership;

    /**
     * How many issues of this series the message is in, on this basis.
     *
     * One row is one SERIES, not one issue, and this is why. An
     * IN_FORCE_AT_CUTOFF series re-lists every message still in force in every
     * edition, so a notice in force for two years is a member of a hundred-odd
     * weekly issues of it -- all saying the same thing. Rendered one per row they
     * bury the one row an editor is looking for, and the response carries a
     * hundred near-identical objects to do it.
     *
     * The count is kept because it is not noise: it says at a glance whether this
     * is a recurring listing or a single appearance.
     */
    private int issueCount;

    private Date intervalFrom;

    private Date intervalTo;

    private Date publishedAt;

    /** lang -> the issue's name. */
    private Map<String, String> names = new LinkedHashMap<>();

    /** lang -> the series' name. */
    private Map<String, String> seriesNames = new LinkedHashMap<>();

    /** lang -> the published document's link, where one exists. */
    private Map<String, String> links = new LinkedHashMap<>();

    public String getIssuePublicId() {
        return issuePublicId;
    }

    public void setIssuePublicId(String issuePublicId) {
        this.issuePublicId = issuePublicId;
    }

    public String getSeriesId() {
        return seriesId;
    }

    public void setSeriesId(String seriesId) {
        this.seriesId = seriesId;
    }

    public IssueStatus getStatus() {
        return status;
    }

    public void setStatus(IssueStatus status) {
        this.status = status;
    }

    public Membership getMembership() {
        return membership;
    }

    public void setMembership(Membership membership) {
        this.membership = membership;
    }

    public int getIssueCount() {
        return issueCount;
    }

    public void setIssueCount(int issueCount) {
        this.issueCount = issueCount;
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

    public Date getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Date publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Map<String, String> getNames() {
        return names;
    }

    public void setNames(Map<String, String> names) {
        this.names = names;
    }

    public Map<String, String> getSeriesNames() {
        return seriesNames;
    }

    public void setSeriesNames(Map<String, String> seriesNames) {
        this.seriesNames = seriesNames;
    }

    public Map<String, String> getLinks() {
        return links;
    }

    public void setLinks(Map<String, String> links) {
        this.links = links;
    }
}
