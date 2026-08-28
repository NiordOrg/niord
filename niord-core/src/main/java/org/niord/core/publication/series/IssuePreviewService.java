package org.niord.core.publication.series;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.niord.core.service.BaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Previews: the same render, written where the public cannot reach it.
 *
 * Staleness is COMPUTED, never stored. A stored flag has to be invalidated by
 * everything that could invalidate it -- an override, a criteria edit, a message
 * changing status -- and the one path that forgets leaves a preview claiming to
 * be current when it is not. Comparing two timestamps cannot forget.
 *
 * Generations are kept rather than overwritten, so that preview, compare,
 * publish is a sequence somebody can actually follow. The sweep bounds the cost
 * on a visible rule instead of an overwrite doing it silently.
 */
@ApplicationScoped
public class IssuePreviewService extends BaseService {

    private static final Logger log = LoggerFactory.getLogger(IssuePreviewService.class);

    /** How long a generation is kept before the sweep may remove it. */
    public static final long PREVIEW_TTL_MILLIS = 7L * 24 * 3600_000L;

    @Inject
    PublicationPathService paths;

    @Inject
    IssueAuditService audit;

    /** One rendered generation. */
    public record Preview(String lang, Path path, long generation, Date renderedAt) {
    }

    /** Writes a generation and returns where it went. */
    @Transactional
    public Preview record(PublicationIssue issue, String lang, String fileName, byte[] bytes) {
        long generation = System.currentTimeMillis();
        Path target = paths.previewPathFor(issue.getPublicId(), lang, generation, fileName);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new IssueRenderService.RenderFailedException("could not write the preview to " + target, e);
        }
        audit.created(issue, null, AuditAction.PREVIEW_GENERATED);
        return new Preview(lang, target, generation, new Date(generation));
    }

    /** The newest generation for a language, if there is one. */
    public Optional<Preview> newest(PublicationIssue issue, String lang) {
        Path dir = paths.previewRoot().resolve(issue.getPublicId()).resolve(lang);
        if (!Files.isDirectory(dir)) {
            return Optional.empty();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(Files::isRegularFile)
                    .map(p -> new Preview(lang, p, generationOf(p), new Date(Math.max(0, generationOf(p)))))
                    .filter(p -> p.generation() > 0)
                    .max(Comparator.comparingLong(Preview::generation));
        } catch (IOException e) {
            log.warn("could not list previews in {}", dir, e);
            return Optional.empty();
        }
    }

    /**
     * Whether the preview predates the thing it is a preview of.
     *
     * No preview at all counts as stale: "nothing to compare" and "current" are
     * different answers, and only one of them should let a release proceed
     * quietly.
     */
    public boolean isStale(PublicationIssue issue, String lang, Date memberSetChangedAt) {
        Optional<Preview> preview = newest(issue, lang);
        if (preview.isEmpty()) {
            return true;
        }
        if (memberSetChangedAt == null) {
            return false;
        }
        return preview.get().renderedAt().before(memberSetChangedAt);
    }

    /** Removes generations past the TTL. */
    public int sweep(Date now) {
        Path root = paths.previewRoot();
        if (!Files.isDirectory(root)) {
            return 0;
        }
        int removed = 0;
        try (Stream<Path> all = Files.walk(root)) {
            List<Path> expired = all.filter(Files::isRegularFile)
                    .filter(p -> {
                        long g = generationOf(p);
                        return g > 0 && now.getTime() - g > PREVIEW_TTL_MILLIS;
                    })
                    .toList();
            for (Path p : expired) {
                try {
                    Files.deleteIfExists(p);
                    removed++;
                } catch (IOException e) {
                    log.warn("could not remove expired preview {}", p, e);
                }
            }
        } catch (IOException e) {
            log.warn("preview sweep failed under {}", root, e);
        }
        return removed;
    }

    /** The generation is the millisecond prefix the writer put on the file name. */
    static long generationOf(Path file) {
        String name = file.getFileName().toString();
        int dash = name.indexOf('-');
        if (dash <= 0) {
            return -1;
        }
        try {
            return Long.parseLong(name.substring(0, dash));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
