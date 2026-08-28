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

package org.niord.core;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every migration that reshapes the schema does it re-runnably.
 *
 * MySQL 8 has no ADD COLUMN IF NOT EXISTS and no CREATE INDEX IF NOT EXISTS, so
 * a bare ALTER is a migration that runs exactly once and fails on the second
 * attempt. That is not a hypothetical: the same statement has to be safe against
 * a database where Hibernate already made the change in place -- a development
 * database recovered exactly that way, or a rehearsal restore replayed onto an
 * environment that had drifted -- and the guarded procedures exist for it.
 *
 * THE FLOOR IS V6, and that is a fact about Flyway rather than about the earlier
 * migrations being right. Flyway checksums what it has applied and refuses to
 * start if a file changes afterwards, so V2 through V5 cannot be retro-fitted
 * with the guard on any environment that has already run them -- which is every
 * environment. They are recorded here as the reason the convention exists, not
 * excused: V2's bare ADD COLUMN is the one this rule would have caught.
 */
public class MigrationIdiomTest {

    /** Below this version the guard had not been adopted, and the files are frozen by checksum. */
    private static final int GUARDED_FROM = 6;

    private static final Pattern VERSION = Pattern.compile("^V(\\d+)__.*\\.sql$");

    /** A statement that reshapes a table and has no IF NOT EXISTS of its own. */
    private static final Pattern UNGUARDED = Pattern.compile(
            "(?im)^\\s*(ALTER\\s+TABLE\\b(?:(?!;).)*?\\bADD\\s+(?:COLUMN|INDEX|KEY|CONSTRAINT)\\b"
                    + "|CREATE\\s+(?:UNIQUE\\s+)?INDEX\\b)",
            Pattern.DOTALL);

    @Test
    public void everyMigrationFromTheGuardedFloorOnwardsIsReRunnable() throws Exception {
        List<Path> migrations = migrations();
        assertFalse(migrations.isEmpty(), "no migrations were found on the classpath at all");

        List<String> offenders = new ArrayList<>();
        for (Path file : migrations) {
            int version = versionOf(file);
            if (version < GUARDED_FROM) {
                continue;
            }
            String sql = Files.readString(file, StandardCharsets.UTF_8);
            // Inside a CALL to one of the guarded procedures the DDL is a string
            // literal, executed only when information_schema says the change is
            // missing. Outside one it runs unconditionally.
            String outsideGuards = sql.replaceAll("(?is)CALL\\s+niord_add_\\w+_if_absent\\s*\\((?:[^;])*?\\);", "");
            Matcher m = UNGUARDED.matcher(stripComments(outsideGuards));
            if (m.find()) {
                offenders.add(file.getFileName() + " -> " + m.group(1).trim());
            }
        }

        assertTrue(offenders.isEmpty(),
                "a migration reshapes the schema without the re-run guard, so a second run against a "
                        + "database that already carries the change fails and takes the deploy with it. "
                        + "Wrap it in niord_add_column_if_absent / niord_add_index_if_absent, as V6 and "
                        + "V9 do: " + offenders);
    }

    /** The newest migration is present, so the guard cannot be satisfied by an empty directory. */
    @Test
    public void theIssueReadPathIndexesShipAsAMigration() throws Exception {
        assertTrue(migrations().stream()
                        .anyMatch(p -> p.getFileName().toString().startsWith("V12__")),
                "V12 is missing; the indexes the entity declares would then describe a schema nobody has");
    }

    private static List<Path> migrations() throws Exception {
        URL dir = MigrationIdiomTest.class.getResource("/db/migration");
        assertNotNull(dir, "db/migration is not on the test classpath");
        try (var files = Files.list(Paths.get(dir.toURI()))) {
            return files.filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .sorted(Comparator.comparingInt(MigrationIdiomTest::versionOf))
                    .toList();
        }
    }

    private static int versionOf(Path file) {
        Matcher m = VERSION.matcher(file.getFileName().toString());
        return m.matches() ? Integer.parseInt(m.group(1)) : Integer.MAX_VALUE;
    }

    /** Line comments only; the files carry no block comments and SQL strings hold no "--". */
    private static String stripComments(String sql) {
        return sql.replaceAll("(?m)^\\s*--.*$", "");
    }
}
