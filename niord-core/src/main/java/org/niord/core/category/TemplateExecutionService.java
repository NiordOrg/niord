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

import freemarker.template.TemplateException;
import org.niord.core.NiordApp;
import org.niord.core.category.vo.SystemCategoryVo;
import org.niord.core.domain.DomainService;
import org.niord.core.message.MessageService;
import org.niord.core.message.vo.SystemMessageVo;
import org.niord.core.script.FmTemplateService;
import org.niord.core.script.JsResourceService;
import org.niord.core.script.ScriptResource;
import org.niord.core.service.BaseService;
import org.niord.model.DataFilter;
import org.niord.model.message.MessagePartType;
import org.niord.model.message.MessagePartVo;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Business interface for executing template categories
 */
@Stateless
@SuppressWarnings("unused")
public class TemplateExecutionService extends BaseService {

    @Inject
    private Logger log;

    @Inject
    DomainService domainService;

    @Inject
    FmTemplateService templateService;

    @Inject
    JsResourceService javaScriptService;

    @Inject
    MessageService messageService;

    @Inject
    NiordApp app;


    /**
     * Executes a message template on the given message
     *
     * @param templateCategory the template to execute
     * @param message  the message to apply the template to
     * @return the resulting message
     */
    public SystemMessageVo executeTemplate(Category templateCategory, SystemMessageVo message) throws Exception {

        if (templateCategory.getType() != CategoryType.TEMPLATE) {
            throw new IllegalArgumentException("Only categories of type TEMPLATE are executable");
        }

        long t0 = System.currentTimeMillis();

        // Adjust the message prior to executing the template
        preExecuteTemplate(message);

        // Create context data to use with Freemarker templates and JavaScript updates
        Map<String, Object> contextData = new HashMap<>();
        contextData.put("languages", app.getLanguages());
        contextData.put("message", message);
        contextData.put("template", templateCategory.toVo(SystemCategoryVo.class, DataFilter.get()));

        for (String scriptResourcePath : templateCategory.getScriptResourcePaths()) {
            contextData.put("part", message.part(MessagePartType.DETAILS));

            ScriptResource.Type type = ScriptResource.path2type(scriptResourcePath);
            if (type == ScriptResource.Type.JS) {
                // JavaScript update
                evalJavaScriptResource(contextData, scriptResourcePath);
            } else if (type == ScriptResource.Type.FM) {
                // Freemarker Template update
                applyFreemarkerTemplate(contextData, scriptResourcePath);
            }
        }

        // Adjust the message after executing the template
        postExecuteTemplate(message);

        log.info("Executed template " + templateCategory.getId() + " on message " + message.getId()
                + " in " + (System.currentTimeMillis() - t0) + " ms");

        return message;
    }


    /**
     * Adjust the message prior to executing a template
     * @param message the message to adjust
     */
    private void preExecuteTemplate(SystemMessageVo message) {

        // Ensure the presence of a DETAILS message part
        message.checkCreatePart(MessagePartType.DETAILS);

        // Ensure that description records exists for all supported languages
        message.checkCreateDescs(app.getLanguages());

        // If message areas are undefined, compute them from the message geometry.
        messageService.adjustMessage(message, MessageService.AdjustmentType.AREAS);
    }


    /**
     * Adjust the message after executing a template
     * @param message the message to adjust
     */
    private void postExecuteTemplate(SystemMessageVo message) {

        // Update auto-title fields, etc.
        messageService.adjustMessage(message, MessageService.AdjustmentType.TITLE);

        // If there is only one DETAILS message part, hide the subject
        List<MessagePartVo> detailParts = message.parts(MessagePartType.DETAILS);
        if (detailParts.size() == 1) {
            detailParts.get(0).setHideSubject(true);
        }
    }


    /**
     * Executes the Freemarker template at the given path and updates the message accordingly
     *
     * @param contextData        the context data to use in the Freemarker template
     * @param scriptResourcePath the path to the Freemarker template
     */
    private void applyFreemarkerTemplate(
            Map<String, Object> contextData,
            String scriptResourcePath) throws IOException, TemplateException {

        // Run the associated Freemarker template to get a result in the "FieldTemplates" format
        String fieldTemplateTxt = templateService.newFmTemplateBuilder()
                .templatePath(scriptResourcePath)
                .data(contextData)
                .dictionaryNames("message")
                .process();

        List<FieldTemplateProcessor.FieldTemplate> fieldTemplates = FieldTemplateProcessor.parse(fieldTemplateTxt);

        applyFieldTemplates(fieldTemplates, contextData);
    }


    /**
     * Updates the fields of the message using the FieldTemplate templates.
     * <p>
     * A FieldTemplate field may have the format "message.promulgation('twitter').tweet"
     * and will be assigned the body content of the field template.
     *
     * @param fieldTemplates the field templates to apply
     */
    private void applyFieldTemplates(List<FieldTemplateProcessor.FieldTemplate> fieldTemplates, Map<String, Object> contextData) {

        ScriptEngine jsEngine = new ScriptEngineManager()
                .getEngineByName("Nashorn");

        // Update the JavaScript engine bindings from the context data
        Bindings bindings = new SimpleBindings();
        contextData.entrySet().forEach(e -> bindings.put(e.getKey(), e.getValue()));
        jsEngine.setBindings(bindings, ScriptContext.ENGINE_SCOPE);

        for (FieldTemplateProcessor.FieldTemplate fieldTemplate : fieldTemplates) {
            bindings.put("content", fieldTemplate.getContent());
            String script = fieldTemplate.getField() + " = content;";
            try {
                jsEngine.eval(script);
            } catch (ScriptException e) {
                // Apply the result of the field templates
                log.error("Error applying field template " + fieldTemplate + ": " + e.getMessage());
            }
        }

    }


    /**
     * Evaluates the JavaScript resource at the given path
     *
     * @param contextData        the context data to use in the Freemarker template
     * @param scriptResourcePath the path to the Freemarker template
     */
    private void evalJavaScriptResource(Map<String, Object> contextData, String scriptResourcePath) throws Exception {

        javaScriptService.newJsResourceBuilder()
                .resourcePath(scriptResourcePath)
                .data(contextData)
                .evaluate();
    }

}
