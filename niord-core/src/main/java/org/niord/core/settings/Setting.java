/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.niord.core.settings;

import org.apache.commons.lang.StringUtils;
import org.niord.core.db.JpaJsonAttributeConverter;
import org.niord.model.IJsonSerializable;

import javax.persistence.*;

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
        this.key = key;
        this.value = value;
        this.description = StringUtils.defaultString(description, null);
        this.cached = cached;
        this.web = web;
        this.editable = editable;
        updateType();
    }

    /** Constructor */
    public Setting(Setting template) {
        this.key = template.getKey();
        this.value = template.getValue();
        this.cached = template.isCached();
        this.type = template.getType();
        this.description = template.getDescription();
        this.web = template.isWeb();
        this.editable = template.isEditable();
        this.cached = template.isCached();
        updateType();
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
    /*************************/

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