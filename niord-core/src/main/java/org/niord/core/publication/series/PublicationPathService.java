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

package org.niord.core.publication.series;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.niord.core.settings.annotation.Setting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Where archived and preview publication files live, and the containment rule
 * that keeps them out of the served repository.
 *
 * X-6 is asserted AT BOOT and the application refuses to start if it fails.
 * That severity is deliberate. The repository root is served to anonymous
 * callers; anything reachable underneath it is public. An archive holds superseded
 * editions of official notices, and a preview holds unpublished ones -- so a
 * misconfiguration that puts either under the repository root does not fail, it
 * silently publishes every draft and every withdrawn edition, and it looks like
 * nothing at all.
 *
 * A boot failure is loud, immediate and impossible to miss. Every other outcome
 * here is quiet.
 */
@ApplicationScoped
public class PublicationPathService {

    private static final Logger log = LoggerFactory.getLogger(PublicationPathService.class);

    @Inject
    @Setting(value = "repoRootPath", defaultValue = "${niord.home}/repo",
            description = "The root directory of the Niord repository")
    Path repoRoot;

    @Inject
    @Setting(value = "publicationArchiveRootPath", defaultValue = "${niord.home}/publication-archive",
            description = "Root directory for superseded publication files. MUST NOT be under repoRootPath.")
    Path archiveRoot;

    @Inject
    @Setting(value = "publicationPreviewRootPath", defaultValue = "${niord.home}/publication-preview",
            description = "Root directory for publication previews. MUST NOT be under repoRootPath.")
    Path previewRoot;

    /** The containment rule failed. Thrown at boot, never caught. */
    public static class UnsafePublicationRootException extends RuntimeException {
        public UnsafePublicationRootException(String message) {
            super(message);
        }
    }

    void init(@Observes StartupEvent ev) {
        assertOutsideRepository("publicationArchiveRootPath", archiveRoot);
        assertOutsideRepository("publicationPreviewRootPath", previewRoot);

        createIfMissing(archiveRoot);
        createIfMissing(previewRoot);

        log.info("Publication archive root: {}", archiveRoot);
        log.info("Publication preview root: {}", previewRoot);
    }

    /**
     * X-6. Neither root may resolve under the repository root.
     *
     * Compared on the REAL, normalised, absolute paths, so a symlink or a
     * ../.. cannot walk back in. Comparing the configured strings would pass for
     * a path that resolves straight back under the served root.
     */
    void assertOutsideRepository(String settingName, Path candidate) {
        if (candidate == null) {
            throw new UnsafePublicationRootException(settingName + " is not configured");
        }
        Path repo = normalise(repoRoot);
        Path root = normalise(candidate);

        if (root.equals(repo) || root.startsWith(repo)) {
            throw new UnsafePublicationRootException(
                    settingName + " resolves to " + root + ", which is inside the served repository root "
                            + repo + ". Everything under that root is readable by anonymous callers, so this "
                            + "would publish every superseded edition and every unpublished preview without "
                            + "erroring. Refusing to start.");
        }
    }

    /** Resolves symlinks and relative segments so the comparison is on real locations. */
    private static Path normalise(Path p) {
        Path absolute = p.toAbsolutePath().normalize();
        try {
            return absolute.toRealPath();
        } catch (IOException e) {
            // Not yet created: the normalised absolute path is the best available,
            // and it is still enough to catch a configured-inside-the-repo mistake.
            return absolute;
        }
    }

    private void createIfMissing(Path root) {
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UnsafePublicationRootException("could not create " + root + ": " + e.getMessage());
        }
    }

    public Path archiveRoot() {
        return archiveRoot;
    }

    public Path previewRoot() {
        return previewRoot;
    }

    public Path repoRoot() {
        return repoRoot;
    }

    /**
     * Where a superseded file is archived to.
     *
     * Keyed by issue and language and stamped, because an issue can be amended
     * more than once and C3 keeps every generation indefinitely -- an archive
     * that overwrites is not an archive.
     */
    public Path archivePathFor(String issuePublicId, String lang, String fileName, long stampedAt) {
        return archiveRoot.resolve(issuePublicId).resolve(lang).resolve(stampedAt + "-" + fileName);
    }

    /** Where a preview generation is rendered to. */
    public Path previewPathFor(String issuePublicId, String lang, long generation, String fileName) {
        return previewRoot.resolve(issuePublicId).resolve(lang).resolve(generation + "-" + fileName);
    }
}
