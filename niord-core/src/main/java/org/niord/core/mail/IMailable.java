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

package org.niord.core.mail;

import org.apache.commons.lang.StringUtils;

import java.util.Comparator;
import java.util.List;

/**
 * Common interface for classes representing users contactable by email
 */
public interface IMailable {

    /** Returns the e-mail address of the contactable entity **/
    String getEmail();

    /** Returns the full name of the contactable entity **/
    String getName();

    /**
     * Returns the RFC-822 internet address, e.g. name and email.
     * Returns null if undefined.
     * @return the RFC-822 internet address, e.g. name and email
     */
    default String computeInternetAddress() {
        String address = getName();
        String email = getEmail();
        if (StringUtils.isNotBlank(email)) {
            address = StringUtils.isBlank(address) ? email : address + " <" + email + ">";
        }
        return StringUtils.isNotBlank(address) ? address : null;
    }


    /**
     * Sorts the list of mailable entities by email address
     * @param mailableList the list to sort
     * @return a reference to the sorted list
     * @noinspection all
     **/
    static <T extends IMailable> List<T> sortByEmail(List<T> mailableList) {
        if (mailableList != null) {
            mailableList.sort(Comparator.comparing(m -> StringUtils.defaultString(m.getEmail().toLowerCase())));
        }
        return mailableList;
    }

}
