package org.niord.core.publication.series.resolve;

import java.util.List;

/**
 * One warning about a resolution, with the messages it concerns.
 *
 * Grouped by code rather than emitted per message: a curator needs to know that
 * eleven members are cancelled-but-alive, not to read eleven identical lines.
 */
public record ResolutionWarningVo(
        ResolutionWarningCode code,
        List<String> messageUids,
        int count,
        boolean acknowledgeable) {

    public static ResolutionWarningVo of(ResolutionWarningCode code, List<String> messageUids) {
        return new ResolutionWarningVo(code, List.copyOf(messageUids), messageUids.size(), code.isAcknowledgeable());
    }
}
