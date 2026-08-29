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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The invariants that are still prose. The invariant-binding pass drove this to EMPTY on 2026-08-23.
 *
 * This file exists so that "not yet asserted" is a DECLARED state with an owner
 * rather than an absence. The manifest test rejects a pending with no owner and
 * a pending naming a task that does not exist, and prints the count on every run
 * -- so the number of rules still living as prose is visible rather than inferred.
 *
 * WHAT THE GATE FOUND, which is the argument for having had it. Thirty rules were
 * pending on tasks spanning the whole build, every one of which was already marked
 * complete. Twenty-three were implemented and simply never asserted -- the code
 * was written and then nothing held it to the rule. The other SEVEN had no
 * enforcement at all; the pending was the only record that the rule existed:
 *
 *   O-4  an INCLUDE of a message the criteria already select was accepted,
 *        recording a decision nobody made and quietly keeping that message if
 *        the criteria later narrowed
 *   O-6  an override naming a message that does not exist was accepted, and
 *        because the annex report takes its heading from the first member, the
 *        visible result was an untitled PDF rather than a complaint
 *   D-3  two languages could share a file name, and since both write into the
 *        same repoPath, the second upload silently overwrote the first
 *   D-8  a natively published issue's link was the bare STORAGE path rather than
 *        /rest/repo/file/..., so it would have 404'd -- while the imported rows
 *        beside it worked, because those carry legacy's link verbatim
 *
 * All four are implemented and asserted now. Had this file been left to Phase Z
 * as convenient, they would have reached cutover as prose, and D-8 would have
 * shipped a public API emitting links that do not resolve.
 *
 * Named ...Test so Surefire actually runs it. It was PendingInvariants first,
 * which looks exactly like a test class and is silently ignored -- the class
 * matters less than the annotations on it, but a file that appears to assert
 * things and never executes is its own small trap.
 */
public class PendingInvariantsTest {

    /**
     * There are no pending invariants, and this is what says so.
     *
     * Kept as a test rather than deleted with the last @BindsRule: an empty file
     * states that the count is zero, where a missing file states nothing at all.
     * The next rule that cannot be asserted yet gets declared here with its
     * owning task, and the manifest count moves off zero where somebody sees it.
     */
    @Test
    public void nothingIsPending() {
        assertTrue(true,
                "Every declared invariant is bound to an assertion. A rule added to the "
                        + "specification and not bound is caught by InvariantManifestTest, not here.");
    }
}
