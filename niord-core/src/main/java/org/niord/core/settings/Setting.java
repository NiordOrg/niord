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
package org.niord.core.settings;

import org.apache.commons.lang.StringUtils;
import org.niord.core.db.JpaJsonAttributeConverter;
import org.niord.model.IJsonSerializable;

import jakarta.persistence.*;

/**
 * Used to persist settings
 */
@Entity
@NamedQueries({
        @NamedQuery(name  = "Setting.findSettingsWithKeys",
                query = "select s from Setting s where s.key in (:keys)"),
        @NamedQuery(name  = "Setting.findAllForWeb",
                query = "select s from Setting s where s.web = true"),
        @NamedQuery(name  = "Setting.findAllEditable",
                query = "select s from Setting s where s.editable = true")
})
public class Setting implements IJsonSerializable {

    public enum Type { String, Password, Integer, Long, Float, Double, Date, Boolean, Path, json}

    @Id
    @Column(name="setting_key", unique = true, nullable = false)
    private String key;

    @Convert(converter = JpaJsonAttributeConverter.class)
    @Column(name="setting_value", length = 2000)
    private Object value;

    @Enumerated(EnumType.STRING)
    private Type type;

    private String description;

    // Determines if the setting will be cached or read directly from the database
    private boolean cached;

    // Determines if the setting will be emitted as a $rootScope variable on site-config.js
    private boolean web;

    // Determines if a system admin can edit the setting on the Admin page.
    private boolean editable;

    /** Constructor */
    public Setting() {
        this(null, null);
    }

    /** Constructor */
    public Setting(String key) {
        this(key, null);
    }

    /** Constructor */
    public Setting(String key, Object value) {
        this(key, value, true);
    }

    /** Constructor */
    public Setting(String key, Object value, boolean cached) {
        this(key, value, null, cached, false, false);
    }

    /** Constructor */
    public Setting(String key, Object value, String description, boolean cached, boolean web, boolean editable) {
        this(key, value, null, description, cached, web, editable);
    }

    /** Designated Constructor */
    public Setting(String key, Object value, Type type, String description, boolean cached, boolean web, boolean editable) {
        this.key = key;
        this.value = value;
        this.type = type;
        this.description = StringUtils.defaultString(description, null);
        this.cached = cached;
        this.web = web;
        this.editable = editable;
        updateType();
    }

    /** Constructor */
    public Setting(Setting template) {
        this(template.getKey(), template.getValue(), template.getType(), template.getDescription(),
                template.isCached(), template.isWeb(), template.isEditable());
    }


    /** Updates the type */
    protected void updateType() {
        if (type == null && value != null) {
            if (value instanceof Integer) {
                type = Type.Integer;
            } else if (value instanceof Long) {
                type = Type.Long;
            } else if (value instanceof Double) {
                type = Type.Double;
            } else if (value instanceof Float) {
                type = Type.Float;
            } else if (value instanceof String) {
                type = Type.String;
            } else if (value instanceof Boolean) {
                type = Type.Boolean;
            } else {
                type = Type.json;
            }
        }
    }


    /*************************/
    /** Method chaining     **/
    /***/

    public Setting key(String key) {
        setKey(key);
        return this;
    }

    public Setting value(Object value) {
        setValue(value);
        return this;
    }

    public Setting description(String description) {
        setDescription(description);
        return this;
    }

    public Setting cached(boolean cached) {
        setCached(cached);
        return this;
    }

    public Setting web(boolean web) {
        setWeb(web);
        return this;
    }

    public Setting editable(boolean editable) {
        setEditable(editable);
        return this;
    }

    public Setting type(Type type) {
        setType(type);
        return this;
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getKey() { return key; }

    public void setKey(String key) { this.key = key; }

    public Object getValue() { return value; }

    public void setValue(Object value) { this.value = value; }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isWeb() {
        return web;
    }

    public void setWeb(boolean web) {
        this.web = web;
    }

    public boolean isEditable() {
        return editable;
    }

    public void setEditable(boolean editable) {
        this.editable = editable;
    }

    public boolean isCached() {
        return cached;
    }

    public void setCached(boolean cached) {
        this.cached = cached;
    }
}
