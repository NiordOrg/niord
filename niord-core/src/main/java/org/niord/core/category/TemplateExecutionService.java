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
import org.apache.commons.lang.StringUtils;
import org.niord.core.NiordApp;
import org.niord.core.category.FieldTemplateProcessor.FieldTemplate;
import org.niord.core.category.vo.SystemCategoryVo;
import org.niord.core.dictionary.DictionaryService;
import org.niord.core.message.MessageService;
import org.niord.core.message.vo.SystemMessageVo;
import org.niord.core.promulgation.PromulgationException;
import org.niord.core.promulgation.PromulgationManager;
import org.niord.core.promulgation.PromulgationType;
import org.niord.core.promulgation.PromulgationTypeService;
import org.niord.core.promulgation.vo.BaseMessagePromulgationVo;
import org.niord.core.script.FmTemplateService;
import org.niord.core.script.JsResourceService;
import org.niord.core.script.ScriptResource;
import org.niord.core.service.BaseService;
import org.niord.model.DataFilter;
import org.niord.model.message.MessagePartType;
import org.niord.model.message.MessagePartVo;
import org.slf4j.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import javax.script.*;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Business interface for executing template categories
 */
@ApplicationScoped
@SuppressWarnings("unused")
public class TemplateExecutionService extends BaseService {

    @Inject
    private Logger log;

    @Inject
    FmTemplateService templateService;

    @Inject
    PromulgationTypeService promulgationTypeService;

    @Inject
    PromulgationManager promulgationManager;

    @Inject
    JsResourceService javaScriptService;

    @Inject
    MessageService messageService;

    @Inject
    CategoryService categoryService;

    @Inject
    DictionaryService dictionaryService;

    @Inject
    NiordApp app;


    /**
     * *******************************************
     * parameter types functionality
     * *******************************************
     */

    /**
     * Returns the parameter types
     * @return the parameter types
     */
    public List<ParamType> getParamTypes() {

        return new ArrayList<>(
                em.createNamedQuery("ParamType.findAll", ParamType.class)
                .getResultList());
    }


    /**
     * Returns the parameter type with the given id. Returns null if the parameter type does not exist.
     * @param id the id
     * @return the parameter type with the given id
     */
    public ParamType getParamType(Integer id) {
        return getByPrimaryKey(ParamType.class, id);
    }


    /**
     * Returns the parameter type with the given name. Returns null if the parameter type does not exist.
     * @param name the name
     * @return the parameter type with the given name
     */
    public ParamType getParamTypeByName(String name) {
        try {
            return em.createNamedQuery("ParamType.findByName", ParamType.class)
                    .setParameter("name", name)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Creates a new parameter type from the given template
     * @param type the parameter type value object
     * @return the new entity
     */
    @Transactional
    public ParamType createParamType(ParamType type) {

        // Ensure validity of the type name
        if (StringUtils.isBlank(type.getName())) {
            throw new IllegalArgumentException("Invalid parameter type name: " + type.getName());
        }

        // Replace the dictionary entry values with the persisted once
        updatePersistedValues(type, type);

        type = saveEntity(type);
        log.info("Created parameter type " + type);

        return type;
    }

    /**
     * Updates an existing parameter type from the given template
     * @param type the parameter type value object
     * @return the updated entity
     */
    @Transactional
    public ParamType updateParamType(ParamType type) {

        ParamType original = getParamType(type.getId());

        original.setName(type.getName());

        // Replace the dictionary entry values with the persisted once
        updatePersistedValues(type, original);

        if (original instanceof CompositeParamType && type instanceof CompositeParamType) {
            CompositeParamType from = (CompositeParamType)type;
            CompositeParamType to = (CompositeParamType)original;
            to.getTemplateParams().clear();
            to.getTemplateParams().addAll(from.getTemplateParams());
        }

        original = saveEntity(original);
        log.info("Updated parameter type " + original);

        return original;
    }


    /**
     * Deletes the parameter type with the given id
     * @param id the id of the parameter type to delete
     * @noinspection all
     */
    @Transactional
    public boolean deleteParamType(Integer id) {

        ParamType type = getByPrimaryKey(ParamType.class, id);
        if (type != null) {
            remove(type);
            log.info("Deleted parameter type " + id);
            return true;
        }
        return false;
    }


    /** Updates the persisted values of the parameter type **/
    private void updatePersistedValues(ParamType source, ParamType dest) {
        if (source != null && source instanceof ListParamType && dest != null && dest instanceof ListParamType) {
            ListParamType srclistParamType = (ListParamType)source;
            ListParamType dstlistParamType = (ListParamType)dest;
            dstlistParamType.setValues(dictionaryService.persistedList(srclistParamType.getValues()));
        }
    }


    /**
     * *******************************************
     * Template Execution
     * *******************************************
     */


    /**
     * Executes a message category template on the given message.
     * If no category template is specified, all the category template of the message are used.
     *
     * @param templateCategory the template to execute
     * @param message  the message to apply the template to
     * @param templateParams the template-specific parameters
     * @return the resulting message
     */
    public SystemMessageVo executeTemplate(Category templateCategory, SystemMessageVo message, List templateParams) throws Exception {

        // Sanity check
        if (templateCategory != null && templateCategory.getType() != CategoryType.TEMPLATE) {
            throw new IllegalArgumentException("Only categories of type TEMPLATE are executable");
        }

        // Either use the specified category or the ones associated with the message
        List<Category> templateCategories =
                templateCategory != null
                ? Collections.singletonList(templateCategory)
                : message.getCategories().stream()
                    .map(c -> categoryService.getCategoryDetails(c.getId()))
                    .filter(Objects::nonNull)
                    .filter(c -> c.getType() == CategoryType.TEMPLATE)
                    .collect(Collectors.toList());

        List<MessagePartVo> detailParts = checkCreateDetailParts(message, templateCategories);

        long t0 = System.currentTimeMillis();

        // Adjust the message prior to executing the template
        preExecuteTemplate(message);

        // Create context data to use with Freemarker templates and JavaScript updates
        Map<String, Object> contextData = new HashMap<>();
        contextData.put("languages", app.getLanguages());
        contextData.put("message", message);


        // Execute the category templates one by one
        for (int x = 0; x < templateCategories.size(); x++) {
            Category template = templateCategories.get(x);
            MessagePartVo detailPart = detailParts.get(x);

            Object params = templateParams != null && x < templateParams.size()
                    ? templateParams.get(x)
                    : new HashMap<>();

            contextData.put("template", template.toVo(SystemCategoryVo.class, DataFilter.get()));
            contextData.put("part", detailPart);
            contextData.put("params", params);

            executeScriptResources(template.getScriptResourcePaths(), contextData);
        }


        // Next, for each associated promulgation type, execute any script resources associated with these
        List<PromulgationType> types = promulgationTypeService.getPromulgationTypes(message, true).stream()
                .filter(type -> !type.getScriptResourcePaths().isEmpty())
                .collect(Collectors.toList());

        for (PromulgationType type : types) {
            BaseMessagePromulgationVo promulgation = message.promulgation(type.getTypeId());

            Map<String, Object> promulgationContextData = new HashMap<>(contextData);
            promulgationContextData.put("promulgation", promulgation);
            promulgationContextData.put("promulgationType", promulgation.getType());

            executeScriptResources(type.getScriptResourcePaths(), promulgationContextData);
        }


        // Adjust the message after executing the template
        postExecuteTemplate(message);

        log.info("Executed " + templateCategories.size() + " templates on message " + message.getId()
                + " in " + (System.currentTimeMillis() - t0) + " ms");

        return message;
    }


    /**
     * Executes the list of script resources on the context data
     * @param scriptResourcePaths the list of script resources
     * @param contextData the context data, i.e. current message, part, etc.
     */
    public void executeScriptResources(List<String> scriptResourcePaths, Map<String, Object> contextData) throws Exception {
        for (String scriptResourcePath : scriptResourcePaths) {

            ScriptResource.Type type = ScriptResource.path2type(scriptResourcePath);
            if (type == ScriptResource.Type.JS) {
                // JavaScript update
                evalJavaScriptResource(contextData, scriptResourcePath);
            } else if (type == ScriptResource.Type.FM) {
                // Freemarker Template update
                applyFreemarkerTemplate(contextData, scriptResourcePath);
            }
        }
    }


    /** Ensures that we have at least 1 message part per template category of type DETAILS **/
    private List<MessagePartVo> checkCreateDetailParts(SystemMessageVo message, List<Category> templateCategories) {
        List<MessagePartVo> detailParts = message.partsOfType(MessagePartType.DETAILS);
        if (detailParts.size() < templateCategories.size()) {
            int noToAdd = templateCategories.size() - detailParts.size();
            for (int x = 0; x < noToAdd; x++) {
                int index = detailParts.isEmpty() ? 0 : message.getParts().size();
                message.checkCreatePart(MessagePartType.DETAILS, index);
            }
        }
        return message.partsOfType(MessagePartType.DETAILS);
    }


    /**
     * Adjust the message prior to executing a template
     * @param message the message to adjust
     */
    private void preExecuteTemplate(SystemMessageVo message) throws PromulgationException {

        // Update base data to ensure that we have all language variants
        message = messageService.updateBaseDate(message);

        // Ensure that description records exists for all supported languages
        message.checkCreateDescs(app.getLanguages());

        // Reset all promulgations
        promulgationManager.resetMessagePromulgations(message);

        // If message areas are undefined, compute them from the message geometry.
        // messageService.adjustMessage(message, MessageService.AdjustmentType.AREAS);
    }


    /**
     * Adjust the message after executing a template
     * @param message the message to adjust
     */
    private void postExecuteTemplate(SystemMessageVo message) throws Exception {

        // Update auto-title fields, etc.
        messageService.adjustMessage(message, MessageService.AdjustmentType.AREAS, MessageService.AdjustmentType.TITLE);

        // If there is only one DETAILS message part, hide the subject
        List<MessagePartVo> detailParts = message.partsOfType(MessagePartType.DETAILS);
        if (detailParts.size() >= 1) {
            detailParts.get(0).setHideSubject(detailParts.size() == 1);
        }

        // Allow the promulgation services to clean up
        promulgationManager.messagePromulgationGenerated(message);
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
                .dictionaryNames("message", "template")
                .process();

        List<FieldTemplate> fieldTemplates = FieldTemplateProcessor.parse(fieldTemplateTxt);

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
    private void applyFieldTemplates(List<FieldTemplate> fieldTemplates, Map<String, Object> contextData) {

        ScriptEngine jsEngine = new ScriptEngineManager()
                .getEngineByName("Nashorn");

        // Update the JavaScript engine bindings from the context data
        Bindings bindings = new SimpleBindings();
        contextData.forEach(bindings::put);
        jsEngine.setBindings(bindings, ScriptContext.ENGINE_SCOPE);

        for (FieldTemplate fieldTemplate : fieldTemplates) {

            bindings.put("content", fieldTemplate.getContent());
            String script = getUpdateScript(fieldTemplate);
            try {
                jsEngine.eval(script);
            } catch (ScriptException e) {
                // Apply the result of the field templates
                log.error("Error applying field template " + fieldTemplate + ": " + e.getMessage());
            }
        }

    }


    /** Returns a JavaScript that either assigns or appends the content to the field **/
    private String getUpdateScript(FieldTemplate fieldTemplate) {
        String field = fieldTemplate.getField();

        // Either append or assign
        if ("append".equalsIgnoreCase(fieldTemplate.getUpdate())) {
            // Append the content variable to the field
            return String.format("%s = (%s == null) ? content : %s + '\\n' + content;", field, field, field);

        } else {
            // Assign the content variable to the field
            return field + " = content;";
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
