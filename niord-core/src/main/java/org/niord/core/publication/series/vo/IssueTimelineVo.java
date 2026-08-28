package org.niord.core.publication.series.vo;

import org.niord.model.IJsonSerializable;

import java.util.ArrayList;
import java.util.List;

/**
 * One series' strip of recent periods.
 *
 * A group per requested series, in ONE response. Built from the issue list
 * instead, the landing screen is a request per series -- roughly sixty on the
 * production estate -- and it still could not render a missing period, because
 * gap synthesis needs exactly one series and the issue list only offers it when
 * one is named.
 *
 * The gate is reported alongside the rows for the same reason it is on the issue
 * list: a strip with no MISSING cell in it and a strip nobody examined for gaps
 * look identical, and every imported series starts out in a state where nothing
 * examines it.
 */
public class IssueTimelineVo implements IJsonSerializable {

    private String publicationSeriesId;

    /** Newest period first, the order the strip reads in. */
    private List<IssueTimelineRowVo> rows = new ArrayList<>();

    /** Whether periods were examined for gaps at all, and why not where they were not. */
    private GapDetectionVo gapDetection;

    public String getPublicationSeriesId() {
        return publicationSeriesId;
    }

    public void setPublicationSeriesId(String publicationSeriesId) {
        this.publicationSeriesId = publicationSeriesId;
    }

    public List<IssueTimelineRowVo> getRows() {
        return rows;
    }

    public void setRows(List<IssueTimelineRowVo> rows) {
        this.rows = rows;
    }

    public GapDetectionVo getGapDetection() {
        return gapDetection;
    }

    public void setGapDetection(GapDetectionVo gapDetection) {
        this.gapDetection = gapDetection;
    }
}
