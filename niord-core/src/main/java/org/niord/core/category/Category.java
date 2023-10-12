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
package org.niord.core.category;

import org.niord.core.category.vo.SystemCategoryVo;
import org.niord.core.domain.Domain;
import org.niord.core.model.TreeBaseEntity;
import org.niord.core.script.ScriptResource;
import org.niord.model.DataFilter;
import org.niord.model.ILocalizable;
import org.niord.model.message.CategoryVo;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Represents a specific named category, part of an category-hierarchy.
 * <p>
 * Categories come in two types, base categories and template categories.
 * The latter type defines the templates used for creating standardized messages.
 */
@Entity
@Cacheable
@NamedQueries({
        @NamedQuery(name="Category.findByLegacyId",
                query = "select c FROM Category c where c.legacyId = :legacyId"),
        @NamedQuery(name  = "Category.findRootCategories",
                query = "select distinct c from Category c left join fetch c.children where c.parent is null order by c.siblingSortOrder"),
        @NamedQuery(name  = "Category.findCategoriesWithDescs",
                query = "select distinct c from Category c left join fetch c.descs  order by c.parent, c.siblingSortOrder"),
        @NamedQuery(name  = "Category.findCategoriesWithIds",
                query = "select distinct c from Category c left join fetch c.descs where c.id in (:ids)"),
        @NamedQuery(name  = "Category.findByMrn",
                query = "select c from Category c left join fetch c.descs where c.mrn = :mrn"),
        @NamedQuery(name  = "Category.findLastUpdated",
                query = "select max(c.updated) from Category c")
})
@SuppressWarnings("unused")
public class Category extends TreeBaseEntity<Category> implements ILocalizable<CategoryDesc> {

    @Enumerated(EnumType.STRING)
    CategoryType type = CategoryType.CATEGORY;

    String legacyId;

    String mrn;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "entity", orphanRemoval = true)
    List<CategoryDesc> descs = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    List<String> editorFields = new ArrayList<>();

    String atonFilter;

    /** The domains. Used for template categories **/
    @ManyToMany
    List<Domain> domains = new ArrayList<>();

    /** Standard fields (e.g. "type", "areas", etc) to when executing this template category **/
    @ElementCollection
    List<String> stdTemplateFields = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn(name = "indexNo")
    List<TemplateParam> templateParams = new ArrayList<>();

    /** The script resources to execute. Used for template categories **/
    @OrderColumn(name = "indexNo")
    @ElementCollection
    List<String> scriptResourcePaths = new ArrayList<>();

    /** Example message ID. Used for template categories **/
    String messageId;


    /** Constructor */
    public Category() {
    }


    /** Constructor */
    public Category(CategoryVo category) {
        this(category, DataFilter.get());
    }


    /** Constructor */
    public Category(CategoryVo category, DataFilter filter) {
        updateCategory(category, filter);
    }


    /** {@inheritDoc} **/
    @Override
    public Category asEntity() {
        return this;
    }


    /** Updates this category from the given category */
    public void updateCategory(CategoryVo category, DataFilter filter) {

        DataFilter compFilter = filter.forComponent(Category.class);

        this.setId(category.getId());
        this.mrn = category.getMrn();
        this.active = category.isActive();

        if (compFilter.includeParent() && category.getParent() != null) {
            setParent(new Category(category.getParent(), filter));
        }

        if (category.getDescs() != null) {
            category.getDescs()
                    .forEach(desc -> createDesc(desc.getLang()).setName(desc.getName()));
        }

        if (category instanceof SystemCategoryVo) {
            SystemCategoryVo sysCategory = (SystemCategoryVo)category;

            this.type = sysCategory.getType();
            this.siblingSortOrder = sysCategory.getSiblingSortOrder() == null ? 0 : sysCategory.getSiblingSortOrder();
            if (compFilter.includeChildren() && sysCategory.getChildren() != null) {
                sysCategory.getChildren().stream()
                        .map(a -> new Category(a, filter))
                        .forEach(this::addChild);
            }
            if (sysCategory.getEditorFields() != null) {
                editorFields.addAll(sysCategory.getEditorFields());
            }
            this.atonFilter = sysCategory.getAtonFilter();
            sysCategory.getScriptResourcePaths().stream()
                    .filter(p -> ScriptResource.path2type(p) != null)
                    .forEach(p -> this.scriptResourcePaths.add(p));
            this.messageId = sysCategory.getMessageId();
            if (!sysCategory.getDomains().isEmpty()) {
                this.domains = sysCategory.getDomains().stream()
                        .map(Domain::new)
                        .collect(Collectors.toList());
            }
            if (sysCategory.getStdTemplateFields() != null) {
                stdTemplateFields.addAll(sysCategory.getStdTemplateFields());
            }
            if (sysCategory.getTemplateParams() != null) {
                setTemplateParams(sysCategory.getTemplateParams().stream()
                        .map(TemplateParam::new)
                        .collect(Collectors.toList()));
            }

        }
    }

    /** Converts this entity to a value object */
    public <C extends  CategoryVo> C toVo(Class<C> clz, DataFilter filter) {

        DataFilter compFilter = filter.forComponent(Category.class);

        C category = newInstance(clz);
        category.setId(this.getId());
        category.setMrn(mrn);
        category.setActive(active);

        if (compFilter.includeParent() && getParent() != null) {
            category.setParent(getParent().toVo(clz, compFilter));
        } else if (compFilter.includeParentId() && getParent() != null) {
            CategoryVo parent = new CategoryVo();
            parent.setId(parent.getId());
            category.setParent(parent);
        }

        if (!descs.isEmpty()) {
            category.setDescs(getDescs(filter).stream()
                    .map(CategoryDesc::toVo)
                    .collect(Collectors.toList()));
        }

        if (category instanceof SystemCategoryVo) {
            SystemCategoryVo sysCategory = (SystemCategoryVo)category;

            sysCategory.setType(type);
            sysCategory.setSiblingSortOrder(siblingSortOrder);

            if (compFilter.includeChildren()) {
                getChildren().forEach(child -> sysCategory.checkCreateChildren().add(child.toVo(SystemCategoryVo.class, compFilter)));
            }

            if (!editorFields.isEmpty()) {
                sysCategory.setEditorFields(new ArrayList<>(editorFields));
            }

            sysCategory.setAtonFilter(atonFilter);
            sysCategory.setDomains(domains.stream()
                    .map(Domain::toVo)
                    .collect(Collectors.toList()));
            sysCategory.getStdTemplateFields().addAll(stdTemplateFields);
            if (!templateParams.isEmpty()) {
                sysCategory.setTemplateParams(templateParams.stream()
                    .map(p -> p.toVo(compFilter))
                    .collect(Collectors.toList()));
            }
            sysCategory.getScriptResourcePaths().addAll(scriptResourcePaths);
            sysCategory.setMessageId(messageId);
        }

        return category;
    }


    /**
     * Checks if the values of the category has changed.
     * Only checks relevant values, not e.g. database id, created date, etc.
     * Hence we do not use equals()
     *
     * @param template the template to compare with
     * @return if the category has changed
     */
    @Transient
    public boolean hasChanged(Category template) {
        return !Objects.equals(type, template.getType()) ||
                !Objects.equals(siblingSortOrder, template.getSiblingSortOrder()) ||
                !Objects.equals(mrn, template.getMrn()) ||
                !Objects.equals(active, template.isActive()) ||
                !Objects.equals(editorFields, template.getEditorFields()) ||
                !Objects.equals(atonFilter, template.getAtonFilter()) ||
                !Objects.equals(messageId, template.getMessageId()) ||
                !Objects.equals(scriptResourcePaths, template.getScriptResourcePaths()) ||
                domainsChanged(template) ||
                templateParamsChanged(template) ||
                descsChanged(template);
    }


    /** Checks if the description has changed */
    private boolean descsChanged(Category template) {
        return descs.size() != template.getDescs().size() ||
                descs.stream()
                    .anyMatch(d -> template.getDesc(d.getLang()) == null ||
                            !Objects.equals(d.getName(), template.getDesc(d.getLang()).getName()));
    }


    /** Checks if the domains have changed */
    private boolean domainsChanged(Category template) {
        return domains.size() != template.getDomains().size() ||
                domains.stream()
                    .anyMatch(d -> template.getDomains()
                            .stream().noneMatch(td -> Objects.equals(td.getDomainId(), d.getDomainId())));
    }


    /** Checks if the template params have changed */
    private boolean templateParamsChanged(Category template) {
        return templateParams.size() != template.getTemplateParams().size() ||
                templateParams.stream()
                    .anyMatch(tp -> template.getTemplateParams()
                            .stream().allMatch(tp::hasChanged));
    }


    /** {@inheritDoc} */
    @Override
    public CategoryDesc createDesc(String lang) {
        CategoryDesc desc = new CategoryDesc();
        desc.setLang(lang);
        desc.setEntity(this);
        getDescs().add(desc);
        return desc;
    }


    /*************************/
    /** Getters and Setters **/
    /***/

    public CategoryType getType() {
        return type;
    }

    public void setType(CategoryType type) {
        this.type = type;
    }

    public String getLegacyId() {
        return legacyId;
    }

    public void setLegacyId(String legacyId) {
        this.legacyId = legacyId;
    }

    public String getMrn() {
        return mrn;
    }

    public void setMrn(String mrn) {
        this.mrn = mrn;
    }

    @Override
    public List<CategoryDesc> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<CategoryDesc> descs) {
        this.descs = descs;
    }

    public List<String> getEditorFields() {
        return editorFields;
    }

    public void setEditorFields(List<String> editorFields) {
        this.editorFields = editorFields;
    }

    public String getAtonFilter() {
        return atonFilter;
    }

    public void setAtonFilter(String atonFilter) {
        this.atonFilter = atonFilter;
    }

    public List<Domain> getDomains() {
        return domains;
    }

    public void setDomains(List<Domain> domains) {
        this.domains = domains;
    }

    public List<String> getStdTemplateFields() {
        return stdTemplateFields;
    }

    public void setStdTemplateFields(List<String> stdTemplateFields) {
        this.stdTemplateFields = stdTemplateFields;
    }

    public List<TemplateParam> getTemplateParams() {
        return templateParams;
    }

    public void setTemplateParams(List<TemplateParam> templateParams) {
        this.templateParams = templateParams;
    }

    public List<String> getScriptResourcePaths() {
        return scriptResourcePaths;
    }

    public void setScriptResourcePaths(List<String> scriptResourcePaths) {
        this.scriptResourcePaths = scriptResourcePaths;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }
}

