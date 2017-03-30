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

package org.niord.core.script.directive;

import freemarker.core.Environment;
import freemarker.template.SimpleScalar;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;
import org.apache.commons.lang.StringUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

/**
 * Freemarker directive for putting quotes around either the "text" parameter or the body text.
 * The "format" parameter may either be "plain", "angular-in" or "angular-out". The latter
 * is for the angular "<<" type of quotes and suitable for html only.
 */
@SuppressWarnings("unused")
public class QuoteDirective implements TemplateDirectiveModel {

    private static final String PARAM_TEXT      = "text";
    private static final String PARAM_FORMAT    = "format";


    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(Environment env,
                        Map params, TemplateModel[] loopVars,
                        TemplateDirectiveBody body)
            throws TemplateException, IOException {


        // "format" parameter
        SimpleScalar formatModel = (SimpleScalar) params.get(PARAM_FORMAT);
        String format = formatModel != null ? formatModel.getAsString() : "plain";

        // "text" parameter
        SimpleScalar textModel = (SimpleScalar) params.get(PARAM_TEXT);

        if (textModel != null) {
            quoteText(textModel.getAsString(), format, env);
        } else {
            quoteTextBody(format, env, body);
        }
    }


    /**
     * Returns the left and right quotes to use for the given format
     * @param format the format
     * @param left left or right quote
     * @return the left and right quotes to use for the given format
     */
    private String quote(String format, boolean left) {
        switch (StringUtils.defaultString(format)) {
            case "angular":
            case "angular-in":
                return (left) ? "&raquo;" : "&laquo;";
            case "angular-out":
                return (left) ? "&laquo;" : "&raquo;";
            default:
                return "\"";
        }
    }


    /**
     * Quote the contents of the text parameter and emit the result
     * @param text the text to quote
     * @param format the quote format
     * @param env the Freemarker environment
     */
    private void quoteText(String text, String format, Environment env) throws IOException {

        // Only add quotes if the body is non-empty
        if (StringUtils.isNotBlank(text)) {

            text = String.format(
                    "%s%s%s",
                    quote(format, true),
                    text,
                    quote(format, false)
            );

            // Write the result
            BufferedWriter out = new BufferedWriter(env.getOut());
            out.write(text);
            out.flush();
        }
    }


    /**
     * Quote the contents of the directive body and emit the result
     * @param format the quote format
     * @param env the Freemarker environment
     * @param body the body
     */
    private void quoteTextBody(String format, Environment env, TemplateDirectiveBody body) throws IOException, TemplateException {
        // If there is non-empty nested content:
        if (body != null) {
            // Executes the nested body.
            StringWriter bodyWriter = new StringWriter();
            body.render(bodyWriter);

            // Process the result
            String text = bodyWriter.toString().trim();

            quoteText(text, format, env);
        } else {
            throw new RuntimeException("missing body");
        }
    }

}
