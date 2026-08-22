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
}
