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
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fails the build if the discovered test count collapses.
 *
 * Adding quarkus-junit5 switches Surefire to the JUnit Platform provider. Without
 * junit-vintage-engine on the classpath the platform discovers none of the JUnit 4
 * tests, and the build reports "Tests run: 0, Failures: 0, Errors: 0" followed by
 * BUILD SUCCESS -- a green build that ran nothing. Nothing else in the toolchain
 * notices, so this test is what makes that state loud.
 *
 * The count is obtained by discovery rather than execution, so this test does not
 * re-run the suite and cannot recurse into itself.
 */
public class TestSuiteGuardTest {

    /**
     * A floor under the discovered suite, held a little below the real count.
     *
     * 617 tests are discovered as this is written. The margin is deliberately
     * small: a floor far below the truth would let the suite lose a hundred tests
     * and still pass, which is a slower version of the failure this exists to
     * catch. It is loose enough only to absorb tests being consolidated.
     *
     * Raise it as real tests are added; NEVER lower it to make a build pass. A
     * drop here is either the vintage engine gone -- discovery collapses to
     * almost nothing and the build still reports success -- or a whole class that
     * stopped being found, and both look identical to a green build.
     */
    private static final int FLOOR = 590;

    @Test
    public void testSuiteIsStillDiscovered() {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectPackage("org.niord"))
                .build();

        Launcher launcher = LauncherFactory.create();
        TestPlan plan = launcher.discover(request);

        long discovered = plan.countTestIdentifiers(this::isForeignTest);

        assertTrue(discovered >= FLOOR,
                "Discovered " + discovered + " tests, expected at least " + FLOOR
                        + ". A drop here usually means the JUnit 4 suite stopped being discovered -- "
                        + "check that junit-vintage-engine is still a test dependency of niord-core.");
    }

    /** Counts real tests, excluding this guard's own so the floor stays honest. */
    private boolean isForeignTest(TestIdentifier id) {
        return id.isTest() && !id.getUniqueId().contains(TestSuiteGuardTest.class.getSimpleName());
    }
}
