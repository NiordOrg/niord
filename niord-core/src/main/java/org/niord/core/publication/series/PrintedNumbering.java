/*
 * Copyright 2026 Danish Emergency Management Agency.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.niord.core.publication.series;

import org.niord.core.publication.series.resolve.IssueNaming;

/**
 * What an issue PRINTS where its derived numbers would go.
 *
 * TWO DIFFERENT FACTS, deliberately in two different columns. The week, the
 * closing week and the year are DERIVED from the cut-off and are numbers:
 * everything that computes reads them -- the ordering, the timeline strip, gap
 * detection, the archive -- and nothing a person types can reach them. The
 * LABELS are free text and are what goes on the page: "36+37", "36 &amp; 37", "36
 * og 37" are all things this estate has published, and none of them is a number.
 * Sharing one column between the two meant either the arithmetic broke or the
 * cover did.
 *
 * ONE RESOLUTION, HERE, because there are three printers and they must not
 * disagree: the name pattern, the file-name pattern (and the citation format
 * with them) all expand ${week} / ${weekTo} / ${year}, and the report gets the
 * same three values injected. A second copy of "label if there is one, else the
 * number" is how a document comes to be titled for two weeks and filed under
 * one.
 *
 * A label that is absent or blank means the derived number prints. That is the
 * state every issue is in until somebody says otherwise, and it is how "follow
 * the cut-off again" is expressed -- there is no third state to store.
 */
public final class PrintedNumbering {

    /**
     * As long as the column, and free text within it.
     *
     * No format check beyond this and the control-character rule below. The
     * ruling is explicit: a week may be written any way the publication writes
     * it, so a validator that understood "36+37" would refuse "36 og 37" the
     * first week somebody typed it.
     */
    public static final int MAX_LABEL = 64;

    /** The refusal for a label that is too long, or that is not one line of text. */
    public static final String INVALID = "NUMBERING_LABEL_INVALID";

    private PrintedNumbering() {
    }

    /** What ${week} prints, and what the report is handed: the label, else the derived week. */
    public static String printedWeek(PublicationIssue issue) {
        return printed(issue == null ? null : issue.getWeekLabel(),
                issue == null ? null : issue.getWeek());
    }

    /** What ${weekTo} prints: the label, else the derived closing week, else nothing. */
    public static String printedWeekTo(PublicationIssue issue) {
        return printed(issue == null ? null : issue.getWeekToLabel(),
                issue == null ? null : issue.getWeekTo());
    }

    /** What ${year} prints: the label, else the derived year. */
    public static String printedYear(PublicationIssue issue) {
        return printed(issue == null ? null : issue.getYearLabel(),
                issue == null ? null : issue.getYear());
    }

    /**
     * The labels this issue carries, in the form the pattern expander takes.
     *
     * {@link IssueNaming.Labels#NONE} where nothing is overridden, so a caller
     * never branches on whether to pass them.
     */
    public static IssueNaming.Labels labelsOf(PublicationIssue issue) {
        if (issue == null) {
            return IssueNaming.Labels.NONE;
        }
        IssueNaming.Labels labels = new IssueNaming.Labels(
                trimmed(issue.getWeekLabel()),
                trimmed(issue.getWeekToLabel()),
                trimmed(issue.getYearLabel()));
        return labels.any() ? labels : IssueNaming.Labels.NONE;
    }

    /** Whether anybody has written a printed number for this issue. */
    public static boolean isOverridden(PublicationIssue issue) {
        return labelsOf(issue).any();
    }

    /**
     * A label as it will be stored: trimmed, or null where it says nothing.
     *
     * A blank one is a CLEAR rather than a refusal, and that is the whole way
     * back: emptying the field returns the issue to the number its cut-off
     * derives. Refusing blank would leave a typed label with no way off the row
     * except retyping the derived number as text, which is the same drift by
     * hand.
     *
     * @throws IssueLifecycleService.TransitionRefusedException if the value is
     *         longer than the column or carries a control character -- a newline
     *         in a label reaches a PDF heading and a file name
     */
    public static String validated(String label) {
        if (label == null) {
            return null;
        }
        String trimmed = label.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_LABEL) {
            throw new IssueLifecycleService.TransitionRefusedException(INVALID,
                    "a printed number is at most " + MAX_LABEL + " characters; it goes on the cover "
                            + "of the document and into its file name");
        }
        for (int i = 0; i < trimmed.length(); i++) {
            if (Character.isISOControl(trimmed.charAt(i))) {
                throw new IssueLifecycleService.TransitionRefusedException(INVALID,
                        "a printed number is one line of text; a control character in it reaches a "
                                + "PDF heading and a published file name");
            }
        }
        return trimmed;
    }

    // ----------------------------------------------------------------- internals

    private static String printed(String label, Integer derived) {
        String trimmed = trimmed(label);
        if (trimmed != null) {
            return trimmed;
        }
        // Null rather than "null" or "": the caller injecting this into a report
        // is handing FreeMarker the same absence it has always been handed for an
        // issue with no cut-off to number by, and the token expander turns a
        // missing value into the empty string on its own.
        return derived == null ? null : String.valueOf(derived);
    }

    private static String trimmed(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
