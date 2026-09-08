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
import jakarta.transaction.Transactional;
import org.niord.core.publication.series.vo.IssuePreviewVo;
import org.niord.core.service.BaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Previews: the same render, written where the public cannot reach it.
 *
 * A preview is a convenience for reading the document before releasing it and
 * nothing more. Releasing renders the document from the frozen member list, so
 * what is stored here never becomes the published bytes and its age carries no
 * consequence: there is no freshness to compute and none is offered.
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
     * What is stored for an issue: the newest generation of each of its
     * languages.
     *
     * A language with nothing stored yields no row at all, so the list is what
     * can actually be opened rather than one entry per configured language with
     * an absence to be told apart by a flag.
     *
     * ONE directory read per language, the same one answering a single language
     * costs.
     */
    public List<IssuePreviewVo> stored(PublicationIssue issue) {
        List<IssuePreviewVo> out = new ArrayList<>();
        for (PublicationIssueDesc desc : issue.getDescs()) {
            newest(issue, desc.getLang()).ifPresent(preview -> out.add(new IssuePreviewVo(
                    preview.lang(),
                    preview.renderedAt().getTime())));
        }
        return out;
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
