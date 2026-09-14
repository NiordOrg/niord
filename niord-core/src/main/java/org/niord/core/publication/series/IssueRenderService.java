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

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.context.ThreadContext;
import org.niord.core.NiordApp;
import org.niord.core.message.MessageService;
import org.niord.core.report.FmReport;
import org.niord.core.report.FmReportService;
import org.niord.core.script.FmTemplateService;
import org.niord.core.script.FmTemplateService.ProcessFormat;
import org.niord.model.message.MessageVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Renders an issue's PDF, in process, from an ordered member list.
 *
 * R1. The old path called the application's own report endpoint over HTTP with
 * a generated ticket, and the endpoint then RE-SEARCHED for the messages using
 * MessageSearchParams.instantiate -- which day-snaps the interval, rewrites
 * seriesIds and forces PUBLISHED-only. So the document could contain a different
 * set of messages than the one the resolver decided on, and nothing compared
 * them.
 *
 * The input here is the ORDERED LIST ITSELF. There is no search inside this
 * class and no way to reach one: what the resolver decided is what gets printed,
 * in the order B1.4 assigned. That is the entire point of the extraction.
 */
@ApplicationScoped
public class IssueRenderService {

    private static final Logger log = LoggerFactory.getLogger(IssueRenderService.class);

    @Inject
    FmTemplateService templateService;

    @Inject
    FmReportService fmReportService;

    @Inject
    MessageService messageService;

    @Inject
    NiordApp app;

    /** The pool the languages are drawn on; see {@link #executor()}. */
    private volatile ManagedExecutor executor;

    /**
     * How many languages of one document may be drawn at the same time.
     *
     * A series is configured for the languages it publishes in, which is two here
     * and has never been more than a handful. The cap is what keeps a document
     * with more of them from turning one publish into as many simultaneous layout
     * engines: each one holds a whole paginated document in memory and fetches
     * the stylesheets and images it names over HTTP, so the memory and the socket
     * count both grow with it, and past a small number the machine gives back
     * less than the concurrency won.
     */
    private static final int MAX_CONCURRENT_LANGUAGES = 3;

    /**
     * What to render, and how.
     *
     * @param groups          the printed sections, where the document is drawn in
     *                        them; null for every regime but the compiled one, so a
     *                        template asks one question and the flat ordered list
     *                        stays the default
     * @param separatePageIds the members that must start on a new page, resolved by
     *                        the caller. It is carried rather than looked up here
     *                        because it is a DATABASE question and the same answer
     *                        for every language: asked inside the render it would be
     *                        asked once per language, on a worker thread, against
     *                        the persistence context of the transaction that is
     *                        waiting for it
     */
    public record RenderRequest(
            String reportId,
            String language,
            List<MessageVo> orderedMessages,
            String pageSize,
            String pageOrientation,
            Boolean mapThumbnails,
            boolean areaHeadings,
            String searchCriteriaCaption,
            Map<String, Object> reportParams,
            List<RenderGroup> groups,
            Set<String> separatePageIds) {
    }

    /** One language of a document, drawn. */
    public record Rendered(RenderRequest request, byte[] bytes) {
    }

    /**
     * One printed section of a document that is drawn in sections.
     *
     * The messages are named by id rather than carried, because the ordered list
     * IS the document: a group that held its own copies could print a message the
     * flat list does not contain, or print one twice, and nothing downstream
     * would notice. The ids partition the list the request already carries.
     *
     * {@code manual} marks the one section that belongs to no source: the members
     * somebody added to the issue by hand, which came from no earlier document and
     * are printed under a heading that says so.
     */
    public record RenderGroup(
            String publicId,
            String name,
            Integer week,
            Integer weekTo,
            Integer year,
            java.util.Date cutoff,
            List<String> messageIds,
            boolean manual) {
    }

    /** A render failed. Carried rather than swallowed: a missing PDF is a failed publish. */
    public static class RenderFailedException extends PublicationException {
        public RenderFailedException(String message, Throwable cause) {
            super("RENDER_FAILED", message, cause);
        }
    }

    /**
     * Renders to bytes.
     *
     * Bytes rather than a stream, because the publish transaction has to hash
     * what it wrote and archive what it replaced, and a stream that is consumed
     * once cannot be both.
     */
    public byte[] render(RenderRequest request) {
        if (request == null || request.orderedMessages() == null) {
            throw new IllegalArgumentException("render() takes an ordered message list, never a query");
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            renderTo(request, out);
            return out.toByteArray();
        } catch (RenderFailedException e) {
            throw e;
        } catch (Exception e) {
            throw new RenderFailedException("could not render the issue report", e);
        }
    }

    /**
     * Every language of one document, drawn at the same time, in the order they
     * were asked for.
     *
     * A document is rendered once per configured language and the two renders
     * have nothing to say to each other: the member rows are the same, the
     * template is the same, and the only difference is which desc a value object
     * carries. Run one after the other they simply add up -- an annual is five
     * hundred pages twice, and the second language is another nineteen seconds
     * on the thread that pressed Publish.
     *
     * So the languages are drawn side by side. WHAT IS HANDED IN IS ALREADY
     * INERT: the caller has converted the entities to value objects and resolved
     * the page breaks on its own thread, so nothing a worker touches is attached
     * to the publish transaction's persistence context. The bytes come back here
     * and the caller writes them, in language order, exactly as it did when it
     * wrote them one at a time.
     *
     * ONE LANGUAGE IS RENDERED INLINE, on the calling thread. It is not an
     * optimisation: the common issue has one language, and a document that was
     * never going to overlap anything should not be handed to a thread pool, a
     * queue and a Future to arrive at the same place.
     *
     * A failure in any language fails the whole thing, as it always has -- but
     * every language is waited for first. Rethrowing at the first failure would
     * leave the other one drawing a document nobody will ever read, on a thread
     * nobody is holding, while the transaction that started it rolls back.
     */
    public List<Rendered> renderAll(List<RenderRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        if (requests.size() == 1) {
            RenderRequest only = requests.get(0);
            return List.of(new Rendered(only, render(only)));
        }

        List<Future<byte[]>> futures = new ArrayList<>(requests.size());
        for (RenderRequest request : requests) {
            futures.add(executor().submit(() -> render(request)));
        }

        List<byte[]> bytes = new ArrayList<>(requests.size());
        RenderFailedException failure = null;
        boolean interrupted = false;
        for (int i = 0; i < futures.size(); i++) {
            try {
                bytes.add(futures.get(i).get());
            } catch (InterruptedException e) {
                // The flag is restored once every language has been waited for,
                // and not before: setting it here would make the remaining waits
                // return immediately and abandon the threads they were waiting on.
                interrupted = true;
                bytes.add(null);
                failure = failure != null ? failure : new RenderFailedException(
                        "interrupted while rendering " + requests.get(i).language(), e);
            } catch (ExecutionException e) {
                bytes.add(null);
                RenderFailedException unwrapped = unwrap(requests.get(i), e.getCause());
                failure = failure != null ? failure : unwrapped;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        if (failure != null) {
            throw failure;
        }

        List<Rendered> out = new ArrayList<>(requests.size());
        for (int i = 0; i < requests.size(); i++) {
            out.add(new Rendered(requests.get(i), bytes.get(i)));
        }
        return out;
    }

    /**
     * A worker's failure as the caller would have seen it had it rendered inline.
     *
     * The cause is what the render threw; the ExecutionException around it is an
     * artefact of having waited for a thread, and a caller that reported it would
     * tell an admin "java.util.concurrent.ExecutionException" where it used to
     * name the template that could not be found.
     */
    private static RenderFailedException unwrap(RenderRequest request, Throwable cause) {
        if (cause instanceof RenderFailedException failed) {
            return failed;
        }
        String message = cause == null ? "the render produced no document"
                : cause.getMessage() != null ? cause.getMessage() : cause.toString();
        return new RenderFailedException(
                "could not render language " + request.language() + ": " + message, cause);
    }

    /**
     * The threads the languages are drawn on.
     *
     * BUILT RATHER THAN INJECTED, and the two cleared contexts are the whole
     * reason. The container's own executor carries every context to the worker
     * unchanged, and both of the ones carried here are a session.
     *
     * The TRANSACTION, because a release is one, and the session it holds is the
     * one every frozen member was read into. Two workers resuming that
     * transaction would be two threads inside one persistence context, which is
     * the single thing a persistence context cannot survive.
     *
     * The CDI REQUEST CONTEXT, because without a transaction the persistence
     * layer falls back to the REQUEST-scoped session instead -- so a propagated
     * request context hands both workers one session again by the other route.
     * Cleared, each worker is given a blank request context of its own for the
     * duration of its render, and takes it away afterwards; the few rows a render
     * still reads for itself -- the report row, the template rows, a settings
     * miss -- are then its own, on its own connection. Nothing the render reads
     * depends on WHOSE request it was: the report is looked up by id, and the
     * zone the dates are printed in travels in the request rather than being
     * asked of the current domain.
     *
     * It borrows the container's worker threads rather than starting any, and the
     * bound is a ceiling on how many of them one document may occupy, so a
     * publish cannot starve the requests running beside it.
     */
    private ManagedExecutor executor() {
        ManagedExecutor current = executor;
        if (current == null) {
            synchronized (this) {
                current = executor;
                if (current == null) {
                    current = ManagedExecutor.builder()
                            .propagated(ThreadContext.ALL_REMAINING)
                            .cleared(ThreadContext.TRANSACTION, ThreadContext.CDI)
                            .maxAsync(MAX_CONCURRENT_LANGUAGES)
                            .build();
                    executor = current;
                }
            }
        }
        return current;
    }

    @PreDestroy
    void releaseTheExecutor() {
        ManagedExecutor current = executor;
        if (current != null) {
            // A view over the container's pool: this releases the view, and the
            // threads underneath belong to the container and go on serving it.
            current.shutdown();
            executor = null;
        }
    }

    private void renderTo(RenderRequest request, OutputStream out) {
        FmReport report;
        try {
            report = fmReportService.getReport(request.reportId());
        } catch (Exception e) {
            throw new RenderFailedException("no such report: " + request.reportId(), e);
        }
        if (report == null) {
            throw new RenderFailedException("no such report: " + request.reportId(), null);
        }

        // Preserved from the endpoint this replaces: messages that must start on
        // a new page are named by uid rather than inferred. Resolved by the
        // caller -- see RenderRequest -- and absent is empty, so a caller that
        // never asked renders without page breaks rather than failing.
        Set<String> separatePageIds = request.separatePageIds() == null
                ? Set.of() : request.separatePageIds();

        try {
            FmTemplateService.FmTemplateBuilder builder = templateService.newFmTemplateBuilder()
                    .templatePath(report.getTemplatePath())
                    .data("executionMode", app.getExecutionMode())
                    .data("messages", request.orderedMessages())
                    .data("areaHeadings", request.areaHeadings())
                    .data("searchCriteria", request.searchCriteriaCaption())
                    .data("pageSize", request.pageSize())
                    .data("pageOrientation", request.pageOrientation())
                    .data("mapThumbnails", request.mapThumbnails())
                    .data("separatePageIds", separatePageIds)
                    .data("frontPage", true)
                    // The sections, where the document has any. Absent rather than
                    // empty for every other regime, so a template asks one question
                    // -- "are there groups" -- and the flat list stays the default
                    // for the templates that have always printed one.
                    .data("groups", modelGroups(request.groups(), request.orderedMessages()))
                    .data(report.getProperties());

            if (request.reportParams() != null) {
                builder = builder.data(request.reportParams());
            }

            builder.dictionaryNames("web", "message", "pdf")
                    .language(request.language())
                    .process(ProcessFormat.PDF, out);

        } catch (Exception e) {
            throw new RenderFailedException(
                    "could not render report " + request.reportId() + " for language " + request.language(), e);
        }
    }

    /**
     * The sections, as the template sees them.
     *
     * Plain maps rather than the records themselves, because the object wrapper
     * exposes JavaBean properties and a record has none: {@code group.name} in a
     * template would find nothing at all on one. A map is also the shape the
     * model already speaks -- every other value here is a string, a list or a
     * flag -- so the template reads the same way whatever produced it.
     *
     * EACH SECTION IS ALSO HANDED ITS OWN MEMBERS, and the ids stay beside them.
     * A section that carries only ids leaves the template to find them, and the
     * only way a template can is to scan the whole ordered list once per section
     * -- fifty-two scans of twelve hundred members for one annual, and the same
     * again for the second language. The partition below is the same one the
     * template performed, done once, in the order the ordered list is in: the
     * members are LOOKED UP IN THAT LIST rather than carried alongside it, so a
     * section still cannot name anything the document does not print.
     */
    private static List<Map<String, Object>> modelGroups(List<RenderGroup> groups,
                                                         List<MessageVo> orderedMessages) {
        if (groups == null) {
            return null;
        }
        Map<String, MessageVo> byId = new LinkedHashMap<>();
        for (MessageVo m : orderedMessages) {
            if (m.getId() != null) {
                byId.put(m.getId(), m);
            }
        }
        List<Map<String, Object>> out = new ArrayList<>(groups.size());
        for (RenderGroup g : groups) {
            List<String> ids = g.messageIds() == null ? List.of() : g.messageIds();
            List<MessageVo> members = new ArrayList<>(ids.size());
            for (String id : ids) {
                MessageVo m = byId.get(id);
                // An id the ordered list does not carry is simply not printed,
                // which is what the template's own scan did with it.
                if (m != null) {
                    members.add(m);
                }
            }
            Map<String, Object> model = new LinkedHashMap<>();
            model.put("publicId", g.publicId());
            model.put("name", g.name());
            model.put("week", g.week());
            model.put("weekTo", g.weekTo());
            model.put("year", g.year());
            model.put("cutoff", g.cutoff());
            model.put("messageIds", ids);
            model.put("messages", members);
            model.put("manual", g.manual());
            out.add(model);
        }
        return out;
    }

    /**
     * Which of these members must start on a new page.
     *
     * ONCE PER DOCUMENT, on the caller's thread, because the answer is a property
     * of the members rather than of the language they are printed in -- and
     * because it is a query. Asked from inside the render it would be asked again
     * for every language, from a thread that has no business holding the publish
     * transaction's session.
     */
    public Set<String> separatePageIds(Collection<String> memberUids) {
        try {
            Set<String> uids = new LinkedHashSet<>();
            for (String uid : memberUids) {
                if (uid != null) {
                    uids.add(uid);
                }
            }
            return uids.isEmpty() ? Set.of() : messageService.getSeparatePageUids(uids);
        } catch (Exception e) {
            // A page-break hint is not worth failing a publish over.
            log.warn("could not resolve separate-page uids; rendering without them", e);
            return Set.of();
        }
    }
}
