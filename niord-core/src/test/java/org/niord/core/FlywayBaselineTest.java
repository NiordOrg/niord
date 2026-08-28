package org.niord.core;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.List;

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
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
public class FlywayBaselineTest {

    /**
     * Mirrors the niord-dk-web runtime configuration. generation=none matters:
     * Flyway owning the schema and Hibernate reshaping it on boot are mutually
     * exclusive, and having both would hide which one actually acted.
     */
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

        assertTrue(rows.size() >= 1, "no rows in flyway_schema_history at all");
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

    }

    /**
     * The migration applies on top of the baseline rather than instead of it.
     *
     * This is the acceptance the delivery mechanism turns on: an existing database is
     * adopted at version 0, the publication tables arrive as a migration, and
     * nothing else in the schema is touched.
     */
    @Test
    public void theMigrationAppliesOnTopOfTheBaseline() {
        var applied = flyway.info().applied();
        assertTrue(applied.length >= 2,
                "expected the baseline plus at least one migration, found " + applied.length);
        assertEquals("0", String.valueOf(applied[0].getVersion()), "the first entry is not the baseline");
        assertTrue(java.util.Arrays.stream(applied)
                        .anyMatch(m -> "1".equals(String.valueOf(m.getVersion()))),
                "V1 was never applied");
        assertEquals(0, flyway.info().pending().length, "a migration is still pending after start-up");
    }
}
