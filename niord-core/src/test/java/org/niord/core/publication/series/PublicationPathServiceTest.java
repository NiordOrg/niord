package org.niord.core.publication.series;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicationPathServiceTest {

    @TempDir
    Path root;

    private PublicationPathService service() {
        PublicationPathService service = new PublicationPathService();
        service.repoRoot = root.resolve("repo");
        service.archiveRoot = root.resolve("archive");
        service.previewRoot = root.resolve("preview");
        return service;
    }

    @Test
    void createsMissingRootsOutsideAMissingRepository() {
        PublicationPathService service = service();
        service.init(null);
        assertTrue(Files.isDirectory(service.archiveRoot()));
        assertTrue(Files.isDirectory(service.previewRoot()));
        assertFalse(Files.exists(service.repoRoot()));
    }

    @Test
    void refusesMissingRootsInsideTheRepository() {
        PublicationPathService service = service();
        service.previewRoot = service.repoRoot.resolve("../repo/preview");
        assertThrows(PublicationPathService.UnsafePublicationRootException.class, () -> service.init(null));
        assertFalse(Files.exists(service.previewRoot));
    }

    @Test
    void refusesMissingChildrenOfALinkIntoTheRepository() throws Exception {
        PublicationPathService service = service();
        Files.createDirectory(service.repoRoot);
        Path alias = root.resolve("external-alias");
        linkDirectory(alias, service.repoRoot);
        try {
            Path candidate = alias.resolve("missing/child");
            service.previewRoot = candidate;
            assertThrows(PublicationPathService.UnsafePublicationRootException.class, () -> service.init(null));
            service.previewRoot = root.resolve("preview");
            service.archiveRoot = candidate;
            assertThrows(PublicationPathService.UnsafePublicationRootException.class, () -> service.init(null));
            assertFalse(Files.exists(service.repoRoot.resolve("missing")));
        } finally {
            Files.delete(alias);
        }
    }

    @Test
    void allowsMissingChildrenOfALinkOutsideTheRepository() throws Exception {
        PublicationPathService service = service();
        Path outside = Files.createDirectory(root.resolve("private"));
        Path alias = root.resolve("private-alias");
        linkDirectory(alias, outside);
        try {
            service.previewRoot = alias.resolve("missing/preview");
            service.init(null);
            assertEquals(outside.resolve("missing/preview").toRealPath(), service.previewRoot.toRealPath());
        } finally {
            Files.delete(alias);
        }
    }

    private static void linkDirectory(Path link, Path target) throws Exception {
        if (System.getProperty("os.name").startsWith("Windows")) {
            // Junctions exercise real-path resolution without requiring the
            // Windows privilege needed to create symbolic links.
            Process process = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J",
                    link.toString(), target.toString()).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes());
            assertEquals(0, process.waitFor(), output);
        } else {
            Files.createSymbolicLink(link, target);
        }
    }
}
