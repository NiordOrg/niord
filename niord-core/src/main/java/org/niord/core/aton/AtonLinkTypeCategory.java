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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The AtoN Link Type Category enum.
 * <p/>
 * As per the requirements of the IHO S-125 and IALA S-201 data products this
 * includes the following types:
 * <p>
 * For aggregations:
 *      <ul>
 *          <li>leading line</li>
 *          <li>range system</li>
 *          <li>measured distance</li>
 *          <li>buoy mooring</li>
 *      </ul>
 * </p>
 * <p>
 *     For Associations:
 *      <ul>
 *          <li>channel markings</li>
 *          <li>danger markings</li>
 *      </ul>
 * </p>
 *
 * @author Nikolaos Vastardis (email: Nikolaos.Vastardis@gla-rad.org)
 */
public enum AtonLinkTypeCategory {
    /**
     * The Leading line.
     */
    LEADING_LINE(AtonLinkType.AGGREGATION, "leading line"),
    /**
     * The Range system.
     */
    RANGE_SYSTEM(AtonLinkType.AGGREGATION, "range system"),
    /**
     * The Measure distance.
     */
    MEASURE_DISTANCE(AtonLinkType.AGGREGATION, "measured distance"),
    /**
     * The Buoy mooring.
     */
    BUOY_MOORING(AtonLinkType.AGGREGATION, "buoy mooring"),
    /**
     * The Channel markings.
     */
    CHANNEL_MARKINGS(AtonLinkType.ASSOCIATION, "channel markings"),
    /**
     * The Danger markings.
     */
    DANGER_MARKINGS(AtonLinkType.ASSOCIATION, "danger markings");

    // Enum Variables
    private AtonLinkType atonLinkType;
    private String value;

    AtonLinkTypeCategory(AtonLinkType atonLinkType, String value) {
        this.atonLinkType = atonLinkType;
        this.value = value;
    }

    /**
     * Gets aton link type.
     *
     * @return the aton link type
     */
    public AtonLinkType getAtonLinkType() {
        return atonLinkType;
    }

    /**
     * Gets value.
     *
     * @return the value
     */
    public String getValue() {
        return value;
    }

    /**
     * Returns the AtoN Link Type categories that match the specified link type.
     *
     * @param atonLinkType      The AtoN link type to get the categories for
     * @return the matching AtoN Link Type categories
     */
    public static List<AtonLinkTypeCategory> getCategoriesByType(AtonLinkType atonLinkType) {
        return Arrays.stream(AtonLinkTypeCategory.values())
                .filter(c -> Objects.equals(c.getAtonLinkType(), atonLinkType))
                .collect(Collectors.toList());
    }
}
