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

import org.niord.model.IJsonSerializable;

import java.util.ArrayList;
import java.util.List;

/**
 * A series' membership query.
 *
 * Two distinct nulls, and confusing them is a live hazard. A null criteria
 * COLUMN means no query at all -- roughly 48 publications have no membership of
 * any kind. A document with an EMPTY criteria list is a legal query meaning
 * "everything in scope". Confusing them turns a link-only one-off into a series
 * that resolves the entire corpus.
 *
 * The schema version lives inside the document rather than in a sibling column
 * on the series. The frozen snapshot copies this document wholesale, and a
 * version held on the series would leave every frozen copy's version
 * unrecoverable after an edit. The same applies to the export/import round trip,
 * where the document travels alone.
 */
public class IssueCriteriaVo implements IJsonSerializable {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    private Integer schemaVersion = CURRENT_SCHEMA_VERSION;
    private CriteriaMatch match = CriteriaMatch.ALL;
    private List<IssueCriterionVo> criteria = new ArrayList<>();

    public Integer getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(Integer schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public CriteriaMatch getMatch() {
        return match;
    }

    public void setMatch(CriteriaMatch match) {
        this.match = match;
    }

    public List<IssueCriterionVo> getCriteria() {
        return criteria;
    }

    public void setCriteria(List<IssueCriterionVo> criteria) {
        this.criteria = criteria == null ? new ArrayList<>() : criteria;
    }

    /**
     * VALUE equality; see IssueCriterionVo.equals for why it is load-bearing.
     *
     * Hibernate dirty-checks this converted attribute by comparing the loaded
     * snapshot against the current value, and the converter returns a fresh object
     * each time. Identity comparison makes every series carrying criteria dirty on
     * every flush.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IssueCriteriaVo other)) {
            return false;
        }
        return java.util.Objects.equals(schemaVersion, other.schemaVersion)
                && match == other.match
                && java.util.Objects.equals(criteria, other.criteria);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(schemaVersion, match, criteria);
    }

}
