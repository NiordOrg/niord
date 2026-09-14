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

import io.quarkus.arc.Arc;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

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
 * The controls exist because a test needs to say something about the document
 * rather than merely have one. {@link #renders} names the bytes, so a test that
 * reads an archived generation can assert WHICH generation it got.
 * {@link #UNRENDERABLE} names a report id this stub refuses, so the "published
 * with no document" guard -- the failure that otherwise looks exactly like
 * success -- stays reachable without a real template engine.
 *
 * The rest are about the languages of one document being drawn at the same time,
 * which is a property of the caller rather than of any document:
 * {@link #rendersPerLanguage} makes the two languages' bytes distinguishable,
 * {@link #fails} makes one of them fail while the other succeeds, and
 * {@link #readsTheDatabase} and {@link #threads} record what each render found
 * on the thread it was handed.
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

    /** Whether the bytes name the language that asked for them. */
    private static volatile boolean perLanguage;

    /** The one language this stub refuses, or null while it refuses none. */
    private static volatile String failingLanguage;

    /** Whether each render reads a row before drawing, as the real one does. */
    private static volatile boolean readsTheDatabase;

    private static final List<RenderRequest> REQUESTS = new CopyOnWriteArrayList<>();

    /**
     * The thread one language was drawn on, as the render found it.
     *
     * The renders run beside each other on borrowed threads, and what makes that
     * safe is not the threading itself but WHAT EACH THREAD IS GIVEN: no
     * transaction, so neither can reach the persistence context the publish is
     * holding; and a CDI request context of its own, so the reads a render still
     * makes for itself -- the report row, the template rows -- each get their own
     * session rather than sharing the caller's. Both of those are invisible in
     * the document, so they are recorded here or they are not tested at all.
     *
     * @param requestContext identity only -- two renders holding the same object
     *                       are two threads sharing one session
     * @param databaseFailure what stopped this thread reading a row, or null where
     *                        nothing did
     */
    public record RenderThread(String lang, String thread, boolean inTransaction, Object requestContext,
                               String databaseFailure) {
    }

    private static final List<RenderThread> THREADS = new CopyOnWriteArrayList<>();

    /** How each language's render found the thread it was given. */
    public static List<RenderThread> threads() {
        return List.copyOf(THREADS);
    }

    /** The bytes every render produces until something says otherwise. */
    public static void renders(String nextBody) {
        body = nextBody;
    }

    /**
     * Makes each language's bytes name the language they were drawn for.
     *
     * The languages of one document are rendered side by side and their bytes are
     * written afterwards, by the caller, from a list. Identical bytes cannot say
     * whether that list stayed in step with the languages: swap two entries and
     * every assertion still passes, while the English reader is served the Danish
     * document. So a test that cares asks for bytes that can be told apart.
     */
    public static void rendersPerLanguage() {
        perLanguage = true;
    }

    /** Makes this stub refuse one language, so a half-failed render is reachable. */
    public static void fails(String lang) {
        failingLanguage = lang;
    }

    /**
     * Makes each render read a row before it draws anything.
     *
     * The real renderer does: the report it draws from and the templates it draws
     * with are database rows, and it asks for them first thing. Off by default
     * because every other test in this module wants the stub to be free, and on
     * for the one test that has to prove the thread a render is handed can still
     * reach a database.
     */
    public static void readsTheDatabase() {
        readsTheDatabase = true;
    }

    /** Back to the default, so one test's bytes are not the next one's. */
    public static void reset() {
        body = DEFAULT_BODY;
        perLanguage = false;
        failingLanguage = null;
        readsTheDatabase = false;
        REQUESTS.clear();
        THREADS.clear();
    }

    /**
     * Every request this stub was handed, in order.
     *
     * Recorded because some of what publish decides is only visible IN the
     * request: which report it chose, and what it put in the parameter map. The
     * document is deterministic bytes here, so there is nothing to read it out
     * of afterwards -- and the parameter map is where a year became "2.026" on
     * the cover of a Danish edition, which is not a fact any assertion about a
     * file could reach.
     */
    public static List<RenderRequest> requests() {
        return List.copyOf(REQUESTS);
    }

    /** The last one, which is what a single-language test is asking about. */
    public static RenderRequest lastRequest() {
        return REQUESTS.isEmpty() ? null : REQUESTS.get(REQUESTS.size() - 1);
    }

    /**
     * The single-document entry point is the one overridden, because it is the one
     * that does the work. The concurrent entry point is left alone deliberately --
     * a test of the release path should exercise the real one, on the real threads.
     */
    @Override
    public byte[] render(RenderRequest request) {
        if (request == null || request.orderedMessages() == null) {
            throw new IllegalArgumentException("render() takes an ordered message list, never a query");
        }
        REQUESTS.add(request);
        THREADS.add(new RenderThread(request.language(), Thread.currentThread().getName(),
                QuarkusTransaction.isActive(),
                Arc.container().requestContext().getStateIfActive(),
                readsTheDatabase ? readARow() : null));
        if (UNRENDERABLE.equals(request.reportId())) {
            throw new RenderFailedException("no such report: " + request.reportId(), null);
        }
        String lang = request.language();
        if (lang != null && lang.equals(failingLanguage)) {
            throw new RenderFailedException("this stub refuses to render " + lang, null);
        }
        return (perLanguage ? body + ":" + lang : body).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * A query, on whatever thread the render was given, reported rather than
     * thrown.
     *
     * The service the real renderer reaches the database through is the one this
     * stub inherits, so the row asked for here is asked for exactly as the report
     * lookup asks for its own -- and a uid nothing carries keeps the answer
     * uninteresting and the question real.
     */
    private String readARow() {
        try {
            messageService.getSeparatePageUids(Set.of("no-such-member"));
            return null;
        } catch (Exception e) {
            return e.toString();
        }
    }
}
