package org.niord.core;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the Quarkus test bootstrap in niord-core actually reaches the MySQL
 * container, and is not silently falling back to something in-memory.
 *
 * The version assertion is the point of the test. An EntityManager that
 * injects successfully proves nothing about what it is connected to: the
 * shared hibernate_sequence behaviour, the native ENUM columns and the
 * spatial types only behave like production against real MySQL, so a test
 * suite that quietly ran on H2 would be green and worthless.
 */
@QuarkusTest
public class CoreQuarkusBootstrapTest {

    @Inject
    EntityManager entityManager;

    @Test
    @Transactional
    public void connectsToRealMySql() {
        assertNotNull(entityManager, "no EntityManager was injected");

        Object version = entityManager.createNativeQuery("SELECT VERSION()").getSingleResult();
        assertNotNull(version, "SELECT VERSION() returned nothing");
        assertTrue(version.toString().startsWith("8.0"),
                "expected MySQL 8.0.x, got '" + version + "'. If this says H2 or similar, the test "
                        + "is not talking to the niord-test-db container.");

        Object db = entityManager.createNativeQuery("SELECT DATABASE()").getSingleResult();
        assertEquals("niord", db.toString(), "connected to the wrong schema");
    }
}
