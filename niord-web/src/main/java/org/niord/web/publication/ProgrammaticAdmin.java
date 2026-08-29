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

package org.niord.web.publication;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This endpoint is admin-only, and says so in code rather than in an annotation.
 *
 * WHY ANY ENDPOINT WOULD. A one-time ticket does not produce a security
 * identity -- it is resolved on the request thread and read back by
 * UserService.isCallerInRole -- so a declarative @RolesAllowed refuses the
 * request before the ticket is ever looked at. An endpoint whose whole purpose
 * is to be opened as a document, by a top-level browser navigation that carries
 * no bearer token, therefore has to be @PermitAll with the role asserted in the
 * body. That is a deliberate and narrow exception, and it is only ever taken for
 * a response that IS a document.
 *
 * The annotation does NOTHING at runtime. It is a declaration, and the tier
 * matrix is what gives it teeth: an @PermitAll route on these resources reads as
 * anonymous to anything inspecting annotations, which is exactly how a route
 * meant to be admin-only ships open. Carrying this makes the tier matrix treat
 * the route as admin AND assert that the body really performs the check, so the
 * two can never drift apart in silence.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ProgrammaticAdmin {
}
