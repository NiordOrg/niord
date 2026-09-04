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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.TableGenerator;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The entity contract: identity, column coverage, and column type.
 *
 * Both are reflection over the classes rather than a reading of the source,
 * because both failures are silent. An entity that quietly acquires its own id
 * generator still works -- until its ids collide with nothing and its rows
 * cannot be joined to the rest of the system. A dropped column still compiles.
 *
 * No database and no Quarkus.
 */
public class EntityContractTest {

    private static final List<Class<?>> ENTITIES = List.of(
            PublicationSeries.class,
            PublicationSeriesDesc.class,
            PublicationIssue.class,
            PublicationIssueDesc.class,
            IssueMember.class,
            IssueOverride.class,
            IssueAuditEntry.class);

    /**
     * Every id in this system comes from one shared sequence row. Inheriting the
     * base class is the whole contract; anything that overrides it breaks that for
     * one table only, and nothing else notices.
     */
    @Test
    public void noEntityBringsItsOwnIdGenerator() {
        List<String> offenders = new ArrayList<>();

        for (Class<?> type : ENTITIES) {
            for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
                if (c.isAnnotationPresent(SequenceGenerator.class)) {
                    offenders.add(c.getSimpleName() + " carries @SequenceGenerator");
                }
                if (c.isAnnotationPresent(TableGenerator.class)) {
                    offenders.add(c.getSimpleName() + " carries @TableGenerator");
                }
                for (Field f : c.getDeclaredFields()) {
                    if (f.isAnnotationPresent(SequenceGenerator.class)) {
                        offenders.add(c.getSimpleName() + "." + f.getName() + " carries @SequenceGenerator");
                    }
                    if (f.isAnnotationPresent(TableGenerator.class)) {
                        offenders.add(c.getSimpleName() + "." + f.getName() + " carries @TableGenerator");
                    }
                    GeneratedValue g = f.getAnnotation(GeneratedValue.class);
                    if (g != null && g.strategy() == GenerationType.IDENTITY) {
                        offenders.add(c.getSimpleName() + "." + f.getName() + " uses GenerationType.IDENTITY");
                    }
                }
            }
        }

        if (!offenders.isEmpty()) {
            fail("these break the shared-sequence identity contract:\n  " + String.join("\n  ", offenders));
        }
    }

    /**
     * Every column the specification declares exists on the entity.
     *
     * Checked against a generated manifest rather than by review, so a column
     * dropped during a refactor is a red build. The manifest is regenerated from
     * the data model by gen-field-manifest.js; a stale one is caught in the other repo.
     */
    @Test
    public void everyDeclaredColumnExistsOnItsEntity() throws Exception {
        JsonNode manifest;
        try (InputStream in = EntityContractTest.class.getResourceAsStream("/entity-fields.json")) {
            assertNotNull(in, "entity-fields.json is missing");
            manifest = new ObjectMapper().readTree(in);
        }

        // Inherited from BaseEntity / VersionedEntity / DescEntity, or replaced by
        // the generalised audit.
        Set<String> notOwnFields = Set.of("id", "version", "created", "updated", "entity", "lang",
                "statusChangedAt", "statusChangedBy", "statusChangeReason");

        List<String> missing = new ArrayList<>();
        int checked = 0;

        for (JsonNode entity : manifest.path("entities")) {
            if (entity.path("existing").asBoolean(false)) {
                continue; // the two pre-existing tables are not ours to declare
            }
            String name = entity.path("entity").asText();
            Class<?> type = ENTITIES.stream()
                    .filter(c -> c.getSimpleName().equals(name))
                    .findFirst()
                    .orElse(null);
            if (type == null) {
                continue; // collection tables have no class of their own
            }

            Set<String> declared = new LinkedHashSet<>();
            for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    declared.add(f.getName());
                }
            }

            for (JsonNode field : entity.path("fields")) {
                String fieldName = field.path("name").asText();
                if (notOwnFields.contains(fieldName)) {
                    continue;
                }
                checked++;
                if (!declared.contains(fieldName)) {
                    missing.add(name + "." + fieldName + "  (" + field.path("ddl").asText() + ")");
                }
            }
        }

        assertTrue(checked > 80, "only " + checked + " fields were checked; the manifest looks truncated");

        if (!missing.isEmpty()) {
            fail(missing.size() + " declared column(s) have no field on their entity:\n  "
                    + String.join("\n  ", missing));
        }
    }

    /**
     * The reserved columns.
     *
     * These belong to work that may never ship, and they are here anyway because
     * adding a column later means hand-rolled DDL against a live schema. This is
     * the one carve-out a reviewer must not tidy away, so it is a test rather than
     * a comment.
     */
    @Test
    public void thePlaceholderColumnsAreReserved() {
        assertHasField(IssueMember.class, "sortIndex");
        assertHasField(PublicationIssue.class, "snapshotSortBy");
        assertHasField(PublicationIssue.class, "snapshotSortOrder");
        assertHasField(PublicationIssue.class, "snapshotDomainId");
        assertHasField(PublicationIssue.class, "snapshotIntervalFrom");
        assertHasField(PublicationIssue.class, "legacyPublicationId");
        assertHasField(PublicationIssue.class, "publicWindowSource");
        assertHasField(PublicationIssue.class, "intervalFromSource");
        assertHasField(PublicationIssue.class, "intervalToSource");
        assertHasField(PublicationIssue.class, "reportParams");
        assertHasField(PublicationIssue.class, "criteriaOverride");
    }

    /** The audit is generalised, so a row can belong to a series instead of an issue. */
    @Test
    public void theAuditIsGeneralisedNotThreeFixedColumns() {
        assertHasField(IssueAuditEntry.class, "issue");
        assertHasField(IssueAuditEntry.class, "series");

        for (String gone : List.of("statusChangedAt", "statusChangedBy", "statusChangeReason")) {
            assertTrue(!hasField(PublicationSeries.class, gone),
                    "PublicationSeries." + gone + " is back. Three fixed columns cannot record a series that "
                            + "was activated, flagged dormant and reactivated -- an event that overwrites its "
                            + "own predecessor is not an audit trail.");
        }
    }

    /**
     * Every collection and map field is non-null on a freshly constructed entity.
     *
     * The converters make this a contract rather than a preference: a null column
     * comes back from JpaPropertiesAttributeConverter as an EMPTY map, so an entity
     * loaded from the database always has one. An uninitialised field therefore
     * leaves exactly one instance that behaves differently -- the one nobody has
     * persisted yet -- and the create path is made entirely of those.
     *
     * PublicationSeries.reportParams was that field. updateFromVo called clear() on
     * it, SystemPublicationSeriesVo initialises its own map so the null-guard above
     * the call was never false, and every create through the REST endpoint answered
     * 500 with an NPE. Not data-dependent: it could not have worked once. The suite
     * missed it because the tests build entities with setters and none of them went
     * through updateFromVo; the seed script found it on the first real call.
     *
     * Asserted over every field of every entity, rather than as a check that those
     * two maps are non-null, because a rule that only knows the fields already fixed
     * would not have caught PublicationIssue.reportParams -- which had the same gap,
     * and which this found.
     */
    @Test
    public void noEntityStartsLifeWithANullCollection() throws Exception {
        List<String> offenders = new ArrayList<>();

        for (Class<?> type : ENTITIES) {
            Constructor<?> ctor = type.getDeclaredConstructor();
            ctor.setAccessible(true);
            Object fresh = ctor.newInstance();

            for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) {
                        continue;
                    }
                    if (!Collection.class.isAssignableFrom(f.getType())
                            && !Map.class.isAssignableFrom(f.getType())) {
                        continue;
                    }
                    f.setAccessible(true);
                    if (f.get(fresh) == null) {
                        offenders.add(type.getSimpleName() + "." + f.getName()
                                + " (" + f.getType().getSimpleName() + ")");
                    }
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                "These fields are null on a newly constructed entity, so any code that mutates them in "
                        + "place -- clear(), add(), put() -- throws for created rows and works for loaded "
                        + "ones: " + offenders + ". Initialise them at the declaration.");
    }

    /**
     * Every enum-typed column keeps the type the migrations actually gave it.
     *
     * On MySQL the schema-generation default for an {@code @Enumerated(EnumType.STRING)}
     * field is a NATIVE ENUM column, not a varchar, and a length does not change
     * that -- only columnDefinition does. So a field that starts life as a String,
     * is later typed as an enum and is not pinned ends up expecting a native enum
     * from a column that was created as varchar. Nothing fails while the
     * application runs, because the enum still reads and writes its own name
     * either way, and the entire cost lands on the one boot that checks:
     * database.generation=validate refuses to start, and that boot is a deploy.
     *
     * Stated as agreement between the entity and the migration rather than as
     * "every enum column is a varchar", because most of them are deliberately
     * native enums -- pinning those would be the same mistake pointing the other
     * way, and would need an ALTER TABLE against a live schema to undo.
     */
    @Test
    public void everyStringEnumColumnKeepsTheTypeTheMigrationsGaveIt() throws Exception {
        Map<String, String> declared = columnTypesFromMigrations();
        assertTrue(declared.size() > 100,
                "only " + declared.size() + " columns were parsed out of db/migration; the scan is broken, "
                        + "and a broken scan agrees with everything");

        List<String> offenders = new ArrayList<>();
        int checked = 0;

        for (Class<?> type : ENTITIES) {
            Table table = type.getAnnotation(Table.class);
            String tableName = table != null && !table.name().isBlank() ? table.name() : type.getSimpleName();

            for (Field f : type.getDeclaredFields()) {
                Enumerated enumerated = f.getAnnotation(Enumerated.class);
                if (enumerated == null || enumerated.value() != EnumType.STRING) {
                    continue;
                }
                checked++;

                Column column = f.getAnnotation(Column.class);
                String columnName = column != null && !column.name().isBlank() ? column.name() : f.getName();
                String pin = column == null ? "" : column.columnDefinition();
                // No columnDefinition means the generator picks, and on MySQL it picks a native enum.
                String expected = pin.isBlank() ? "enum" : typeKeyword(pin);

                String actual = declared.get(tableName + "." + columnName);
                if (actual == null) {
                    offenders.add(tableName + "." + columnName + " -- no migration ever created this column");
                } else if (!actual.equals(expected)) {
                    offenders.add(tableName + "." + columnName + " -- the migration created it as " + actual
                            + ", the mapping expects " + expected
                            + (pin.isBlank() ? " (no columnDefinition, so the generator chooses a native enum)"
                                             : " (columnDefinition = \"" + pin + "\")"));
                }
            }
        }

        assertTrue(checked >= 20, "only " + checked + " enum fields were found; the reflection is broken");

        assertTrue(offenders.isEmpty(),
                "an enum-typed column disagrees with the column the database actually has, so a boot with "
                        + "hibernate-orm.database.generation=validate fails while everything else keeps "
                        + "working. Pin the column with columnDefinition = \"varchar(255)\" where the "
                        + "migration made a varchar -- PublicationIssue.intervalToSource is the pattern:\n  "
                        + String.join("\n  ", offenders));
    }

    /** A create table or add column statement in the shipped migrations. */
    private static final Pattern CREATE_TABLE = Pattern.compile(
            "(?is)create\\s+table\\s+(\\w+)\\s*\\((.*?)\\)\\s*engine\\s*=\\s*InnoDB\\s*;");

    private static final Pattern ADD_COLUMN = Pattern.compile(
            "(?is)ALTER\\s+TABLE\\s+(\\w+)\\s+ADD\\s+COLUMN\\s+(\\w+)\\s+([A-Za-z]+)");

    private static final Pattern MODIFY_COLUMN = Pattern.compile(
            "(?is)ALTER\\s+TABLE\\s+(\\w+)\\s+MODIFY\\s+(?:COLUMN\\s+)?(\\w+)\\s+([A-Za-z]+)");

    /** Not a column: the table-level clauses that share the create-table body. */
    private static final Set<String> NOT_A_COLUMN =
            Set.of("primary", "unique", "key", "index", "constraint", "foreign", "fulltext");

    /**
     * The schema the committed migrations build, as column name to type keyword.
     *
     * Read from the files rather than from a live database, so the rule holds on a
     * machine with no MySQL and cannot be satisfied by a database that someone
     * reshaped by hand. A later ALTER wins over the original create, which is what
     * replaying the files in order does.
     */
    private static Map<String, String> columnTypesFromMigrations() throws Exception {
        URL dir = EntityContractTest.class.getResource("/db/migration");
        assertNotNull(dir, "db/migration is not on the test classpath");

        List<Path> files;
        try (var listing = Files.list(Paths.get(dir.toURI()))) {
            files = listing.filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .sorted(java.util.Comparator.comparingInt(EntityContractTest::versionOf))
                    .toList();
        }

        Map<String, String> types = new HashMap<>();
        for (Path file : files) {
            String sql = Files.readString(file, StandardCharsets.UTF_8);

            Matcher tables = CREATE_TABLE.matcher(sql);
            while (tables.find()) {
                String table = tables.group(1);
                for (String column : topLevelParts(tables.group(2))) {
                    Matcher c = Pattern.compile("^(\\w+)\\s+([A-Za-z]+)").matcher(column);
                    if (c.find() && !NOT_A_COLUMN.contains(c.group(1).toLowerCase())) {
                        types.put(table + "." + c.group(1), c.group(2).toLowerCase());
                    }
                }
            }

            for (Pattern altered : List.of(ADD_COLUMN, MODIFY_COLUMN)) {
                Matcher m = altered.matcher(sql);
                while (m.find()) {
                    types.put(m.group(1) + "." + m.group(2), m.group(3).toLowerCase());
                }
            }
        }
        return types;
    }

    private static final Pattern VERSION = Pattern.compile("^V(\\d+)__.*\\.sql$");

    private static int versionOf(Path file) {
        Matcher m = VERSION.matcher(file.getFileName().toString());
        return m.matches() ? Integer.parseInt(m.group(1)) : Integer.MAX_VALUE;
    }

    /** Splits a create-table body on the commas that separate columns, not the ones inside enum ('A','B'). */
    private static List<String> topLevelParts(String body) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (char c : body.toCharArray()) {
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            }
            if (c == ',' && depth == 0) {
                parts.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        if (!current.toString().isBlank()) {
            parts.add(current.toString().trim());
        }
        return parts;
    }

    /** "varchar(255)" and "enum ('A','B')" both reduce to the word the column type is. */
    private static String typeKeyword(String columnDefinition) {
        Matcher m = Pattern.compile("^\\s*([A-Za-z]+)").matcher(columnDefinition);
        return m.find() ? m.group(1).toLowerCase() : columnDefinition.trim().toLowerCase();
    }

    private static boolean hasField(Class<?> type, String name) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getName().equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void assertHasField(Class<?> type, String name) {
        assertTrue(hasField(type, name),
                type.getSimpleName() + "." + name + " is missing. Adding it later means hand-rolled DDL "
                        + "against a live schema, which is exactly what reserving it now avoids.");
    }
}
