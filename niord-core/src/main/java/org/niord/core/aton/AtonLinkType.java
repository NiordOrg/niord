/*
 * Copyright 2023 GLA UK Research and Development Directive
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
package org.niord.core.aton;

/**
 * The AtoN Link Type enum.
 * <p/>
 * As per the requirements of the IHO S-125 and IALA S-201 data products this
 * includes the following types:
 * <ul>
 *     <li>
 *         AGGREGATION
 *     </li>
 *     <li>
 *         ASSOCIATION
 *     </li>
 * </ul>
 *
 * @author Nikolaos Vastardis (email: Nikolaos.Vastardis@gla-rad.org)
 */
public enum AtonLinkType {
    AGGREGATION,
    ASSOCIATION
}
