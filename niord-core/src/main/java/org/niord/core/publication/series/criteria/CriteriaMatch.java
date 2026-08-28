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

package org.niord.core.publication.series.criteria;

/**
 * How the nodes of a document combine.
 *
 * Only ALL exists. It is declared as an enum rather than left implicit so that
 * adding ANY later is additive and does not bump the schema version. All four
 * production filters are top-level conjunctions; the single disjunction in the
 * estate (type == T or type == P) is expressed inside a set-valued node instead.
 */
public enum CriteriaMatch {
    ALL
}
