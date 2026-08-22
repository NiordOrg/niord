package org.niord.core.publication.series;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.TableGenerator;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The entity contract: identity, and column coverage.
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
     * DATA-MODEL by gen-field-manifest.js; a stale one is caught in the other repo.
     */
    @Test
    public void everyDeclaredColumnExistsOnItsEntity() throws Exception {
        JsonNode manifest;
        try (InputStream in = EntityContractTest.class.getResourceAsStream("/entity-fields.json")) {
            assertNotNull(in, "entity-fields.json is missing");
            manifest = new ObjectMapper().readTree(in);
        }

        // Inherited from BaseEntity / VersionedEntity / DescEntity, or replaced by
        // the generalised audit (DM-Q2).
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

    /** DM-Q2: the audit is generalised, so a row can belong to a series instead of an issue. */
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
