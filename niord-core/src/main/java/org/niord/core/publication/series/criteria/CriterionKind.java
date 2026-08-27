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
