package org.niord.model.vo;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The type of the message series identifier
 */
public enum MainType {
    NW,
    NM;

    /** Returns all the sub-type implied by this main type */
    public Set<Type> getTypes() {
        return Arrays.stream(Type.values())
                .filter(t -> t.getMainType() == this)
                .collect(Collectors.toSet());
    }
}
