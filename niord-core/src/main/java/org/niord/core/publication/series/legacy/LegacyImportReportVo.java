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

package org.niord.core.publication.series.legacy;

import org.niord.model.IJsonSerializable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What an import run did, or would do.
 *
 * The dry run and the real run return the SAME shape, so that an admin reading a
 * dry run is reading exactly what the real run will report. A dry run with a
 * different, thinner report would be a different thing pretending to be a
 * preview.
 */
public class LegacyImportReportVo implements IJsonSerializable {

    /** One reason the import cannot proceed, naming the row it came from. */
    public static class ProblemVo implements IJsonSerializable {

        private String code;
        private String publicationId;
        private String title;
        private String detail;

        public ProblemVo() {
        }

        public ProblemVo(String code, String publicationId, String title, String detail) {
            this.code = code;
            this.publicationId = publicationId;
            this.title = title;
            this.detail = detail;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getPublicationId() {
            return publicationId;
        }

        public void setPublicationId(String publicationId) {
            this.publicationId = publicationId;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDetail() {
            return detail;
        }

        public void setDetail(String detail) {
            this.detail = detail;
        }
    }

    private boolean dryRun;
    private boolean wouldSucceed;
    private int categoriesSeen;
    private int categoriesCreated;
    private int seriesImported;

    /** Query-backed series that got a translated criteria document. */
    private int seriesCriteriaWritten;

    /**
     * Query-backed series left WITHOUT one, because the archive showed no message
     * series to scope them by. They will refuse to activate until somebody
     * authors the criteria, which is the honest outcome -- an unscoped document
     * would resolve over every message in the system.
     */
    private int seriesWithoutCriteria;

    /**
     * Series that will land with no domain, and so no timezone.
     *
     * A count rather than a problem: the consequence is that the series stays
     * DRAFT and S-20 refuses to activate it with a message that says why, which
     * is proportionate. Refusing 1,077 issues over one absent domain is not.
     *
     * Zero is the finding. A silent log is indistinguishable from nothing having
     * been checked, and this is the number that says how many publications are
     * not yet activatable.
     */
    private int seriesWithoutDomain;
    private int issuesImported;

    /**
     * Tag rows the import left out because they post-date the issue by too much.
     *
     * The tag goes on being edited after the document has gone out, so it holds
     * notices the printed page never carried. They are dropped rather than frozen
     * -- see LateMemberRule -- and the number is the only place that drop is
     * visible from outside, which is why it is reported even when it is zero: an
     * absent number and a zero read alike, and a run that discarded nothing has to
     * be tellable from one whose report was never written.
     */
    private int membersLeftOutPublishedAfterIssue;

    /**
     * Annuals the import OPENED on a compiled series, rather than read anywhere.
     *
     * The archive stops at the last hand-assembled edition, and the years since
     * carry no issue at all -- which reads as a dormant series, so gap detection
     * switches off and the missing years are not even reported as missing. These
     * are the periods the import opens so somebody does not have to open them by
     * hand in the go-live window.
     *
     * Written whatever it turns out to be. Zero is the finding an operator ticks:
     * it says the archive was already up to date, which is not the same thing as
     * the rule never having run.
     */
    private int compilationIssuesCreated;

    private Map<String, Integer> issuesByStatus = new LinkedHashMap<>();
    private Map<String, Integer> issuesByCutoffSource = new LinkedHashMap<>();
    private List<ProblemVo> problems = new ArrayList<>();

    /**
     * Rows the import carried through with a decision somebody should see.
     *
     * Deliberately NOT problems: a problem refuses the whole estate, and these
     * are rows that import correctly while carrying less than the archive would
     * have liked. The distinction matters because the alternative to naming them
     * here is not naming them at all -- the import runs once, in one window, and
     * a row nobody was told about is a row nobody goes back to.
     */
    private List<ProblemVo> notes = new ArrayList<>();

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public boolean isWouldSucceed() {
        return wouldSucceed;
    }

    public void setWouldSucceed(boolean wouldSucceed) {
        this.wouldSucceed = wouldSucceed;
    }

    public int getCategoriesSeen() {
        return categoriesSeen;
    }

    public void setCategoriesSeen(int categoriesSeen) {
        this.categoriesSeen = categoriesSeen;
    }

    public int getCategoriesCreated() {
        return categoriesCreated;
    }

    public void setCategoriesCreated(int categoriesCreated) {
        this.categoriesCreated = categoriesCreated;
    }

    public int getSeriesImported() {
        return seriesImported;
    }

    public void setSeriesImported(int seriesImported) {
        this.seriesImported = seriesImported;
    }

    public int getIssuesImported() {
        return issuesImported;
    }

    public void setIssuesImported(int issuesImported) {
        this.issuesImported = issuesImported;
    }

    public int getMembersLeftOutPublishedAfterIssue() {
        return membersLeftOutPublishedAfterIssue;
    }

    public void setMembersLeftOutPublishedAfterIssue(int membersLeftOutPublishedAfterIssue) {
        this.membersLeftOutPublishedAfterIssue = membersLeftOutPublishedAfterIssue;
    }

    public int getCompilationIssuesCreated() {
        return compilationIssuesCreated;
    }

    public void setCompilationIssuesCreated(int compilationIssuesCreated) {
        this.compilationIssuesCreated = compilationIssuesCreated;
    }

    public Map<String, Integer> getIssuesByStatus() {
        return issuesByStatus;
    }

    public void setIssuesByStatus(Map<String, Integer> issuesByStatus) {
        this.issuesByStatus = issuesByStatus == null ? new LinkedHashMap<>() : issuesByStatus;
    }

    public Map<String, Integer> getIssuesByCutoffSource() {
        return issuesByCutoffSource;
    }

    public void setIssuesByCutoffSource(Map<String, Integer> issuesByCutoffSource) {
        this.issuesByCutoffSource = issuesByCutoffSource == null ? new LinkedHashMap<>() : issuesByCutoffSource;
    }

    public int getSeriesWithoutDomain() {
        return seriesWithoutDomain;
    }

    public void setSeriesWithoutDomain(int seriesWithoutDomain) {
        this.seriesWithoutDomain = seriesWithoutDomain;
    }

    public List<ProblemVo> getProblems() {
        return problems;
    }

    public void setProblems(List<ProblemVo> problems) {
        this.problems = problems == null ? new ArrayList<>() : problems;
    }

    public List<ProblemVo> getNotes() {
        return notes;
    }

    public void setNotes(List<ProblemVo> notes) {
        this.notes = notes == null ? new ArrayList<>() : notes;
    }

    public int getSeriesCriteriaWritten() {
        return seriesCriteriaWritten;
    }

    public void setSeriesCriteriaWritten(int seriesCriteriaWritten) {
        this.seriesCriteriaWritten = seriesCriteriaWritten;
    }

    public int getSeriesWithoutCriteria() {
        return seriesWithoutCriteria;
    }

    public void setSeriesWithoutCriteria(int seriesWithoutCriteria) {
        this.seriesWithoutCriteria = seriesWithoutCriteria;
    }

}
