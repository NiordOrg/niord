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

package org.niord.core.fm.directive;

import freemarker.core.Environment;
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
 * Freemarker directive. Ensures that the contents end with a trailing dot character
 */
@SuppressWarnings("unused")
public class TrailingDotDirective implements TemplateDirectiveModel {

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(Environment env,
                        Map params,
                        TemplateModel[] loopVars,
                        TemplateDirectiveBody body)
            throws TemplateException, IOException {

        // If there is non-empty nested content:
        if (body != null) {
            // Executes the nested body.
            StringWriter bodyWriter = new StringWriter();
            body.render(bodyWriter);

            // Process the result
            String s = bodyWriter.toString();

            if (StringUtils.isNotBlank(s) && !s.trim().endsWith(".")) {
                s = s.trim() + ".";
            }


            // Write the result
            BufferedWriter out = new BufferedWriter(env.getOut());
            out.write(s);
            out.flush();
        }
    }

}
