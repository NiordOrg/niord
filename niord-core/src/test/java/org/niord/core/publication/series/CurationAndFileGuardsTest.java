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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The four rules that had no enforcement at all.
 *
 * O-4, O-6, D-3 and D-8 were each left @Pending on a task. Those tasks are
 * marked complete, and none of the four had a line of code behind it -- the
 * pending was the only record that the rule existed. That is precisely the
 * failure the gate is for: without it these four would have reached cutover as
 * prose, and D-8 in particular would have shipped a public API emitting links
 * that do not resolve.
 *
 * The guards themselves are asserted where they live; this covers D-8, which is
 * a pure mapping and needs no database.
 */
public class CurationAndFileGuardsTest {

    private static PublicationIssueDesc desc(String link, String filePath) {
        PublicationIssueDesc d = new PublicationIssueDesc();
        d.setLang("da");
        d.setLink(link);
        d.setFilePath(filePath);
        return d;
    }

    /**
     * D-8: a repository-hosted file gets the fetchable relative URL.
     *
     * filePath is a STORAGE path and is not fetchable. Legacy stores
     * /rest/repo/file/<path> for all 2,128 of its repository-hosted descs, so
     * emitting the bare path would give every natively published issue a link
     * that 404s while the imported rows beside it worked -- the worst kind of
     * inconsistency, because the working ones hide it.
     */
    @BindsRule({"D-8"})
    @Test
    public void arepositoryHostedFileGetsTheRelativeRepositoryUrl() {
        String link = IssuePublicationMapping.linkOf(
                desc(null, "publications/a/8e/issue-id/EfS-Uge-27-2026.pdf"));

        assertEquals("/rest/repo/file/publications/a/8e/issue-id/EfS-Uge-27-2026.pdf", link);
        assertTrue(link.startsWith("/"), "repository links are relative, never absolute");
    }

    /** An explicit link is external and returned untouched, absolute and all. */
    @BindsRule({"D-8"})
    @Test
    public void anExternalLinkIsLeftAbsolute() {
        String absolute = "https://www.soefartsstyrelsen.dk/Media/6/0/Dansk%20Fyrliste%202020.pdf";
        assertEquals(absolute, IssuePublicationMapping.linkOf(desc(absolute, null)),
                "an EXTERNAL_LINK points off-site and must not be rewritten as a repository path");
    }

    /** No file and no link is no link, rather than a prefix pointing at nothing. */
    @BindsRule({"D-8"})
    @Test
    public void nofileAndNoLinkYieldsNoLink() {
        assertNull(IssuePublicationMapping.linkOf(desc(null, null)),
                "a bare /rest/repo/file/ would be a link to the repository root");
    }
}
