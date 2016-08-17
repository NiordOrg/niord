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

    // NtM types
    PERMANENT_NOTICE(MainType.NM),
    TEMPORARY_NOTICE(MainType.NM),
    PRELIMINARY_NOTICE(MainType.NM),
    MISCELLANEOUS_NOTICE(MainType.NM),
    
    // NW types
    COASTAL_WARNING(MainType.NW),
    SUBAREA_WARNING(MainType.NW),
    NAVAREA_WARNING(MainType.NW),
    LOCAL_WARNING(MainType.NW);

    MainType mainType;

    /** Constructor */
    Type(MainType mainType) {
        this.mainType = mainType;
    }

    /** Returns the main type, i.e. either NW or NM, of this type */
    public MainType getMainType() {
        return mainType;
    }
}
