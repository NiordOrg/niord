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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    /**
     * What to render, and how.
     *
     * @param groups the printed sections, where the document is drawn in them;
     *               null for every regime but the compiled one, so a template
     *               asks one question and the flat ordered list stays the default
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
            List<RenderGroup> groups) {
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

    /** Renders straight to a file, for the publish and preview paths. */
    public void renderToFile(RenderRequest request, Path target) {
        try {
            Files.createDirectories(target.getParent());
            byte[] bytes = render(request);
            Files.write(target, bytes);
        } catch (RenderFailedException e) {
            throw e;
        } catch (Exception e) {
            throw new RenderFailedException("could not write the rendered report to " + target, e);
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
        // a new page are looked up by uid rather than inferred.
        Set<String> separatePageIds = separatePageIds(request.orderedMessages());

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
                    .data("groups", modelGroups(request.groups()))
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
     */
    private static List<Map<String, Object>> modelGroups(List<RenderGroup> groups) {
        if (groups == null) {
            return null;
        }
        List<Map<String, Object>> out = new java.util.ArrayList<>(groups.size());
        for (RenderGroup g : groups) {
            Map<String, Object> model = new java.util.LinkedHashMap<>();
            model.put("publicId", g.publicId());
            model.put("name", g.name());
            model.put("week", g.week());
            model.put("weekTo", g.weekTo());
            model.put("year", g.year());
            model.put("cutoff", g.cutoff());
            model.put("messageIds", g.messageIds() == null ? List.of() : g.messageIds());
            model.put("manual", g.manual());
            out.add(model);
        }
        return out;
    }

    private Set<String> separatePageIds(List<MessageVo> messages) {
        try {
            Set<String> uids = messages.stream()
                    .map(MessageVo::getId)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            return uids.isEmpty() ? Set.of() : messageService.getSeparatePageUids(uids);
        } catch (Exception e) {
            // A page-break hint is not worth failing a publish over.
            log.warn("could not resolve separate-page uids; rendering without them", e);
            return Set.of();
        }
    }
}
