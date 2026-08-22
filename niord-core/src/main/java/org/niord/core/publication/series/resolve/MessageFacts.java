package org.niord.core.publication.series.resolve;

import org.niord.model.message.Status;
import org.niord.model.message.Type;

import java.util.Date;

/**
 * The facts about one message that membership depends on.
 *
 * Deliberately a plain value with no entity behind it. Two of these fields are
 * mutable in production -- publishDateFrom is editor-writable and nullable even
 * once published, and type is mutable and unversioned -- so a frozen member
 * snapshot has to record what they were at freeze time. A structure that could
 * re-read them later would not be reproducible.
 *
 * Keyed on uid, never shortId: shortId is not declared unique and nothing
 * prevents reuse, so a shortId-keyed comparison can report two member sets as
 * identical while they differ.
 */
public record MessageFacts(
        String uid,
        Date publishDateFrom,
        Date publishDateTo,
        Status status,
        Type type,
        String messageSeriesId) {
}
