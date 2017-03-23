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
import freemarker.template.TemplateModelException;
import org.apache.commons.lang.StringUtils;
import org.niord.core.aton.LightCharacterService;
import org.niord.core.util.CdiUtils;

import java.io.IOException;
import java.util.Map;

/**
 * Freemarker directive. Emits a light character in the requested format.
 * If the "format" parameter is "verbose", the light character model is translated to text in the current language,
 * otherwise, the light character model is emitted directly.
 * <p>
 * Example of "verbose" translation:
 * <pre>
 *     "Gp.L.Fl(2+1)G. 5s" -> "composite groups of 2 + 1 long flashes in green, repeated every 5. seconds"
 * </pre>
 */
@SuppressWarnings("unused")
public class LightCharacterDirective implements TemplateDirectiveModel {

    private static final String PARAM_LIGHT     = "light";
    private static final String PARAM_FORMAT    = "format";

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(Environment env,
                        Map params,
                        TemplateModel[] loopVars,
                        TemplateDirectiveBody body)
            throws TemplateException, IOException {


        // Get the "light" parameter
        SimpleScalar lightModel = (SimpleScalar)params.get(PARAM_LIGHT);
        if (lightModel == null) {
            throw new TemplateModelException("The 'light' parameter must be specified");
        }
        String lightCharacter = lightModel.getAsString();

        // Get the "format" parameter
        SimpleScalar formatModel = (SimpleScalar)params.get(PARAM_FORMAT);
        boolean verbose = formatModel != null && "verbose".equalsIgnoreCase(formatModel.getAsString());

        try {
            if (verbose && StringUtils.isNotBlank(lightCharacter)) {

                String lang = env.getLocale().getLanguage();
                LightCharacterService lightCharacterService = CdiUtils.getBean(LightCharacterService.class);
                String result = lightCharacterService.formatLightCharacter(lang, lightCharacter);
                env.getOut().write(result);

            } else {
                env.getOut().write(lightCharacter);
            }
        } catch (Exception e) {
            // Prefer robustness over correctness
            env.getOut().write(lightCharacter);
        }
    }

}
