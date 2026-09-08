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
import java.util.Set;
import java.util.stream.Collectors;
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
        return newest(issue, lang)
                .map(preview -> isStale(preview, memberSetChangedAt))
                .orElse(true);
    }

    /**
     * The same question asked about a generation already in hand.
     *
     * The comparison itself, in one place, so that the rail's answer and the row
     * the issue screen puts a "stale" badge on cannot be two different rules. It
     * takes the preview rather than looking it up because the listing above is a
     * directory read: a caller that has already paid for it must not pay again to
     * find out whether what it holds is current.
     */
    private static boolean isStale(Preview preview, Date memberSetChangedAt) {
        return memberSetChangedAt != null && preview.renderedAt().before(memberSetChangedAt);
    }

    /**
     * What is stored for an issue: the newest generation of each of its
     * languages, with staleness answered against the issue's own stamp.
     *
     * A language with nothing stored yields no row at all. "No preview" and "a
     * preview that is stale" are different states -- one offers something to
     * open and the other does not -- and a row standing in for the absence would
     * have to be told apart by its own flag on every reader.
     *
     * The issue's stamp moves on every edit and every curation, which is what
     * makes it what "current" is read against; it is the same instant
     * {@link #isStaleFor} compares to for the rail, so a screen cannot show a
     * fresh badge on a row beside a rail warning that the preview is stale.
     *
     * ONE directory read per language, the same one answering a single language
     * costs.
     */
    public List<IssuePreviewVo> stored(PublicationIssue issue) {
        List<IssuePreviewVo> out = new ArrayList<>();
        for (PublicationIssueDesc desc : issue.getDescs()) {
            newest(issue, desc.getLang()).ifPresent(preview -> out.add(new IssuePreviewVo(
                    preview.lang(),
                    preview.renderedAt().getTime(),
                    isStale(preview, issue.getUpdated()))));
        }
        return out;
    }

    /**
     * Whether ANY of the issue's languages has a preview that predates its
     * current member set -- or has none at all.
     *
     * The issue's own stamp moves on every edit and every curation, so it is what
     * "current" is read against. Only meaningful for a series that renders a
     * document: an uploaded or link-backed one has no preview to be stale, and
     * warning about one nobody can generate is a warning nobody can clear.
     *
     * It lives here rather than beside each caller because it feeds the
     * PREVIEW_FRESH rail row, and the rail is computed on three paths -- the
     * publish gate, the checklist endpoint and the issue workbench. Three copies
     * of one definition is three chances for the rail to warn on one screen and
     * pass on another.
     */
    public boolean isStaleFor(PublicationIssue issue) {
        // Asked before the store is read: a series that renders nothing has no
        // preview to be stale, and listing its directories to find that out costs
        // a read per language for an answer that was already fixed.
        return rendersADocument(issue) && isStaleFor(issue, stored(issue));
    }

    /**
     * The same answer, off rows a caller has already read.
     *
     * The issue screen ships those rows and computes this rail row in one
     * response, and reading the store twice for the two would be one directory
     * listing per language spent on a question already answered. It is the same
     * rule either way: a language is current only if it has a row and the row is
     * not stale -- having no row at all is the "nothing to compare" half, and it
     * is what makes an issue nobody has previewed report a warning rather than a
     * pass.
     */
    public boolean isStaleFor(PublicationIssue issue, List<IssuePreviewVo> stored) {
        if (!rendersADocument(issue)) {
            return false;
        }
        Set<String> current = stored.stream()
                .filter(p -> !p.stale())
                .map(IssuePreviewVo::lang)
                .collect(Collectors.toSet());
        return issue.getDescs().stream().anyMatch(desc -> !current.contains(desc.getLang()));
    }

    /** Whether there is anything to preview: an uploaded or link-backed issue renders nothing. */
    private static boolean rendersADocument(PublicationIssue issue) {
        return issue.getSeries() != null && issue.getSeries().getReportId() != null;
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
