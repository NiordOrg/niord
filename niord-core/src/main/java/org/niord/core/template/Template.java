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

import org.niord.core.category.Category;
import org.niord.core.domain.Domain;
import org.niord.core.model.IndexedEntity;
import org.niord.core.model.VersionedEntity;
import org.niord.core.script.ScriptResource;
import org.niord.core.template.vo.TemplateDescVo;
import org.niord.core.template.vo.TemplateVo;
import org.niord.model.DataFilter;
import org.niord.model.ILocalizable;
import org.niord.model.message.CategoryVo;

import javax.persistence.Cacheable;
import javax.persistence.CascadeType;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.OrderColumn;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


/**
 * Entity class for the message templates
 */
@Entity
@Cacheable
@NamedQueries({
        @NamedQuery(name  = "Template.findAll",
                query = "select t from Template t"),
        @NamedQuery(name  = "Template.findByCategoryAndDomain",
                query = "select t from Template t where t.category.id = :categoryId and :domain in t.domains")
})
@SuppressWarnings("unused")
public class Template extends VersionedEntity<Integer> implements ILocalizable<TemplateDesc>, IndexedEntity {

    @NotNull
    @ManyToOne
    Category category;

    @ManyToMany
    List<Domain> domains = new ArrayList<>();

    boolean active = true;

    int indexNo;

    @OrderColumn(name = "indexNo")
    @ElementCollection
    List<String> scriptResourcePaths = new ArrayList<>();

    // Example message ID
    String messageId;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "entity", orphanRemoval = true)
    List<TemplateDesc> descs = new ArrayList<>();

    /** Constructor **/
    public Template() {
    }


    /** Constructor **/
    public Template(TemplateVo template) {
        this.id = template.getId();
        this.category = new Category(template.getCategory());
        this.active = template.isActive();
        template.getScriptResourcePaths().stream()
                .filter(p -> ScriptResource.path2type(p) != null)
                .forEach(p -> this.scriptResourcePaths.add(p));
        this.messageId = template.getMessageId();
        if (!template.getDomains().isEmpty()) {
            this.domains = template.getDomains().stream()
                    .map(Domain::new)
                    .collect(Collectors.toList());
        }
        if (template.getDescs() != null) {
            template.getDescs().stream()
                    .filter(TemplateDescVo::descDefined)
                    .forEach(desc -> createDesc(desc.getLang()).setName(desc.getName()));
        }
    }


    /** Converts this entity to a value object */
    public TemplateVo toVo() {
        return toVo(DataFilter.get());
    }


    /** Converts this entity to a value object */
    public TemplateVo toVo(DataFilter filter) {
        TemplateVo template = new TemplateVo();
        template.setId(id);
        template.setCategory(category.toVo(CategoryVo.class, filter));
        template.setDomains(domains.stream()
            .map(Domain::toVo)
            .collect(Collectors.toList()));
        template.setActive(active);
        template.getScriptResourcePaths().addAll(scriptResourcePaths);
        template.setMessageId(messageId);
        if (!descs.isEmpty()) {
            template.setDescs(getDescs(filter).stream()
                    .map(TemplateDesc::toVo)
                    .collect(Collectors.toList()));
        }
        return template;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public TemplateDesc createDesc(String lang) {
        TemplateDesc desc = new TemplateDesc();
        desc.setLang(lang);
        desc.setEntity(this);
        getDescs().add(desc);
        return desc;
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/


    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public List<Domain> getDomains() {
        return domains;
    }

    public void setDomains(List<Domain> domains) {
        this.domains = domains;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public int getIndexNo() {
        return indexNo;
    }

    @Override
    public void setIndexNo(int indexNo) {
        this.indexNo = indexNo;
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

    @Override
    public List<TemplateDesc> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<TemplateDesc> descs) {
        this.descs = descs;
    }
}
