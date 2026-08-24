package org.niord.core.publication.series.vo;

import org.niord.model.IJsonSerializable;

/**
 * Whether gap detection ran, and why.
 *
 * It is an object rather than a bare boolean because "no gaps were found" and
 * "nobody looked for gaps" are different answers that a boolean plus a count
 * renders identically. Every imported series is DRAFT, so on today's estate the
 * gate is closed for all twenty of them -- a screen reading a count alone would
 * report a clean bill of health for an archive nothing examined.
 *
 * reasonCode is what a caller switches on and a UI translates. reason is the
 * same fact in prose, for a log or a diagnostic; a client matching on the
 * sentence would break the first time the sentence is improved.
 */
public class GapDetectionVo implements IJsonSerializable {

    private boolean enabled;

    private String reasonCode;

    private String reason;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

}
