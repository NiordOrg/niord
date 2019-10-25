/*
 * Copyright 2016 Danish Maritime Authority.
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
package org.niord.model.message;

/**
 * Message type
 */
public enum Type {

    /**
     * The Permanent notice.
     */
// NtM types
    PERMANENT_NOTICE(MainType.NM),
    /**
     * Temporary notice type.
     */
    TEMPORARY_NOTICE(MainType.NM),
    /**
     * Preliminary notice type.
     */
    PRELIMINARY_NOTICE(MainType.NM),
    /**
     * Miscellaneous notice type.
     */
    MISCELLANEOUS_NOTICE(MainType.NM),

    /**
     * The Coastal warning.
     */
// NW types
    COASTAL_WARNING(MainType.NW),
    /**
     * Subarea warning type.
     */
    SUBAREA_WARNING(MainType.NW),
    /**
     * Navarea warning type.
     */
    NAVAREA_WARNING(MainType.NW),
    /**
     * Local warning type.
     */
    LOCAL_WARNING(MainType.NW);

    /**
     * The Main type.
     */
    MainType mainType;

    /** Constructor */
    Type(MainType mainType) {
        this.mainType = mainType;
    }

    /**
     * Returns the main type, i.e. either NW or NM, of this type  @return the main type
     */
    public MainType getMainType() {
        return mainType;
    }
}
