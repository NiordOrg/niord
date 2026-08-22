package org.niord.core.publication.series.criteria;

/**
 * The node kinds. The wire name of each is the Jackson discriminator value.
 *
 * Only messageSeries and messageType occur in production. The rest are
 * forward-looking vocabulary, carried so that using one later needs no format
 * change.
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
