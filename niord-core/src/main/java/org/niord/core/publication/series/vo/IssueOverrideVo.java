package org.niord.core.publication.series.vo;

/**
 * One standing curation decision on an issue, whichever way it went.
 *
 * A curation decision plus the message it is about, which is why it is the
 * member row's curation block with a uid on it rather than a second shape: the
 * why-line an INCLUDE renders and the reason an EXCLUDE was taken are the same
 * facts and must not be able to disagree.
 *
 * It exists as a list of its own because an EXCLUDE never appears in the member
 * list -- that is what excluding means -- so there is no row for a "withdraw
 * this exclusion" affordance to hang off. Without this the exclusions have to be
 * reconstructed from the audit trail, which records what HAPPENED rather than
 * what STANDS: an exclude followed by a clear leaves two entries and no
 * decision, and reading it as one is how a withdrawn exclusion comes back to
 * life on a screen.
 */
public class IssueOverrideVo extends MemberCurationVo {

    private String messageUid;

    public String getMessageUid() {
        return messageUid;
    }

    public void setMessageUid(String messageUid) {
        this.messageUid = messageUid;
    }
}
