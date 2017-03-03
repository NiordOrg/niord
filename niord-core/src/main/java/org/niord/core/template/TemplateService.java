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

package org.niord.core.template;

import freemarker.template.TemplateException;
import org.apache.commons.lang.StringUtils;
import org.niord.core.NiordApp;
import org.niord.core.aton.AtonFilter;
import org.niord.core.aton.vo.AtonNodeVo;
import org.niord.core.category.Category;
import org.niord.core.category.CategoryService;
import org.niord.core.db.CriteriaHelper;
import org.niord.core.domain.Domain;
import org.niord.core.domain.DomainService;
import org.niord.core.message.vo.SystemMessageVo;
import org.niord.core.script.FmTemplateService;
import org.niord.core.script.JsResourceService;
import org.niord.core.script.ScriptResource;
import org.niord.core.script.ScriptResource.Type;
import org.niord.core.service.BaseService;
import org.niord.core.template.FieldTemplateProcessor.FieldTemplate;
import org.niord.model.message.MessagePartType;
import org.niord.model.message.MessagePartVo;
import org.niord.model.search.PagedSearchResultVo;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
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
import java.util.stream.Collectors;

/**
 * Main interface for accessing and processing message templates
 */
@Stateless
@SuppressWarnings("unused")
public class TemplateService extends BaseService {

    @Inject
    CategoryService categoryService;

    @Inject
    DomainService domainService;

    @Inject
    FmTemplateService templateService;

    @Inject
    JsResourceService javaScriptService;

    @Inject
    NiordApp app;

    @Inject
    Logger log;


    /***************************************/
    /** Template life cycle               **/
    /***************************************/

    /**
     * Returns the template with the given ID, or null if not found
     *
     * @param id the template id
     * @return the template with the given ID, or null if not found
     */
    public Template findById(Integer id) {
        return getByPrimaryKey(Template.class, id);
    }


    /**
     * Returns the paged set of templates matching the search criteria
     *
     * @param params the search criteria
     * @return the paged search result
     */
    public PagedSearchResultVo<Template> searchTemplates(TemplateSearchParams params) {

        long t0 = System.currentTimeMillis();

        PagedSearchResultVo<Template> result = new PagedSearchResultVo<>();

        CriteriaBuilder cb = em.getCriteriaBuilder();

        // First compute the total number of matching mails
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Template> countTemplatesRoot = countQuery.from(Template.class);

        countQuery.select(cb.count(countTemplatesRoot))
                .where(buildQueryPredicates(cb, countQuery, countTemplatesRoot, params))
                .orderBy(cb.desc(countTemplatesRoot.get("created")));

        result.setTotal(em.createQuery(countQuery).getSingleResult());


        // Then, extract the current page of matches
        CriteriaQuery<Template> query = cb.createQuery(Template.class);
        Root<Template> templateRoot = query.from(Template.class);
        query.select(templateRoot)
                .where(buildQueryPredicates(cb, query, templateRoot, params))
                .orderBy(cb.desc(countTemplatesRoot.get("created")));

        List<Template> mails = em.createQuery(query)
                .setMaxResults(params.getMaxSize())
                .setFirstResult(params.getPage() * params.getMaxSize())
                .getResultList();
        result.setData(mails);
        result.updateSize();

        log.info("Search [" + params + "] returned " + result.getSize() + " of " + result.getTotal() + " in "
                + (System.currentTimeMillis() - t0) + " ms");

        return result;
    }


    /**
     * Helper function that translates the search parameters into predicates
     */
    @SuppressWarnings("all")
    private <T> Predicate[] buildQueryPredicates(
            CriteriaBuilder cb,
            CriteriaQuery<T> query,
            Root<Template> templateRoot,
            TemplateSearchParams params) {

        // Build the predicate
        CriteriaHelper<T> criteriaHelper = new CriteriaHelper<>(cb, query);

        // Match the name
        if (StringUtils.isNotBlank(params.getName())) {
            Join<Template, TemplateDesc> descs = templateRoot.join("descs", JoinType.LEFT);
            criteriaHelper.equals(descs.get("lang"), params.getLanguage());
            criteriaHelper.like(descs.get("name"), params.getName());
        }

        // Match category
        if (params.getCategory() != null) {
            Category category = categoryService.getCategoryDetails(params.getCategory());
            if (category != null) {
                Join<Template, Category> categories = templateRoot.join("category", JoinType.LEFT);
                criteriaHelper.add(cb.like(categories.get("lineage"), category.getLineage() + "%"));
            }
        }

        // Match the domain
        if (StringUtils.isNotBlank(params.getDomain())) {
            Domain domain = domainService.findByDomainId(params.getDomain());
            if (domain != null) {
                Join<Template, Domain> domains = templateRoot.join("domains", JoinType.LEFT);
                criteriaHelper.equals(domains.get("domainId"), params.getDomain());
            }
        }

        return criteriaHelper.where();
    }


    /**
     * Returns all templates
     *
     * @return all templates
     */
    public List<Template> findAll() {
        return em.createNamedQuery("Template.findAll", Template.class)
                .getResultList();
    }


    /**
     * Creates a new template based on the template parameter
     *
     * @param template the template to create
     * @return the created template
     */
    public Template createTemplate(Template template) {
        if (template.isPersisted()) {
            throw new IllegalArgumentException("Cannot create existing template " + template.getId());
        }

        // Replace category with persisted entity
        Category category = categoryService.getCategoryDetails(template.getCategory().getId());
        category.getTemplates().add(template);
        template.setCategory(category);

        // Replace domains with persisted entities
        template.setDomains(domainService.persistedDomains(template.getDomains()));

        return saveEntity(template);
    }


    /**
     * Updates the template data from the template parameter
     *
     * @param template the template to update
     * @return the updated template
     */
    public Template updateTemplate(Template template) {
        Template original = findById(template.getId());
        if (original == null) {
            throw new IllegalArgumentException("Cannot update non-existing template " + template.getId());
        }

        // Copy the template data
        original.getScriptResourcePaths().clear();
        template.getScriptResourcePaths().stream()
                .filter(p -> ScriptResource.path2type(p) != null)
                .forEach(p -> original.getScriptResourcePaths().add(p));
        original.setMessageId(template.getMessageId());
        original.copyDescsAndRemoveBlanks(template.getDescs());

        // Replace category with persisted entity
        Category category = categoryService.getCategoryDetails(template.getCategory().getId());
        if (!category.getTemplates().contains(original)) {
            original.getCategory().getTemplates().remove(original);
            category.getTemplates().add(template);
        }
        original.setCategory(category);

        // Replace domains with persisted entities
        original.setDomains(domainService.persistedDomains(template.getDomains()));

        return saveEntity(original);
    }


    /**
     * Deletes the template with the given id
     *
     * @param id the ID of the template to delete
     */
    public boolean deleteTemplate(Integer id) {

        Template template = findById(id);
        if (template != null) {
            template.getCategory().getTemplates().remove(template);
            remove(template);
            return true;
        }
        return false;
    }


    /***************************************/
    /** Template Execution                **/
    /***************************************/


    /**
     * Executes a message template on the given message
     *
     * @param template the template to execute
     * @param message  the message to apply the template to
     * @return the resulting message
     */
    public SystemMessageVo executeTemplate(Template template, SystemMessageVo message) throws Exception {

        long t0 = System.currentTimeMillis();

        // Ensure the presence of a DETAILS message part
        MessagePartVo part = message.checkCreatePart(MessagePartType.DETAILS);

        // Ensure that description records exists for all supported languages
        message.checkCreateDescs(app.getLanguages());

        // Create context data to use with Freemarker templates and JavaScript updates
        Map<String, Object> contextData = new HashMap<>();
        contextData.put("languages", app.getLanguages());
        contextData.put("message", message);
        contextData.put("part", part);
        contextData.put("template", template.toVo());

        for (String scriptResourcePath : template.getScriptResourcePaths()) {
            Type type = ScriptResource.path2type(scriptResourcePath);
            if (type == Type.JS) {
                // JavaScript update
                evalJavaScriptResource(contextData, scriptResourcePath);
            } else if (type == Type.FM) {
                // Freemarker Template update
                applyFreemarkerTemplate(contextData, scriptResourcePath);
            }
        }


        log.info("Executed template " + template.getId() + " on message " + message.getId()
                + " in " + (System.currentTimeMillis() - t0) + " ms");

        return message;
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
        contextData.entrySet().forEach(e -> bindings.put(e.getKey(), e.getValue()));
        jsEngine.setBindings(bindings, ScriptContext.ENGINE_SCOPE);

        for (FieldTemplate fieldTemplate : fieldTemplates) {
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


    /***************************************/
    /** AtonN functionality               **/
    /***************************************/


    /**
     * Resolves all templates that matches the given AtoNs
     * by matching the associated category and parent categories.
     * <p>
     * NB: This is a potentially expensive operation involving iteration of all templates and categories,
     *     including JavaScript evaluation of AtoN filters.
     *     However, given that there are only dozens - and not hundreds - of categories and templates,
     *     we should be fine. Use with care though...
     *
     * @param atons the atons to find templates for
     * @return the matching templates
     */
    public List<Template> resolveAtonTemplates(List<AtonNodeVo> atons) {

        long t0 = System.currentTimeMillis();

        // Start out with all active templates for the current domain
        TemplateSearchParams params = new TemplateSearchParams()
                .domain(domainService.currentDomain().getDomainId())
                .inactive(false);
        List<Template> templates = searchTemplates(params).getData();

        // Resolve matching templates via associated categories. Cache the result by category ID
        Map<Integer, Boolean> includeCategory = new HashMap<>();

        // Filter the templates
        List<Template> result = templates.stream()
                .filter(t -> matchesAtons(atons, t, includeCategory))
                .collect(Collectors.toList());

        log.info("Found " + result.size() + " templates matching " + atons.size() + " AtoNs in "
            + (System.currentTimeMillis() - t0) + " ms");

        return result;
    }


    /**
     * Checks if all AtoNs matches the AtoN filters of the template category lineage
     * @param atons the AtoNs to check
     * @param template the template to test
     * @return if all AtoNs matches the AtoN filters of the template category lineage
     */
    private boolean matchesAtons(List<AtonNodeVo> atons, Template template, Map<Integer, Boolean> includeCategory) {

        // There must be at least one AtoN filter in the template category lineage to qualify
        boolean atonFiltered = false;

        // Check the category and all its parent categories
        for (Category category = template.getCategory(); category != null; category = category.getParent()) {
            if (includeCategory.containsKey(category.getId())) {
                return includeCategory.get(category.getId());
            }
            if (StringUtils.isNotBlank(category.getAtonFilter())) {
                atonFiltered = true;
                boolean matchesAtons = matchesAtons(atons, category.getAtonFilter());
                includeCategory.put(category.getId(), matchesAtons);
                if (!matchesAtons) {
                    return false;
                }
            }
        }

        return atonFiltered;
    }


    /**
     * Checks if all AtoNs matches the AtoN filter
     * @param atons the AtoNs to check
     * @param atonFilter the AtoN filter to test
     * @return if all AtoNs matches the AtoN filter
     */
    private boolean matchesAtons(List<AtonNodeVo> atons, String atonFilter) {
        try {
            AtonFilter filter = AtonFilter.getInstance(atonFilter);
            return filter.matches(atons);
        } catch (ScriptException e) {
            return false;
        }
    }
}
