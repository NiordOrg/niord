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

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The renderer, stood in for, in this module only.
 *
 * The PDF templates live in the web module; this one has none. So the real
 * renderer cannot produce a document here AT ALL -- every call would fail
 * looking for a FreeMarker template that is not on this classpath -- and a
 * release refuses to finish without a document. What these tests are about is
 * everything AROUND the render: what is stamped, what is frozen, what is
 * archived before it is overwritten, and which file the official record ends up
 * pointing at. So the document is produced here, deterministically, and the
 * bytes are the ones the test asked for.
 *
 * Two controls, and both exist because a test needs to say something about the
 * document rather than merely have one. {@link #renders} names the bytes, so a
 * test that reads an archived generation can assert WHICH generation it got.
 * {@link #UNRENDERABLE} names a report id this stub refuses, so the "published
 * with no document" guard -- the failure that otherwise looks exactly like
 * success -- stays reachable without a real template engine.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class StubIssueRenderService extends IssueRenderService {

    /** The default body, for the many tests that only need a document to exist. */
    public static final String DEFAULT_BODY = "rendered";

    /** A report id this stub refuses, so a failing render is reachable. */
    public static final String UNRENDERABLE = "no-such-report";

    private static volatile String body = DEFAULT_BODY;

    /** The bytes every render produces until something says otherwise. */
    public static void renders(String nextBody) {
        body = nextBody;
    }

    /** Back to the default, so one test's bytes are not the next one's. */
    public static void reset() {
        body = DEFAULT_BODY;
    }

    @Override
    public byte[] render(RenderRequest request) {
        if (request == null || request.orderedMessages() == null) {
            throw new IllegalArgumentException("render() takes an ordered message list, never a query");
        }
        if (UNRENDERABLE.equals(request.reportId())) {
            throw new RenderFailedException("no such report: " + request.reportId(), null);
        }
        return body.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void renderToFile(RenderRequest request, Path target) {
        byte[] bytes = render(request);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new RenderFailedException("could not write the rendered report to " + target, e);
        }
    }
}
