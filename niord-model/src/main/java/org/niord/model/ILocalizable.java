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

import javax.persistence.Transient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Interface to be implemented by localized entities
 */
public interface ILocalizable<D extends ILocalizedDesc> {

    /**
     * Returns the list of localized descriptions
     * @return the list of localized descriptions
     */
    List<D> getDescs();

    /**
     * Sets the list of localized descriptions
     * @param descs the list of localized descriptions
     */
    void setDescs(List<D> descs);

    /**
     * Returns the list of localized descriptions and creates the list if necessary
     * @return the list of localized descriptions
     */
    default List<D> checkCreateDescs() {
        if (getDescs() == null) {
            setDescs(new ArrayList<>());
        }
        return getDescs();
    }

    /**
     * Returns the list of localized descriptions as specified by the data filter.
     * <p>
     * If no description matches the filter, the first available description is included.
     *
     * @param dataFilter defines the languages to include from the entity
     * @return the list of localized descriptions as specified by the data filter
     */
    @Transient
    default List<D> getDescs(DataFilter dataFilter) {
        // Sanity checks
        if (dataFilter == null || getDescs() == null) {
            return getDescs();
        }

        // Collect the matching descriptions
        List<D> result = getDescs().stream()
                .filter(dataFilter::includeLang)
                .collect(Collectors.toList());

        // If no match is found, pick the first available
        if (result.isEmpty() && !getDescs().isEmpty()) {
            result = Collections.singletonList(getDescs().get(0));
        }
        return result;
    }

    /**
     * Returns the localized description for the given language.
     * Returns null if the description is not defined.
     *
     * @param lang the language
     * @return the localized description for the given language
     */
    default D getDesc(String lang) {
        if (getDescs() != null) {
            return getDescs().stream()
                    .filter(desc -> desc.getLang() == null || desc.getLang().equalsIgnoreCase(lang))
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    /**
     * Creates the localized description for the given language
     * and adds it to the list of description entities.
     *
     * @param lang the language
     * @return the created description
     */
    D createDesc(String lang);


    /**
     * Returns the localized description for the given language.
     * Creates a new description entity if none exists in advance.
     *
     * @param lang the language
     * @return the localized description for the given language
     */
    @SuppressWarnings("unused")
    default D checkCreateDesc(String lang) {
        D desc = getDesc(lang);
        if (desc == null) {
            desc = createDesc(lang);
        }
        return desc;
    }


    /**
     * Copies the descriptive fields of the list of descriptions
     * @param descs the description entities to copy
     */
    default void copyDescs(List<D> descs) {
        if (descs != null && descs.size() > 0) {
            descs.forEach(desc -> checkCreateDesc(desc.getLang()).copyDesc(desc));
        }
    }

    /**
     * Copies the descriptive fields of the list of descriptions.
     * Subsequently removes descriptive entities left blank.
     * @param descs the description entities to copy
     */
    default void copyDescsAndRemoveBlanks(List<D> descs) {
        copyDescs(descs);

        // Remove descriptive entities left blank
        if (getDescs() != null) {
            getDescs().removeIf(desc -> !desc.descDefined());
        }
    }


    /**
     * Sorts the descriptive entities to ensure that the given language is first
     * @param lang the language to sort first
     */
    default void sortDescs(final String lang) {
        if (getDescs() != null && lang != null) {
            getDescs().sort((d1, d2) -> {
                String l1 = (d1 == null) ? null : d1.getLang();
                String l2 = (d2 == null) ? null : d2.getLang();
                if (l1 == null && l2 == null) {
                    return 0;
                } else if (l1 == null) {
                    return 1;
                } else if (l2 == null) {
                    return -1;
                } else if (l1.equals(l2)) {
                    return 0;
                } else {
                    return l1.equals(lang) ? -1 : (l2.equals(lang) ? 1 : 0);
                }
            });
        }
    }
}
