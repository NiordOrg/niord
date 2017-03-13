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
import freemarker.template.SimpleNumber;
import freemarker.template.SimpleScalar;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

/**
 * Freemarker directive for controlling line flow
 */
@SuppressWarnings("unused")
public class LineDirective implements TemplateDirectiveModel {

    private static final String PARAM_MAX_LENGTH = "maxLength";
    private static final String PARAM_CASE = "case";

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(Environment env,
                        Map params, TemplateModel[] loopVars,
                        TemplateDirectiveBody body)
            throws TemplateException, IOException {

        SimpleNumber maxLengthModel = (SimpleNumber) params.get(PARAM_MAX_LENGTH);
        int maxLength = (maxLengthModel == null) ? -1 : maxLengthModel.getAsNumber().intValue();

        SimpleScalar caseModel = (SimpleScalar) params.get(PARAM_CASE);
        boolean lowerCase = (caseModel != null && "lower".equalsIgnoreCase(caseModel.getAsString()));
        boolean upperCase = (caseModel != null && "upper".equalsIgnoreCase(caseModel.getAsString()));

        // If there is non-empty nested content:
        if (body != null) {
            // Executes the nested body.
            StringWriter bodyWriter = new StringWriter();
            body.render(bodyWriter);

            // Process the result
            String s = bodyWriter.toString();
            s = s.replace("\n", " ");
            s = s.replace("\r", " ");
            s = s.replaceAll("\\s+", " ").trim();
            s = s.replace(" ,", ",");
            s = s.replace(" .", ".");

            if (upperCase) {
                s = s.toUpperCase();
            } else if (lowerCase) {
                s = s.toLowerCase();
            }

            if (maxLength != -1) {
                StringBuilder sb = new StringBuilder();
                while (s.length() > maxLength) {
                    String t = s.substring(0, maxLength);
                    int idx = Math.max(t.lastIndexOf(" "), t.lastIndexOf("\n"));
                    sb.append(s.substring(0, idx + 1)).append("\n");
                    s = s.substring(idx + 1);
                }
                sb.append(s);
                s = sb.toString();
            }

            // Write the result
            BufferedWriter out = new BufferedWriter(env.getOut());
            out.write(s);
            out.flush();
        } else {
            throw new RuntimeException("missing body");
        }
    }
}
