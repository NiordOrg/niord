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

package org.niord.core.category;

import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The field template process handles a message fields templates format, exemplified by:
 * <pre>
 *     &lt;message-template&gt;
 *         &lt;field-template field="part.getDesc('da').subject" format="text" update="replace"&gt;
 *             Fyr slukket
 *         &lt;/field-template&gt;
 *     &lt;/message-template&gt;
 * </pre>
 *
 * Important: The format is not a proper XML format. The &lt;field-template&gt; elements are extracted
 * and parsed manually.
 */
public class FieldTemplateProcessor {

    /**
     * Parses the field templates text and returns the list of parsed field templates
     * @param messageTemplatesText the field templates text
     * @return the list of parsed field templates
     */
    public static List<FieldTemplate> parse(String messageTemplatesText) {

        List<FieldTemplate> result = new ArrayList<>();

        // Chop the message template text into <field-template> blocks and parse these
        String txt = messageTemplatesText;
        while (true) {

            int startIndex = txt.indexOf("<field-template");
            if (startIndex == -1) {
                break;
            }

            int endIndex = txt.indexOf("</field-template>", startIndex);
            if (endIndex == -1) {
                break;
            }
            endIndex += "</field-template>".length();

            FieldTemplate fieldTemplate = FieldTemplate.parse(txt.substring(startIndex, endIndex));
            result.add(fieldTemplate);

            txt = txt.substring(endIndex + 1);
        }

        return result;
    }


    /**
     * Represents a field template
     */
    public static class FieldTemplate {

        public static final Pattern FIELD_PATTERN = Pattern.compile("field=\"([^\"]+)");
        public static final Pattern FORMAT_PATTERN = Pattern.compile("format=\"([^\"]+)");
        public static final Pattern UPDATE_PATTERN = Pattern.compile("update=\"([^\"]+)");

        String field;
        String format;
        String update;
        Object content;


        /**
         * Parses the field template text and returns the parsed field template
         * @param fieldTemplateText the field template text
         * @return the parsed field templates
         */
        public static FieldTemplate parse(String fieldTemplateText) {
            fieldTemplateText = fieldTemplateText.trim();
            if (!fieldTemplateText.startsWith("<field-template") || !fieldTemplateText.endsWith("</field-template>")) {
                throw new IllegalArgumentException("Invalid format of field-template " + fieldTemplateText);
            }

            FieldTemplate fieldTemplate = new FieldTemplate();

            int index = fieldTemplateText.indexOf(">");
            String attrs = fieldTemplateText.substring("<field-template".length(), index).trim();
            String content = fieldTemplateText.substring(index + 1, fieldTemplateText.length() - "</field-template>".length());

            Matcher fieldMatcher = FIELD_PATTERN.matcher(attrs);
            if (fieldMatcher.find()) {
                fieldTemplate.field = fieldMatcher.group(1);
            }

            Matcher formatMatcher = FORMAT_PATTERN.matcher(attrs);
            if (formatMatcher.find()) {
                fieldTemplate.format = formatMatcher.group(1);
            }

            Matcher updateMatcher = UPDATE_PATTERN.matcher(attrs);
            if (updateMatcher.find()) {
                fieldTemplate.update = updateMatcher.group(1);
            }

            // Update the content according to the format
            fieldTemplate.content = fieldTemplate.parseContent(content);

            return fieldTemplate;
        }


        /** Update the content based on the format **/
        private Object parseContent(String content) {
            if (StringUtils.isNotBlank(content)) {
                content = content
                        .trim()
                        .replaceAll("\\s+\\.", ".");

                if ("text".equalsIgnoreCase(format) || "boolean".equalsIgnoreCase(format)) {
                    // Trim the text to a proper plain-text format
                    content = cleanUpPlainTextContent(content);
                    if ("boolean".equalsIgnoreCase(format)) {
                        return Boolean.valueOf(content.trim());
                    }
                }
            }
            return content;
        }


        /**
         * Executing templates using Freemarker will produce a lot of extra space that we want to clean up
         * @param content the content to clean up
         * @return the result
         */
        private String cleanUpPlainTextContent(String content) {
            if (content != null) {
                // Trim the text to a proper plain-text format
                content = content
                        // 1. compress all non-newline whitespaces to single space
                        .replaceAll("[\\s&&[^\\n]]+", " ")
                        // 2. remove spaces from beginning or end of lines
                        .replaceAll("(?m)^\\s|\\s$", "")
                        // 3. compress multiple newlines to single newlines
                        .replaceAll("\\n+", "\n")
                        // 4. remove newlines from beginning or end of string
                        .replaceAll("^\n|\n$", "");
            }
            return content;
        }


        /** {@inheritDoc} **/
        @Override
        public String toString() {
            return "<field-template" +
                    (StringUtils.isNotBlank(field) ? " field=\"" + field + "\"" : "") +
                    (StringUtils.isNotBlank(format) ? " format=\"" + format + "\"" : "") +
                    (StringUtils.isNotBlank(update) ? " update=\"" + update + "\"" : "") +
                    ">" + content + "</field-template>";
        }


        public String getField() {
            return field;
        }


        public String getFormat() {
            return format;
        }


        public Object getContent() {
            return content;
        }


        public String getUpdate() {
            return update;
        }
    }
}
