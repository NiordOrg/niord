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

package org.niord.web.publication;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The estate-scale endpoints must not impose a request transaction on work that
 * outlives one.
 *
 * WHAT GOES WRONG WITHOUT THIS, MEASURED. The series resource is @Transactional
 * at class level, which is right for the endpoints that write one row. An
 * endpoint that drives the whole archive is a different animal: it commits
 * through transactions of its own, sized for the estate or bounded per batch, and
 * the ambient one the class annotation opens sits there for the duration on the
 * container's DEFAULT budget. The reaper takes it long before the work finishes,
 * and the commit as the interceptor unwinds throws a CHECKED exception that the
 * generated subclass cannot declare -- so the caller reads 500
 * (ArcUndeclaredThrowableException) over a database that has every imported row
 * in it. The import ran 784 seconds against a 240-second default and answered
 * exactly that; the danger is that an operator believes it and re-runs a cutover
 * that had worked.
 *
 * A DECLARED SET, not a rule. Which endpoints are estate-scale is a fact about
 * what they call, not something readable off a signature -- /shadow-diff/run and
 * /shadow-diff look identical from here and only one of them writes. So the list
 * is written down and its absentees are pinned too: an endpoint that leaves the
 * set silently is the regression this test exists to catch.
 */
public class PublicationTransactionContractTest {

    /**
     * The endpoints that must carry NOT_SUPPORTED, by method name.
     *
     * import-legacy/validate, import-legacy (POST and DELETE): each opens one
     * transaction with a budget sized for the whole estate.
     * shadow-diff/run: commits per release, so an ambient transaction would hold
     * every batch open to the end and discard what the sweep reported as written.
     * shadow-diff/reset and cutover-preflight and diagnostic-report: the services
     * behind them manage their own, and the report re-resolves every imported
     * issue when asked for the historical replay.
     */
    private static final Set<String> ESTATE_SCALE = new LinkedHashSet<>(Set.of(
            "importDryRun",
            "importLegacy",
            "undoImport",
            "runShadowDiff",
            "resetShadowDiff",
            "diagnosticReport",
            "cutoverPreflight"));

    @Test
    public void everyEstateScaleEndpointRunsOutsideTheRequestTransaction() {
        Set<String> found = new TreeSet<>();
        for (Method method : PublicationSeriesRestService.class.getDeclaredMethods()) {
            if (!ESTATE_SCALE.contains(method.getName())) {
                continue;
            }
            found.add(method.getName());
            Transactional tx = method.getAnnotation(Transactional.class);
            assertNotNull(tx, method.getName() + " carries no method-level @Transactional, so the "
                    + "class annotation wraps it in a request transaction on the default budget");
            assertEquals(Transactional.TxType.NOT_SUPPORTED, tx.value(),
                    method.getName() + " must run outside the request transaction");
        }
        assertEquals(new TreeSet<>(ESTATE_SCALE), found,
                "an endpoint named in the estate-scale set no longer exists under that name; "
                        + "rename it here rather than dropping it, or the contract stops being checked");
    }

    /**
     * Nothing else on the resource may opt out of the class transaction, and
     * nothing may opt into a different one.
     *
     * REQUIRES_NEW is the one that would be tempting and wrong here: it commits
     * the endpoint's writes independently of the request, so a later refusal in
     * the same call leaves half a change behind.
     */
    @Test
    public void noOtherEndpointRedeclaresItsTransaction() {
        for (Method method : PublicationSeriesRestService.class.getDeclaredMethods()) {
            Transactional tx = method.getAnnotation(Transactional.class);
            if (tx == null) {
                continue;
            }
            assertTrue(ESTATE_SCALE.contains(method.getName()),
                    method.getName() + " declares @Transactional(" + tx.value() + ") but is not in "
                            + "the estate-scale set. Either it belongs there -- say so above, with "
                            + "what it drives -- or it should inherit the class annotation.");
        }
    }
}
