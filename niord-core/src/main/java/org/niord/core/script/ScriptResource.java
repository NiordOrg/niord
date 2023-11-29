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

package org.niord.core.script;

import org.apache.commons.lang.StringUtils;
import org.niord.core.model.VersionedEntity;
import org.niord.core.script.vo.ScriptResourceVo;

import jakarta.persistence.Cacheable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Lob;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.validation.constraints.NotNull;

import static org.niord.core.script.ScriptResource.Type.FM;
import static org.niord.core.script.ScriptResource.Type.JS;


/**
 * Represents a database-backed Freemarker template.
 */
@Entity
@Cacheable
@NamedQueries({
        @NamedQuery(name="ScriptResource.findByPath",
                query="SELECT t FROM ScriptResource t where lower(t.path) = lower(:path)"),
        @NamedQuery(name="ScriptResource.findByTypes",
                query="SELECT t FROM ScriptResource t where t.type in (:types) order by t.path"),
        @NamedQuery(name="ScriptResource.findAll",
                query="SELECT t FROM ScriptResource t order by t.path"),
        @NamedQuery(name="ScriptResource.findAllPaths",
                query="SELECT t.path FROM ScriptResource t order by t.path")
})
@SuppressWarnings("unused")
public class ScriptResource extends VersionedEntity<Integer>  {

    public enum Type {
        FM, // Freemarker Template
        JS  // JavaScript Resource
    }

    @NotNull
    @Enumerated(EnumType.STRING)
    Type type;

    @NotNull
    @Column(unique = true)
    String path;

    @Lob
    @Column(name = "content", columnDefinition="longtext")
    String content;


    /** Constructor **/
    public ScriptResource() {
    }


    /** Constructor **/
    public ScriptResource(ScriptResourceVo scriptResource) {
        this.setId(scriptResource.getId());
        this.type = scriptResource.getType();
        this.path = scriptResource.getPath();
        this.content = scriptResource.getContent();
        updateType();
    }


    /** Converts this entity to a value object */
    public ScriptResourceVo toVo() {
        ScriptResourceVo scriptResource = new ScriptResourceVo();
        scriptResource.setId(this.getId());
        scriptResource.setType(type);
        scriptResource.setPath(path);
        scriptResource.setContent(content);
        return scriptResource;
    }


    /** Updates the type from the path file extension **/
    public void updateType() {
        type = path2type(path);
    }


    /** Determines the resource type from the path **/
    public static Type path2type(String path) {
        if (StringUtils.isNotBlank(path)) {
            if (path.toLowerCase().trim().endsWith(".js")) {
                return JS;
            } else if (path.toLowerCase().trim().endsWith(".ftl")) {
                return FM;
            }
        }
        return null;
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String template) {
        this.content = template;
    }
}
