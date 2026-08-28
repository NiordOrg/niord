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

    /** What to render, and how. */
    public record RenderRequest(
            String reportId,
            String language,
            List<MessageVo> orderedMessages,
            String pageSize,
            String pageOrientation,
            Boolean mapThumbnails,
            boolean areaHeadings,
            String searchCriteriaCaption,
            Map<String, Object> reportParams) {
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
