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

package org.niord.core.fm;

import org.niord.core.fm.vo.FmTemplateVo;
import org.niord.core.model.VersionedEntity;

import javax.persistence.Cacheable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Lob;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.validation.constraints.NotNull;


/**
 * Represents a database-backed Freemarker template.
 */
@Entity
@Cacheable
@NamedQueries({
        @NamedQuery(name="FmTemplate.findByPath",
                query="SELECT t FROM FmTemplate t where lower(t.path) = lower(:path)"),
        @NamedQuery(name="FmTemplate.findAll",
                query="SELECT t FROM FmTemplate t order by lower(t.path)"),
        @NamedQuery(name="FmTemplate.findAllPaths",
                query="SELECT t.path FROM FmTemplate t order by lower(t.path)")
})
@SuppressWarnings("unused")
public class FmTemplate extends VersionedEntity<Integer>  {

    @NotNull
    @Column(unique = true)
    String path;

    @Lob
    String template;


    /** Constructor **/
    public FmTemplate() {
    }


    /** Constructor **/
    public FmTemplate(FmTemplateVo fmTemplate) {
        this.id = fmTemplate.getId();
        this.path = fmTemplate.getPath();
        this.template = fmTemplate.getTemplate();
    }


    /** Converts this entity to a value object */
    public FmTemplateVo toVo() {
        FmTemplateVo fmTemplate = new FmTemplateVo();
        fmTemplate.setId(id);
        fmTemplate.setPath(path);
        fmTemplate.setTemplate(template);
        return fmTemplate;
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }
}
