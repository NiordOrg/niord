/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.niord.core.settings;

import org.apache.commons.lang.StringUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Used for expanding "${token}" from a Setting.
 */
public class SettingValueExpander {

    private static final int MAX_REPLACEMENT_NO = 5;

    public static final Pattern TOKEN_FORMAT = Pattern.compile(
            "\\$\\{(?<token>[^\\}]+)\\}"
    );

    String value;
    Set<String> replacedTokens = new HashSet<>();

    /** Constructor **/
    public SettingValueExpander(String value) {
        this.value = value;
    }

    /**
     * Returns the next token to be replaced in the Setting value, or null if none left
     * @return the next token to be replaced
     */
    public String nextToken() {
        // Sanity check
        if (value == null) {
            return null;
        }

        Matcher m = TOKEN_FORMAT.matcher(value);
        if (m.find()) {
            String token = m.group("token");

            // To avoid recursion, we never accept the same token being replaced multiple times
            // Furthermore, we restrict the number of replacements to MAX_REPLACEMENT_NO
            if (replacedTokens.contains(token) || replacedTokens.size() >= MAX_REPLACEMENT_NO) {
                return null;
            }
            return token;
        }
        return null;
    }

    /**
     * Replaces the given token with the given value
     * @param token the token to replace
     * @param replacementValue the replacement value
     * @return this
     */
    public SettingValueExpander replaceToken(String token, String replacementValue) {
        // Sanity check
        if (StringUtils.isBlank(value) || StringUtils.isBlank(token) || StringUtils.isBlank(replacementValue)) {
            return this;
        }

        String replaceToken = String.format("${%s}", token);
        value = value.replace(replaceToken, replacementValue);
        replacedTokens.add(token);

        return this;
    }


    public String getValue() {
        return value;
    }
}
