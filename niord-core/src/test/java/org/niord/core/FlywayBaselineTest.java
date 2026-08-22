package org.niord.core;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the schema-delivery mechanism: Flyway adopting an existing database
 * rather than rebuilding it.
 *
 * The deployable that carries Flyway at runtime is niord-dk-web, in the other
 * repository. The behaviour under test here is decided entirely by the four
 * properties below plus an empty migrations directory, so it is exercised in
 * the module that already has a working database test, rather than duplicating
 * the JUnit 5 apparatus into a third repo to assert the same thing.
 *
 * What this does NOT cover, and what covers it instead: that db/migration
 * actually travels in the niord-core jar and is therefore on the deployable's
 * classpath. That is asserted by packaging, not by this test.
 */
@QuarkusTest
@TestProfile(FlywayBaselineTest.BaselineProfile.class)
public class FlywayBaselineTest {

    /**
     * Mirrors the niord-dk-web runtime configuration. generation=none matters:
     * Flyway owning the schema and Hibernate reshaping it on boot are mutually
     * exclusive, and having both would hide which one actually acted.
     */
    public static class BaselineProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.hibernate-orm.database.generation", "none",
                    "quarkus.flyway.migrate-at-start", "true",
                    "quarkus.flyway.baseline-on-migrate", "true",
                    "quarkus.flyway.baseline-version", "0",
                    "quarkus.flyway.locations", "db/migration");
        }
    }

    @Inject
    EntityManager entityManager;

    @Inject
    Flyway flyway;

    @Test
    @Transactional
    public void adoptsTheExistingSchemaAtVersionZero() {
        Object present = entityManager.createNativeQuery(
                        "SELECT COUNT(*) FROM information_schema.tables "
                                + "WHERE table_schema = DATABASE() AND table_name = 'flyway_schema_history'")
                .getSingleResult();
        assertEquals(1, ((Number) present).intValue(), "flyway_schema_history was not created");

        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(
                        "SELECT version, type, success FROM flyway_schema_history ORDER BY installed_rank")
                .getResultList();

        assertEquals(1, rows.size(), "expected exactly the baseline row, got " + rows.size());
        assertEquals("0", String.valueOf(rows.get(0)[0]), "baseline was not stamped at version 0");
        assertEquals("BASELINE", String.valueOf(rows.get(0)[1]), "row is not a BASELINE");
        // MySQL stores success as TINYINT(1) and the driver hands it back as a Boolean,
        // not a Number.
        Object success = rows.get(0)[2];
        boolean ok = success instanceof Boolean b2 ? b2 : ((Number) success).intValue() == 1;
        assertTrue(ok, "baseline row is not marked successful");
    }

    /**
     * A second run must change nothing. This is the property that makes it safe
     * for every boot of a deployed instance to run migrate-at-start.
     */
    @Test
    public void isIdempotent() {
        int before = flyway.info().applied().length;
        flyway.migrate();
        int after = flyway.info().applied().length;
        assertEquals(before, after, "a second migrate() changed the applied set");
        assertEquals(1, after, "expected only the baseline to be applied, found " + after);
    }

    /** No hand-written DDL: the migrations directory is deliberately empty at this point. */
    @Test
    public void hasNoMigrationsYet() {
        long pending = flyway.info().pending().length;
        assertEquals(0, pending, "expected zero pending migrations, found " + pending);
    }
}
