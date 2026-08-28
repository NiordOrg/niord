package org.niord.core.publication.series.criteria;

import org.niord.core.publication.series.PublicationException;

/**
 * A criteria document could not be read.
 *
 * This is unchecked and it is thrown rather than swallowed on purpose. The
 * existing JSON converters in this repo log and return null; for print settings
 * that degrades a PDF, but for criteria it degrades to "no criteria at all",
 * which is indistinguishable from a legitimately empty query and resolves very
 * differently.
 */
public class CriteriaParseException extends PublicationException {

    public CriteriaParseException(String message, Throwable cause) {
        super("CRITERIA_INVALID", message, cause);
    }
}
