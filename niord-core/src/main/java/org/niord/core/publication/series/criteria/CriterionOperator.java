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

package org.niord.core.publication.series.criteria;

/**
 * How a criterion's operand list is applied.
 *
 * NOT_IN is defined here but rejected by the validator. Keeping it in the schema
 * means adding it later is purely additive; rejecting it means no untested
 * predicate ships. No production filter negates anything.
 */
public enum CriterionOperator {
    IN,
    NOT_IN
}
