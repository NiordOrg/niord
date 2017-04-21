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
import freemarker.template.SimpleDate;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;
import org.niord.core.util.TimeUtils;

import java.io.IOException;
import java.util.Date;
import java.util.Map;

/**
 * Will format a date according to the NAVTEX standard
 */
@SuppressWarnings("unused")
public class NavtexDateFormatDirective implements TemplateDirectiveModel {

    private static final String PARAM_DATE = "date";

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(Environment env,
                        Map params,
                        TemplateModel[] loopVars,
                        TemplateDirectiveBody body)
            throws TemplateException, IOException {

        Date date = null;
        SimpleDate dateParam = (SimpleDate)params.get(PARAM_DATE);
        if (dateParam != null && dateParam.getAsDate() != null) {
            date = dateParam.getAsDate();
        }

        try {
            String result = TimeUtils.formatNavtexDate(date);
            env.getOut().write(result);
        } catch (Exception e) {
            // Prefer robustness over correctness
        }
    }
}
