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

import javax.persistence.Cacheable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Lob;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.validation.constraints.NotNull;

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
        @NamedQuery(name="ScriptResource.findAll",
                query="SELECT t FROM ScriptResource t order by lower(t.path)"),
        @NamedQuery(name="ScriptResource.findAllPaths",
                query="SELECT t.path FROM ScriptResource t order by lower(t.path)")
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
    String content;


    /** Constructor **/
    public ScriptResource() {
    }


    /** Constructor **/
    public ScriptResource(ScriptResourceVo scriptResource) {
        this.id = scriptResource.getId();
        this.type = scriptResource.getType();
        this.path = scriptResource.getPath();
        this.content = scriptResource.getContent();
        updateType();
    }


    /** Converts this entity to a value object */
    public ScriptResourceVo toVo() {
        ScriptResourceVo scriptResource = new ScriptResourceVo();
        scriptResource.setId(id);
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
