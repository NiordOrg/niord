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
    private int issuesImported;
    private Map<String, Integer> issuesByStatus = new LinkedHashMap<>();
    private Map<String, Integer> issuesByFilterShape = new LinkedHashMap<>();
    private List<ProblemVo> problems = new ArrayList<>();

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

    public Map<String, Integer> getIssuesByStatus() {
        return issuesByStatus;
    }

    public void setIssuesByStatus(Map<String, Integer> issuesByStatus) {
        this.issuesByStatus = issuesByStatus == null ? new LinkedHashMap<>() : issuesByStatus;
    }

    public Map<String, Integer> getIssuesByFilterShape() {
        return issuesByFilterShape;
    }

    public void setIssuesByFilterShape(Map<String, Integer> issuesByFilterShape) {
        this.issuesByFilterShape = issuesByFilterShape == null ? new LinkedHashMap<>() : issuesByFilterShape;
    }

    public List<ProblemVo> getProblems() {
        return problems;
    }

    public void setProblems(List<ProblemVo> problems) {
        this.problems = problems == null ? new ArrayList<>() : problems;
    }
}
