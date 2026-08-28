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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This write compares the caller's revision against the stored one before it
 * changes anything.
 *
 * The annotation does NOTHING at runtime, exactly as the domain-scope marker does
 * nothing: it is a declaration, and the contract test is what gives it teeth.
 * Every method on the publication resources that changes a series or an issue
 * that already exists must either carry this or be named, with a reason, on the
 * test's unguarded list.
 *
 * WHY DECLARE IT rather than let each method just call the guard. A missing check
 * is invisible -- the endpoint works, and the only symptom is an edit somebody
 * made being quietly reverted by a form that was loaded before it. The endpoint
 * added next year would ship without the check and nothing would surface it.
 * Making the absence a declaration turns "forgot" into a failing test.
 *
 * An interceptor is the wrong shape here for the same reason it is wrong for the
 * domain guard: the revision arrives in a typed body on some endpoints, in an
 * untyped map on others and as a query parameter where there is no body at all,
 * and the entity is resolved inside the method from a path parameter. A second
 * resolver that can disagree with the first is how a guard ends up protecting a
 * different row from the one being written.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface VersionChecked {
}
