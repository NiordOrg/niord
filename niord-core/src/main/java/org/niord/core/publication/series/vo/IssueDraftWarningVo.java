package org.niord.core.publication.series.vo;

/**
 * One thing worth saying about a draft issue, before anybody creates it.
 *
 * A CODE and a SENTENCE, together. The code is what a client switches on and
 * what a translation keys off; the sentence is what an admin reads when nobody
 * has written a translation yet, and what appears in a log. A warning that
 * carried only a code would be untranslatable text in a foreign language the
 * first time it surfaced somewhere nobody expected.
 *
 * None of these refuses anything. A draft that could not be created at all is a
 * refusal with an error code; these are the facts an admin should see before
 * pressing create -- that the interval chains off nothing, that the series is
 * not active, that an in-force series has no lower bound to show.
 */
public record IssueDraftWarningVo(String code, String message) {
}
