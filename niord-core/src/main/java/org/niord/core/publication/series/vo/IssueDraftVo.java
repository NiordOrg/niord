package org.niord.core.publication.series.vo;

import java.util.ArrayList;
import java.util.List;

/**
 * S12. An issue that does not exist yet, in the shape of one that does.
 *
 * It EXTENDS the issue's system shape rather than declaring a parallel set of
 * fields. Three screens ask for a draft -- "＋ Ny udgave", the retro-create
 * prefill, and the live preview on a gap row -- and each of them renders an
 * issue afterwards. A separate type would mean two renderers for one thing, and
 * the second one is where the interval bound and its provenance marker stop
 * agreeing with the list the row came from.
 *
 * NOTHING HERE IS PERSISTED. The draft is computed on every request from the
 * series and its issues; no row is written, no member is resolved into an issue,
 * and the counting probe below reads only.
 *
 * The three fields it adds are the ones a saved issue has no need of: what the
 * criteria would select over this interval, which issue the interval chains off,
 * and what an admin should know before pressing create.
 */
public class IssueDraftVo extends SystemPublicationIssueVo {

    /**
     * What the series' criteria would select over this interval, right now.
     *
     * The prototype's "11 ville matche". Null rather than 0 when no count could
     * be taken -- a series with no membership at all, or a criteria document that
     * does not resolve -- because 0 is a finding about the interval and null is
     * the absence of one, and a screen that showed them alike would tell an admin
     * their week is empty when nothing was ever counted.
     */
    private Integer wouldMatchCount;

    /**
     * The issue this draft's interval opens at the close of.
     *
     * Null at the head of the chain, and null when the caller typed a bound that
     * matches no issue's close -- which is exactly when the chained warning is
     * raised, so the two answers cannot disagree.
     */
    private String chainedFromPublicId;

    /** What an admin should know before creating this issue. Never a refusal. */
    private List<IssueDraftWarningVo> warnings = new ArrayList<>();

    public Integer getWouldMatchCount() {
        return wouldMatchCount;
    }

    public void setWouldMatchCount(Integer wouldMatchCount) {
        this.wouldMatchCount = wouldMatchCount;
    }

    public String getChainedFromPublicId() {
        return chainedFromPublicId;
    }

    public void setChainedFromPublicId(String chainedFromPublicId) {
        this.chainedFromPublicId = chainedFromPublicId;
    }

    public List<IssueDraftWarningVo> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<IssueDraftWarningVo> warnings) {
        this.warnings = warnings;
    }
}
