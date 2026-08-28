package org.niord.core.publication.series.vo;

import org.niord.model.IJsonSerializable;

/**
 * What a frozen member's message looks like TODAY.
 *
 * Carried beside the frozen row only when the two disagree, so its presence is
 * itself the signal: a member list with no `current` anywhere is one nothing has
 * moved under. The frozen row is never rewritten from this -- the snapshot is
 * the record of what was published, and healing it would erase the very
 * divergence somebody needs to see before deciding whether to amend.
 *
 * `exists` is separate from the two values because a deleted message has no type
 * and no status to report, and reporting nulls for them would be indistinguishable
 * from a message whose fields happen to be empty.
 */
public class LiveMessageStateVo implements IJsonSerializable {

    /** Whether the message is still in the database at all. */
    private boolean exists;

    /**
     * Whether the message is still one the public may read.
     *
     * Derived from the live status rather than stored. A member that has been
     * withdrawn since the issue went out is the case an admin most wants
     * flagged, and it is not visible from the status name alone unless the
     * reader knows which statuses are public.
     */
    private boolean publiclyVisible;

    private String type;

    private String status;

    public boolean isExists() {
        return exists;
    }

    public void setExists(boolean exists) {
        this.exists = exists;
    }

    public boolean isPubliclyVisible() {
        return publiclyVisible;
    }

    public void setPubliclyVisible(boolean publiclyVisible) {
        this.publiclyVisible = publiclyVisible;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
