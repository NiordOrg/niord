/*
 * Copyright 2026 Danish Maritime Authority.
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

package org.niord.web.publication;

import org.junit.jupiter.api.Test;
import org.niord.core.publication.series.IssueLifecycleService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The uploaded file's name, treated as data from the request.
 *
 * IssueFileService resolves the name against the issue's repository folder and
 * writes there. A name is not a path, but a multipart part can carry anything in
 * its filename header -- a browser is not the only thing that posts here -- and a
 * name that survives with `..` or a drive letter in it writes outside the folder
 * it was supposed to write inside.
 *
 * This is the only place that guard exists, so it is the only place it can be
 * pinned. The service below it takes the name as given, deliberately: one
 * sanitiser is a rule, two are a disagreement waiting to happen.
 */
public class UploadedFileNameTest {

    /** An ordinary name survives unchanged. */
    @Test
    public void aplainNameIsKept() {
        assertEquals("EfS-uge-29.pdf", PublicationIssueRestService.safeFileName("EfS-uge-29.pdf"));
    }

    /** Directory traversal is stripped rather than rejected: the name part is still a name. */
    @Test
    public void atraversingNameKeepsOnlyItsLastSegment() {
        assertEquals("EfS.pdf",
                PublicationIssueRestService.safeFileName("../../../../etc/EfS.pdf"));
    }

    /** Windows separators are path separators too, whatever the server runs on. */
    @Test
    public void abackslashPathIsStrippedAsWell() {
        assertEquals("EfS.pdf",
                PublicationIssueRestService.safeFileName("C:\\Users\\someone\\EfS.pdf"));
    }

    /** Surrounding whitespace would become part of the stored name and of the URL. */
    @Test
    public void surroundingWhitespaceIsRemoved() {
        assertEquals("EfS.pdf", PublicationIssueRestService.safeFileName("  EfS.pdf  "));
    }

    /**
     * A name that is only a path is refused, not silently turned into something.
     *
     * "." and ".." name directories. Stripping them leaves nothing to write to,
     * and inventing a name here would put a document on the public site under a
     * name nobody chose.
     */
    @Test
    public void anameThatNamesNoFileIsRefused() {
        for (String bad : new String[] { null, "", "   ", "..", ".", "some/dir/", "some\\dir\\" }) {
            assertThrows(IssueLifecycleService.TransitionRefusedException.class,
                    () -> PublicationIssueRestService.safeFileName(bad),
                    "'" + bad + "' does not name a file and must be refused");
        }
    }
}
