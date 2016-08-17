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
