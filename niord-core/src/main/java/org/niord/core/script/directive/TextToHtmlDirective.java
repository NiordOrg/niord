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

package org.niord.core.script.directive;

import freemarker.core.Environment;
import freemarker.template.SimpleScalar;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;
import org.niord.core.util.TextUtils;

import java.io.IOException;
import java.util.Map;

/**
 * Freemarker directive. Converts plain text to HTML
 */
@SuppressWarnings("unused")
public class TextToHtmlDirective implements TemplateDirectiveModel {

    private static final String PARAM_TEXT = "text";

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(Environment env,
                        Map params,
                        TemplateModel[] loopVars,
                        TemplateDirectiveBody body)
            throws TemplateException, IOException {


        SimpleScalar txtModel = (SimpleScalar)params.get(PARAM_TEXT);
        if (txtModel == null) {
            throw new TemplateModelException("The 'txt' parameter must be specified");
        }

        try {
            String txt = txtModel.getAsString();

            if (txt != null) {
                env.getOut().write(TextUtils.txt2html(txt));
            }
        } catch (Exception e) {
            // Prefer robustness over correctness
        }
    }

}
