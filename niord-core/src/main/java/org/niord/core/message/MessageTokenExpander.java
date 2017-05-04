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

package org.niord.core.message;

import org.apache.commons.lang.StringUtils;
import org.niord.model.DataFilter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility method that may be used for expanding message-related tokens in a string,
 * such as "${short-id}", "${title}", etc.
 */
public class MessageTokenExpander {

    final Map<String, String> params;

    private MessageTokenExpander(Map<String, String> params) {
        this.params = params;
    }


    /**
     * Create a new instance of the token-expander for the given message
     * @param message the message
     * @param languages all supported languages
     * @param language optionally, define a current language
     * @return a new instance of the token-expander
     */
    public static MessageTokenExpander getInstance(Message message, List<String> languages, String language) {

        Map<String, String> params = new HashMap<>();

        if (StringUtils.isNotBlank(language)) {
            MessageDesc desc = message.getDescs().isEmpty() ? null : message.getDescs(DataFilter.get().lang(language)).get(0);
            params.put("${title}", desc == null ? "" : StringUtils.defaultString(desc.getTitle()));
        }

        if (languages != null) {
            languages.forEach(lang -> {
                MessageDesc desc = message.getDesc(lang);
                params.put("${title:" + lang + "}", desc == null ? "" : StringUtils.defaultString(desc.getTitle()));
            });
        }

        params.put("${uid}", StringUtils.defaultString(message.getUid()));
        params.put("${id}", message.getId() == null ? "" : message.getId().toString());
        params.put("${short-id}", message.getShortId() == null ? "" : message.getShortId());
        params.put("${number}", message.getNumber() == null ? "" : String.valueOf(message.getNumber()));
        params.put("${number-3-digits}", message.getNumber() == null ? "" : String.format("%03d", message.getNumber()));
        params.put("${main-type}", message.getMainType() == null ? "" : message.getMainType().toString());
        params.put("${main-type-lower}", message.getMainType() == null ? "" : message.getMainType().toString().toLowerCase());

        return new MessageTokenExpander(params);
    }


    /**
     * Register a custom token expansion
     * @param token the token
     * @param value the expanded value
     * @return the token expander
     */
    public MessageTokenExpander token(String token, String value) {
        params.put(StringUtils.defaultString(token), StringUtils.defaultString(value));
        return this;
    }


    /**
     * Expands the tokens, such as "${short-id}" and "${title}" in the text
     * @param text the text to process
     * @return the processed text
     */
    public String expandTokens(String text) {
        if (StringUtils.isNotBlank(text)) {
            for (Map.Entry<String, String> e : params.entrySet()) {
                text = text.replace(e.getKey(), e.getValue()).trim();
            }
        }
        return text;
    }

}
