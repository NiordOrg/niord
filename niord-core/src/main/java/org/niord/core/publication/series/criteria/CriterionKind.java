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
 * The node kinds. The wire name of each is the Jackson discriminator value.
 *
 * Every one of them resolves and queries end to end. Four of them used to be
 * carried as vocabulary the resolver refused, which meant a series could pass
 * validation with a criterion that only failed when somebody pressed publish --
 * the one moment in the whole flow with no way back.
 */
public enum CriterionKind {
    MESSAGE_SERIES("messageSeries"),
    MESSAGE_MAIN_TYPE("messageMainType"),
    MESSAGE_TYPE("messageType"),
    DOMAIN("domain"),
    AREA("area"),
    CATEGORY("category"),
    CHART("chart");

    private final String wireName;

    CriterionKind(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static CriterionKind ofWireName(String name) {
        for (CriterionKind k : values()) {
            if (k.wireName.equals(name)) {
                return k;
            }
        }
        throw new IllegalArgumentException("unknown criterion kind: " + name);
    }
}
