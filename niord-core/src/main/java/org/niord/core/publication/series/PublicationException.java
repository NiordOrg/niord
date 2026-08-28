package org.niord.core.publication.series;

import java.util.List;

/**
 * The one supertype of every failure the publication API turns into a coded
 * response.
 *
 * It exists so the JAX-RS provider can be declared against THIS type instead of
 * against RuntimeException. A provider registered for RuntimeException is
 * registered for every runtime failure in the whole deployment: JAX-RS picks the
 * mapper whose type parameter is the nearest supertype of what was thrown, and
 * with no other mapper present that is this one for literally everything. The
 * previous shape rethrew what it did not recognise, which escapes the container's
 * own handling -- so a bare {@code WebApplicationException(403)} raised anywhere
 * outside this feature came back as a 500, and so did every unmatched route.
 *
 * Two things travel with the failure and neither may be invented at the throw
 * site. The CODE is what a client branches on; a client that has to match on
 * message text breaks the first time somebody improves the wording. The FIELD
 * ERRORS are the same information in the shape a form can act on -- "seven rules
 * fail" cannot be rendered against a control, and the rules already say which
 * field each belongs to.
 */
public abstract class PublicationException extends RuntimeException {

    private final String code;

    private final List<SeriesValidator.FieldError> fieldErrors;

    protected PublicationException(String code, String message) {
        this(code, message, null, List.of());
    }

    protected PublicationException(String code, String message, Throwable cause) {
        this(code, message, cause, List.of());
    }

    protected PublicationException(String code, String message,
                                   List<SeriesValidator.FieldError> fieldErrors) {
        this(code, message, null, fieldErrors);
    }

    protected PublicationException(String code, String message, Throwable cause,
                                   List<SeriesValidator.FieldError> fieldErrors) {
        super(message, cause);
        this.code = code;
        this.fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    /** The wire code. Stable, and the only thing a client is expected to branch on. */
    public String code() {
        return code;
    }

    /** Which fields failed, empty when the failure is not about a form. */
    public List<SeriesValidator.FieldError> fieldErrors() {
        return fieldErrors;
    }
}
