package org.niord.core.publication.series.vo;

import org.niord.model.IJsonSerializable;

import java.util.Date;

/**
 * Who decided this member was in or out, when, and why.
 *
 * The why-line on a curated row is unreadable without it: "manuelt tilføjet"
 * says a human overruled the query and nothing else, and the question anybody
 * asks next -- who, and on what grounds -- is answerable only from the override
 * row. It is carried here rather than left to a second request per member,
 * because a panel that needs one call per row is a panel that renders the
 * reasons late or not at all.
 *
 * ADMIN TIER. The editor shape of a member list stops at "this was curated"; the
 * author and the reason are a step further in, which is why the override is a
 * foreign key rather than columns copied onto the member -- the redaction is one
 * decision in one place.
 */
public class MemberCurationVo implements IJsonSerializable {

    /** INCLUDE or EXCLUDE: which way the decision went. */
    private String kind;

    /** The curator, by display name where there is one and by username otherwise. */
    private String author;

    private Date at;

    private String reason;

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public Date getAt() {
        return at;
    }

    public void setAt(Date at) {
        this.at = at;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
