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
package org.niord.core.util;

import org.apache.commons.lang.StringUtils;
import org.apache.lucene.util.Version;

/**
 * Lucene utility methods
 */
@SuppressWarnings("unused")
public class LuceneUtils {

    public final static Version LUCENE_VERSION = Version.LATEST;

    final static String ACCENTED_CHARS = "ÁÀÄÂáàäâÉÈËÊéèëêÍÌÏÎíìïîÓÒÖÔóòöôÚÙÜÛúùüûÝýÑñ";
    final static String REPLACED_CHARS = "AAAAaaaaEEEEeeeeIIIIiiiiOOOOooooUUUUuuuuYyNn";

    /**
     * No-access constructor
     */
    private LuceneUtils() {
    }

    /**
     * Normalizes the string by replacing all accented chars
     * with non-accented versions
     *
     * @param txt the text to update
     * @return the normalized text
     */
    public static String normalize(String txt) {
        return StringUtils.replaceChars(
                txt,
                ACCENTED_CHARS,
                REPLACED_CHARS);
    }

    /**
     * Normalizes the string by replacing all accented chars
     * with non-accented versions and ensure that lowercase
     * "and" and "or" operators are supported
     *
     * @param txt the text to update
     * @return the normalized text
     */
    public static String normalizeQuery(String txt) {
        if (txt == null) {
            return null;
        }

        return normalize(txt)
                .replaceAll(" or ", " OR ")
                .replaceAll(" and ", " AND ");
    }
}
