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

import java.util.Date;

/**
 * What one language's document is called.
 *
 * THE PATTERN NO LONGER ALWAYS WINS. The series names the file -- "EfS-PT-Uge-${week}-${year}.pdf"
 * -- and for the ordinary week that is exactly right: the name renders the
 * period, and the period is what the file is about. But a hand-set name is a
 * decision, and until now there was nowhere to make it: the pattern was expanded
 * on every publish and the language's own file name was consulted only when the
 * series named no pattern at all. An edition that had to be filed under
 * something else could not be.
 *
 * So the order is: the name somebody set for THIS issue, then the series'
 * pattern, then whatever the language already carries, then the issue's public
 * id. Always a PDF, because that is what is written.
 *
 * ONE RESOLUTION, because three callers ask: the publish writes the file, the
 * preview names its bytes from the same rule so a preview and a release cannot
 * disagree about the address, and the editor screen shows what the file WILL be
 * called before anything is published. A second copy would let the screen
 * promise one name and the release write another.
 */
public final class IssueFileNaming {

    private IssueFileNaming() {
    }

    /**
     * The name the SERIES' pattern produces for this language, or null where it
     * names no pattern.
     *
     * Throws where the pattern carries a token that does not exist. That refusal
     * is load-bearing on the publish path -- production serves a real PDF at
     * .../Skydeomraader-%24%7Byear%7D.pdf because an unexpanded token once
     * reached a file name and then a URL -- so it is not softened here. A caller
     * that is merely showing a suggestion uses {@link #suggestedQuietly}.
     */
    public static String suggested(PublicationIssue issue, PublicationSeries series,
                                   PublicationIssueDesc desc, Date cutoff) {
        if (issue == null || series == null || desc == null || cutoff == null) {
            return null;
        }
        String pattern = null;
        for (PublicationSeriesDesc sd : series.getDescs()) {
            if (desc.getLang() != null && desc.getLang().equals(sd.getLang())) {
                pattern = sd.getFileNamePattern();
            }
        }
        if (pattern == null || pattern.isBlank()) {
            return null;
        }
        IssueNaming.Numbers numbers = IssueNaming.derive(cutoff, issue.getIntervalFrom(),
                series.cutoffZone(), IssueShape.editionOf(issue), IssueShape.yearBasisOf(series));
        // The printed labels ride along, so a typed "36+37" reaches the file name
        // as well as the title. They are the same decision.
        String expanded = IssueNaming.expand(pattern, numbers, PrintedNumbering.labelsOf(issue));
        return expanded == null || expanded.isBlank() ? null : withPdf(expanded);
    }

    /** The same, for a screen: a pattern that cannot expand shows no suggestion rather than failing a read. */
    public static String suggestedQuietly(PublicationIssue issue, PublicationSeries series,
                                          PublicationIssueDesc desc, Date cutoff) {
        try {
            return suggested(issue, series, desc, cutoff);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * The official file name for one language.
     *
     * @param cutoff the instant the name renders -- the stamped cut-off at
     *               publish, the cut-off the publish WOULD use for a preview.
     *               Never the clock: a preview of last year's list generated in
     *               January carries last year's tokens, exactly as the published
     *               file will.
     */
    public static String resolve(PublicationIssue issue, PublicationSeries series,
                                 PublicationIssueDesc desc, Date cutoff) {
        // A name somebody set for this issue. It is a decision, and re-deriving
        // over it would discard it with nothing to say it had been made -- the
        // same rule the issue's NAME has carried since the edit path existed.
        if (desc != null && desc.isFileNameOverridden()
                && desc.getFileName() != null && !desc.getFileName().isBlank()) {
            return withPdf(desc.getFileName().trim());
        }

        String name = suggested(issue, series, desc, cutoff);
        if (name == null || name.isBlank()) {
            name = desc != null && desc.getFileName() != null && !desc.getFileName().isBlank()
                    ? desc.getFileName()
                    : (issue == null ? null : issue.getPublicId()) + ".pdf";
        }
        return withPdf(name);
    }

    /** Always a PDF: it is what publish writes, whatever the pattern says. */
    public static String withPdf(String name) {
        if (name == null) {
            return null;
        }
        return name.toLowerCase().endsWith(".pdf") ? name : name + ".pdf";
    }
}
