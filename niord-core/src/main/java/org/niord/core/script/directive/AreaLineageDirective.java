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
import freemarker.ext.beans.BeanModel;
import freemarker.template.SimpleScalar;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;
import org.niord.core.NiordApp;
import org.niord.core.area.Area;
import org.niord.core.message.Message;
import org.niord.core.message.MessageService;
import org.niord.core.message.vo.SystemMessageVo;
import org.niord.core.util.CdiUtils;
import org.niord.core.util.TextUtils;

import java.io.IOException;
import java.util.Map;

import static org.niord.core.promulgation.NavtexPromulgationService.NAVTEX_LINE_LENGTH;

/**
 * This Freemarker directive will format the areas of the message.
 * <p>
 * The area lineage is defined as the area lineages + vicinity.
 * <p>
 * By convention, a list of areas will be emitted top-to-bottom. So, if we have a list with:
 * [ Kattegat -> Danmark, Skagerak -> Danmark, Hamborg -> Tyskland ], the resulting title
 * line should be: "Danmark. Tyskland. Kattegat. Skagerak. Hamborg."
 */
@SuppressWarnings("unused")
public class AreaLineageDirective implements TemplateDirectiveModel {

    private static final String PARAM_MESSAGE   = "message";
    private static final String PARAM_LANG      = "lang";
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


        TemplateModel msgParam = (TemplateModel)params.get(PARAM_MESSAGE);
        SimpleScalar langParam = (SimpleScalar)params.get(PARAM_LANG);
        SimpleScalar formatParam = (SimpleScalar)params.get(PARAM_FORMAT);

        if (msgParam == null) {
            throw new TemplateModelException("The 'message' parameter must be specified");
        }
        String lang = (langParam != null) ? langParam.getAsString() : env.getLocale().getLanguage();
        boolean navtex = formatParam != null && "navtex".equalsIgnoreCase(formatParam.getAsString());

        try {
            SystemMessageVo message = (SystemMessageVo) ((BeanModel)msgParam).getWrappedObject();

            Message msg = new Message(message);

            NiordApp app = CdiUtils.getBean(NiordApp.class);
            MessageService messageService = CdiUtils.getBean(MessageService.class);

            msg.setAreas(messageService.persistedList(Area.class, msg.getAreas()));
            String areaLineage = msg.computeAreaTitle(lang);

            if (navtex) {
                areaLineage = areaLineage
                        .replaceAll("(?i)\\s+(the)\\s+", " ")
                        .replaceAll("(?i)^(the)\\s+", "");

                areaLineage = TextUtils.maxLineLength(areaLineage, NAVTEX_LINE_LENGTH)
                        .toUpperCase()
                        .trim();
            }

            env.getOut().write(areaLineage);

        } catch (Exception e) {
            // Prefer robustness over correctness
        }
    }

}
