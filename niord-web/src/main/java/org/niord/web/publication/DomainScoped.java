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
 * This write asks the domain guard before it changes anything.
 *
 * The annotation does NOTHING at runtime -- it is not an interceptor binding and
 * removing it changes no behaviour. It is a declaration, and the contract test
 * is what gives it teeth: every non-GET method on the publication resources must
 * either carry this or be named, with a reason, on the test's unscoped list.
 *
 * WHY DECLARE IT AT ALL rather than let each method just call the guard. A
 * missing guard call looks exactly like a method that never needed one, so the
 * endpoint somebody adds next year ships unscoped and nothing surfaces it -- the
 * write succeeds, from the wrong domain, and the only evidence is in the audit
 * trail after the fact. Making the absence a compile-time-visible declaration
 * turns "forgot" into a failing test.
 *
 * An interceptor was the alternative and it is worse here: the guard needs the
 * series or the issue, which is resolved inside the method from a path parameter
 * that is a public id, an issue id or a body field depending on the endpoint. An
 * interceptor would have to re-resolve all three, and a second resolver that can
 * disagree with the first is how the guard ends up protecting a different row
 * from the one being written.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface DomainScoped {
}
