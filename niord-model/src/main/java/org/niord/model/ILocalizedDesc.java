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
package org.niord.model;

/**
 * Interface to be implemented by the descriptive entities of localizable entities
 */
public interface ILocalizedDesc {

    /**
     * Returns the language of this descriptive entity
     * @return the language of this descriptive entity
     */
    String getLang();

    /**
     * Sets the language of this descriptive entity
     * @param lang the language of this descriptive entity
     */
    void setLang(String lang);

    /**
     * Returns if this descriptive entity is defined, i.e. has at least one non-blank field
     * @return if this descriptive entity is defined
     */
    @SuppressWarnings("unused")
    boolean descDefined();

    /**
     * Copies the description values from the desc entity to this entity
     * @param desc the description entity to copy from
     */
    void copyDesc(ILocalizedDesc desc);
    
    /**
     * Utility method that returns if at least one of the given fields in non-blank
     * @param fields the list of fields to check
     * @return if at least one of the given fields in non-blank
     */
    static boolean fieldsDefined(String... fields) {
        for (String field : fields) {
            if (field != null && field.length() > 0) {
                return true;
            }
        }
        return false;
    }
}
