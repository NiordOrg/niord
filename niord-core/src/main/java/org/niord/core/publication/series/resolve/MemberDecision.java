package org.niord.core.publication.series.resolve;

/**
 * Whether a message is a member, and why.
 *
 * The reason costs nothing extra -- the UI renders a "why is this here" line
 * per member regardless -- and it is what makes an exclusion reportable instead
 * of invisible.
 */
public record MemberDecision(String uid, boolean member, MembershipReason reason) {

    static MemberDecision included(String uid, MembershipReason reason) {
        return new MemberDecision(uid, true, reason);
    }

    static MemberDecision excluded(String uid, MembershipReason reason) {
        return new MemberDecision(uid, false, reason);
    }

    /** True when the message was excluded for a reason worth surfacing rather than assuming. */
    public boolean isReportableOmission() {
        return !member && reason == MembershipReason.NO_PUBLISH_DATE;
    }
}
