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

package org.niord.core.publication.series.legacy;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.niord.core.service.BaseService;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The estate-scale entry points must not run inside anybody's transaction but
 * their own -- including the one they would otherwise inherit.
 *
 * THE FAILURE THIS PINS IS INVISIBLE IN THE SOURCE OF THE METHOD ITSELF.
 * {@link BaseService} is annotated {@code @Transactional} at class level, and
 * {@code jakarta.transaction.Transactional} is {@code @Inherited}, so every
 * public method of every service extending it is wrapped by the REQUIRED
 * interceptor on the container's DEFAULT transaction budget -- with nothing at
 * the method to say so.
 *
 * For a 784-second import that is fatal in the one way that looks like success:
 * the work commits through the transaction the method opens for itself, the
 * ambient one is reaped at the default timeout, and the interceptor's commit on
 * the way out throws a CHECKED RollbackException the generated subclass cannot
 * declare. The caller reads 500 (ArcUndeclaredThrowableException) over a database
 * holding the complete archive, and an operator seeing that re-runs a cutover
 * that had worked.
 *
 * Reflection rather than a run, because the annotation IS the contract: there is
 * no observable difference between the two shapes until the operation runs longer
 * than the default budget, which is minutes of real estate and not something a
 * test can afford to reproduce.
 */
public class LegacyImportTransactionTest {

    /**
     * The premise. If the base class ever stops being transactional the
     * annotations below become belt-and-braces rather than load-bearing -- and
     * whoever notices should be told that here, not by a 500 in a cutover window.
     */
    @Test
    public void theBaseClassIsWhereTheAmbientTransactionComesFrom() {
        assertTrue(BaseService.class.isAssignableFrom(LegacyImportService.class),
                "the import service no longer extends the transactional base class; "
                        + "re-check whether the NOT_SUPPORTED annotations below are still needed");
        assertNotNull(BaseService.class.getAnnotation(Transactional.class),
                "BaseService is no longer @Transactional, so the inherited binding this "
                        + "test exists to neutralise may have moved somewhere else");
        assertTrue(Transactional.class.isAnnotationPresent(java.lang.annotation.Inherited.class),
                "@Transactional is no longer @Inherited; the inheritance premise has changed");
    }

    @Test
    public void theEstateScaleEntryPointsJoinNoCallersTransaction() throws NoSuchMethodException {
        for (String name : new String[] { "run", "dryRun", "undo" }) {
            Method method = LegacyImportService.class.getDeclaredMethod(name);
            Transactional tx = method.getAnnotation(Transactional.class);
            assertNotNull(tx, "LegacyImportService." + name + "() carries no method-level "
                    + "@Transactional, so it inherits REQUIRED from BaseService and runs the whole "
                    + "estate inside an ambient transaction on the default budget");
            assertEquals(Transactional.TxType.NOT_SUPPORTED, tx.value(),
                    "LegacyImportService." + name + "() must run in no transaction but the one it "
                            + "opens itself, with the budget it chose");
        }
    }
}
