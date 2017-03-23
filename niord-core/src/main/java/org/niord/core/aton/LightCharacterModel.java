/*
 * Copyright 2017 Danish Maritime Authority.
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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents a light character model
 * See e.g. https://en.wikipedia.org/wiki/Light_characteristic
 */
@SuppressWarnings("unused")
public class LightCharacterModel {

    List<LightGroup> lightGroups = new ArrayList<>();

    Integer elevation;  // Metres
    Integer period;     // Seconds
    Integer range;      // Nautical miles

    /** {@inheritDoc} */
    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("[lightGroups={ ");
        str.append(lightGroups.stream().map(LightGroup::toString).collect(Collectors.joining(", "))).append(" }");
        if (elevation != null) {
            str.append(", elevation=").append(elevation);
        }
        if (period != null) {
            str.append(", period=").append(period);
        }
        if (range != null) {
            str.append(", range=").append(range);
        }
        return str.append("]").toString();
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public List<LightGroup> getLightGroups() {
        return lightGroups;
    }

    public Integer getElevation() {
        return elevation;
    }

    public void setElevation(Integer elevation) {
        this.elevation = elevation;
    }

    public Integer getPeriod() {
        return period;
    }

    public void setPeriod(Integer period) {
        this.period = period;
    }

    public Integer getRange() {
        return range;
    }

    public void setRange(Integer range) {
        this.range = range;
    }


    /*************************/
    /** Helper classes      **/
    /*************************/


    /**
     * Helper class that represents a single light group
     */
    public static class LightGroup {

        String phase;
        List<String> colors = new ArrayList<>();
        String morseCode;
        List<Integer> groupSpec = new ArrayList<>();
        boolean grouped;

        /** {@inheritDoc} */
        @Override
        public String toString() {
            StringBuilder str = new StringBuilder();
            str.append("[phase=").append(phase);
            if (colors.size() > 0) {
                str.append(", colors={ ");
                str.append(colors.stream().map(Object::toString).collect(Collectors.joining(", "))).append(" }");
            }
            if (groupSpec.size() > 0) {
                str.append(", groupSpec={ ");
                str.append(groupSpec.stream().map(Object::toString).collect(Collectors.joining(", "))).append(" }");
            }
            if (morseCode != null) {
                str.append(", morseCode=").append(morseCode);
            }
            return str.append("]").toString();
        }

        /*************************/
        /** Getters and Setters **/
        /*************************/

        public String getPhase() {
            return phase;
        }

        public void setPhase(String phase) {
            this.phase = phase;
        }

        public List<String> getColors() {
            return colors;
        }

        public String getMorseCode() {
            return morseCode;
        }

        public void setMorseCode(String morseCode) {
            this.morseCode = morseCode;
        }

        public boolean isGrouped() {
            return grouped;
        }

        public void setGrouped(boolean grouped) {
            this.grouped = grouped;
        }

        public boolean isComposite() {
            return isGrouped() && getGroupSpec().size() > 1;
        }

        public List<Integer> getGroupSpec() {
            return groupSpec;
        }
    }
}
