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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds a test to the invariant ids it asserts.
 *
 * The binding lives on the assertion rather than in a central table on purpose:
 * a table drifts from the tests it claims to describe, and nothing notices,
 * whereas an annotation moves when the test moves and disappears when the test
 * is deleted.
 *
 * An invariant with no fixture is a comment. These rules were prose, and prose
 * is the class the publicTo capping bugs fell into -- both read perfectly well
 * and both were wrong.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface BindsRule {

    /** The invariant ids this test asserts, e.g. {"RI-1", "RI-2"}. */
    String[] value();

    /**
     * The task that will make this assertion real, when it cannot be written yet.
     *
     * A bounded state, not a TODO: the manifest test rejects a pending with no
     * owner, and rejects an owner that is not a task in the plan. The count of
     * pendings is printed on every run, so it is visible rather than inferred.
     */
    String pending() default "";
}
