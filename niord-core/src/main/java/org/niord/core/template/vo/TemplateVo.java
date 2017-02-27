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

package org.niord.core.template.vo;

import org.niord.core.domain.vo.DomainVo;
import org.niord.model.IJsonSerializable;
import org.niord.model.ILocalizable;
import org.niord.model.message.CategoryVo;

import java.util.ArrayList;
import java.util.List;

/**
 * Value object for message templates
 */
@SuppressWarnings("unused")
public class TemplateVo implements ILocalizable<TemplateDescVo>, IJsonSerializable {

    Integer id;
    CategoryVo category;
    List<DomainVo> domains = new ArrayList<>();
    String templatePath;
    String messageId;
    List<TemplateDescVo> descs;


    /** {@inheritDoc} */
    @Override
    public TemplateDescVo createDesc(String lang) {
        TemplateDescVo desc = new TemplateDescVo();
        desc.setLang(lang);
        checkCreateDescs().add(desc);
        return desc;
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public CategoryVo getCategory() {
        return category;
    }

    public void setCategory(CategoryVo category) {
        this.category = category;
    }

    public List<DomainVo> getDomains() {
        return domains;
    }

    public void setDomains(List<DomainVo> domains) {
        this.domains = domains;
    }

    public String getTemplatePath() {
        return templatePath;
    }

    public void setTemplatePath(String templatePath) {
        this.templatePath = templatePath;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    @Override
    public List<TemplateDescVo> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<TemplateDescVo> descs) {
        this.descs = descs;
    }
}
