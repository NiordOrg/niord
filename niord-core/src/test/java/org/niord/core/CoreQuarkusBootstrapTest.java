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

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

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
@EnabledIf(value = "org.niord.core.DatabaseAvailable#isAvailable",
        disabledReason = "no MySQL on this machine -- see DatabaseAvailable for how to start one")
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
        // Any niord* schema: the migration check runs the same boot against a
        // throwaway database, and pinning the exact name would fail it for a
        // reason that has nothing to do with what is being tested.
        assertTrue(db.toString().startsWith("niord"),
                "connected to the wrong schema: " + db);
    }
}
