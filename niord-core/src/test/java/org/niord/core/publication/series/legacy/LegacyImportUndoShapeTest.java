package org.niord.core.publication.series.legacy;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the import undo deletes, asserted over the source.
 *
 * The undo is the cutover window's only escape hatch: if the estate imports
 * wrongly there is one way back, and it runs once, under time pressure, on a
 * production database. Its failure mode is not a wrong answer -- it is a foreign
 * key violation part way through, naming a table nobody was thinking about, at
 * the moment somebody most needs it to work.
 *
 * WHY THIS IS A SOURCE ASSERTION AND NOT AN EXECUTION. Undo is scoped by
 * importSource across the whole estate, so calling it deletes every imported
 * series a shared database holds -- including the fixtures of whatever else is
 * running against it. A test that destroys its neighbours' data to prove a
 * delete list is complete is a worse trade than one that reads the delete list.
 * What is checked here is precisely what was missing: a child table with a
 * foreign key into what the undo removes, and no delete for it.
 */
public class LegacyImportUndoShapeTest {

    private static final Path SERVICE = Paths.get(
            "src/main/java/org/niord/core/publication/series/legacy/LegacyImportService.java");

    @Test
    public void theUndoDeletesTheCurationDecisions() throws IOException {
        String src = read();
        assertTrue(src.contains("DELETE FROM IssueOverride"),
                "the undo does not delete IssueOverride. An override holds a foreign key to the "
                        + "issue, so a single curation decision taken on an imported issue makes the "
                        + "issue delete fail with a constraint violation -- and this is the only way "
                        + "back out of a bad import.");
    }

    @Test
    public void theUndoDeletesTheSeriesLanguageRows() throws IOException {
        String src = read();
        assertTrue(src.contains("DELETE FROM PublicationSeries_languages"),
                "the undo does not delete the series language rows. The configured language list "
                        + "is an element collection in its own table with a foreign key back to the "
                        + "series; JPQL cannot address it because it is not an entity, and a bulk "
                        + "delete of the owner does not cascade the way remove() would -- so the "
                        + "rows survive and the series delete fails on the constraint.");
    }

    /** The order is children first, and the parents last. */
    @Test
    public void theDeletesAreOrderedChildrenFirst() throws IOException {
        String src = read();
        int overrides = src.indexOf("DELETE FROM IssueOverride");
        int languages = src.indexOf("DELETE FROM PublicationSeries_languages");
        int issues = src.indexOf("DELETE FROM PublicationIssue i WHERE i.series.id IN :ids");
        int series = src.indexOf("DELETE FROM PublicationSeries s WHERE s.id IN :ids");

        assertTrue(overrides > 0 && languages > 0 && issues > 0 && series > 0,
                "one of the four deletes the ordering is about has moved or been renamed");
        assertTrue(overrides < issues,
                "the overrides must go before the issues they point at");
        assertTrue(languages < series,
                "the language rows must go before the series they point at");
    }

    private static String read() throws IOException {
        assertTrue(Files.isRegularFile(SERVICE),
                "missing " + SERVICE + " -- this test reads source, so a move breaks it silently "
                        + "otherwise");
        return Files.readString(SERVICE, StandardCharsets.UTF_8);
    }
}
