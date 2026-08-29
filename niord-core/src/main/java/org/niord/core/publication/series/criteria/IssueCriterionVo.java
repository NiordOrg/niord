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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.niord.model.IJsonSerializable;

import java.util.ArrayList;
import java.util.List;

/**
 * One criterion node: a kind, an operator, and a list of stable string operands.
 *
 * The discriminator property is "kind", not "type". Both precedents in this repo
 * use "type" because neither had a collision; here "type" is already the
 * message-type domain word, and a node reading {"type":"messageType"} invites
 * exactly the confusion this estate cannot afford. The discriminator name is a
 * per-hierarchy choice, so this deviates from two instances rather than a rule.
 *
 * values holds stable string keys only -- never surrogate integer ids, never
 * display labels. Labels are resolved live for the criteria chips and captured
 * separately in the frozen envelope, so renaming an area cannot rewrite a stored
 * document or leave a stale caption on a published issue.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = MessageSeriesCriterionVo.class, name = "messageSeries"),
        @JsonSubTypes.Type(value = MessageMainTypeCriterionVo.class, name = "messageMainType"),
        @JsonSubTypes.Type(value = MessageTypeCriterionVo.class, name = "messageType"),
        @JsonSubTypes.Type(value = DomainCriterionVo.class, name = "domain"),
        @JsonSubTypes.Type(value = AreaCriterionVo.class, name = "area"),
        @JsonSubTypes.Type(value = CategoryCriterionVo.class, name = "category"),
        @JsonSubTypes.Type(value = ChartCriterionVo.class, name = "chart"),
})
public abstract class IssueCriterionVo implements IJsonSerializable {

    private CriterionOperator operator = CriterionOperator.IN;
    private List<String> values = new ArrayList<>();

    /** The kind this node represents. Not serialized -- Jackson writes the discriminator. */
    public abstract CriterionKind kind();

    public CriterionOperator getOperator() {
        return operator;
    }

    public void setOperator(CriterionOperator operator) {
        this.operator = operator;
    }

    public List<String> getValues() {
        return values;
    }

    public void setValues(List<String> values) {
        this.values = values == null ? new ArrayList<>() : values;
    }

    /**
     * VALUE equality, and it is load-bearing rather than tidiness.
     *
     * The criteria document is a converted attribute: Hibernate compares the
     * loaded snapshot against the current value to decide whether the row is
     * dirty, and the converter deserializes a FRESH object every time. Without
     * this the two are different instances of an equal document, every flush
     * writes a spurious UPDATE and bumps the version, and a bulk delete followed
     * by that flush fails outright -- which is how it surfaced, as an undo that
     * could not delete a series it had just read.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IssueCriterionVo other) || kind() != other.kind()) {
            return false;
        }
        return operator == other.operator && values.equals(other.values);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(kind(), operator, values);
    }
}
