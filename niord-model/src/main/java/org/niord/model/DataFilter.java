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
package org.niord.model;

import java.security.Principal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable filter object used to define, in a textual manner, which part of an entity should be copied.<br>
 * Example usage:
 * <ul>
 * <li>When converting an entity class to a value object (VO).</li>
 * </ul>
 * <br>
 * <br>
 * The data filter contains two types of information:
 * <ul>
 * <li>A language: This is assumed to be the same for an entire operation and
 * specifies which localized description records to include. If undefined, all languages are included.</li>
 * <li>A list of fields. The fields can be specific fields, such as "parentId" or more
 * abstract specifications such as "details" or "all".<br>
 * Also, the fields may have a component prefix, such as "Area.parent" or "*.*" </li>
 * </ul>
 */
@SuppressWarnings("unused")
public class DataFilter {

    /**
     * The constant ALL.
     */
// Standard values for specific fields to include
    public static final String ALL          = "*";
    /**
     * The constant PARENT.
     */
    public static final String PARENT       = "parent";
    /**
     * The constant PARENT_ID.
     */
    public static final String PARENT_ID    = "parentId";
    /**
     * The constant CHILDREN.
     */
    public static final String CHILDREN     = "children";
    /**
     * The constant GEOMETRY.
     */
    public static final String GEOMETRY     = "geometry";
    /**
     * The constant DETAILS.
     */
    public static final String DETAILS      = "details";

    private UserResolver user;
    private Set<String> fields = new HashSet<>();
    private String lang;


    /**
     * Constructor. Disable public access.
     */
    private DataFilter() {
    }


    /**
     * Constructor. Disable public access.
     */
    private DataFilter(DataFilter other) {
        this.lang = other.lang;
        this.fields.addAll(other.fields);
        this.user = other.user;
    }


    /**
     * Factory method for creating a new data filter
     *
     * @return the new data filter
     */
    public static DataFilter get() {
        return new DataFilter();
    }


    /**
     * Returns a new DataFilter instance that includes the given fields
     *
     * @param fields enlisting of what should be copied
     * @return a new DataFilter instance that includes the given fields
     */
    public DataFilter fields(String... fields) {
        DataFilter filter = new DataFilter(this);
        filter.fields.addAll(Arrays.asList(fields));
        return filter;
    }


    /**
     * Returns a new DataFilter instance specifying a specific language to include
     *
     * @param lang the language to include
     * @return a new DataFilter instance specifying a specific language to include
     */
    public DataFilter lang(String lang) {
        DataFilter filter = new DataFilter(this);
        filter.lang = lang;
        return filter;
    }


    /**
     * Returns a new DataFilter instance specifying a specific user
     *
     * @param user the current user resolver
     * @return a new DataFilter instance specifying a specific user
     */
    public DataFilter user(UserResolver user) {
        DataFilter filter = new DataFilter(this);
        filter.user = user;
        return filter;
    }


    /**
     * Returns the current language setting of the data filter  @return the lang
     */
    public String getLang() {
        return lang;
    }


    /**
     * Returns a copy of the data filter with fields for the given component.
     * The component part of any field is removed.
     *
     * @param component the component to return the data filter for
     * @return the data filter
     */
    public DataFilter forComponent(String component) {
        if (component == null) {
            return this;
        }
        DataFilter filter = get().lang(lang).user(user);
        fields.forEach(field -> {
            if (!field.contains(".") || !field.startsWith(component + ".")) {
                filter.fields.add(field);
            } else if (field.startsWith(component + ".")) {
                filter.fields.add(field.substring(component.length() + 1));
            }
        });
        return filter;
    }


    /**
     * Returns a copy of the data filter with fields for the given component.
     * The component part of any field is removed.
     *
     * @param componentClass the component to return the data filter for
     * @return the data filter
     */
    public DataFilter forComponent(Class<?> componentClass) {
        return forComponent(componentClass.getSimpleName());
    }


    /**
     * If a user resolver has been associated with the filter, returns the current user principal.
     *
     * @return the current user principal
     */
    public Principal principal() {
        return user != null ? user.getPrincipal() : null;
    }


    /**
     * If a user resolver has been associated with the filter, returns if the user has the given role.
     *
     * @param role the role to check
     * @return if the user has the given rol
     */
    public boolean userInRole(String role) {
        return user != null && user.isUserInRole(role);
    }


    /**
     * Returns if the given language should be included
     *
     * @param lang the lanugage to check
     * @return if the given language should be include
     */
    public boolean includeLang(String lang) {
        return this.lang == null || lang == null || Objects.equals(this.lang, lang);
    }


    /**
     * Returns if the given language should be included
     *
     * @param desc the localizable description entity to check
     * @return if the given language should be include
     */
    public boolean includeLang(ILocalizedDesc desc) {
        return desc != null && includeLang(desc.getLang());
    }


    /**
     * Returns whether to include the given field
     *
     * @param field the field to check
     * @return whether to include the given field
     */
    public boolean includeField(String field) {
        return field != null && (fields.contains(ALL) || fields.contains(field));
    }


    /**
     * Returns whether the field is the only one defined for the filter
     *
     * @param field the field
     * @return whether the field is the only one defined for the filter
     */
    public boolean restrictToFields(String field) {
        return fields.size() == 1 && fields.contains(field);
    }


    /**
     * Returns whether to include any of the given fields
     *
     * @param fields the field to check
     * @return whether to include any the given fields
     */
    public boolean anyOfFields(String... fields) {
        if (this.fields.contains(ALL)) {
            return true;
        }
        for (String field : fields) {
            if (this.fields.contains(field)) {
                return true;
            }
        }
        return false;
    }


    /**
     * Returns whether to include all of the given fields
     *
     * @param fields the field to check
     * @return whether to include all the given fields
     */
    public boolean allOfFields(String... fields) {
        if (this.fields.contains(ALL)) {
            return true;
        }
        for (String field : fields) {
            if (!this.fields.contains(field)) {
                return false;
            }
        }
        return true;
    }


    /**
     * Shortcut method that returns whether to include the parent field
     *
     * @return whether to include the parent field
     */
    public boolean includeParent() {
        return includeField(PARENT);
    }


    /**
     * Shortcut method that returns whether to include the parent id field
     *
     * @return whether to include the parent id field
     */
    public boolean includeParentId() {
        return includeField(PARENT_ID);
    }


    /**
     * Shortcut method that returns whether to include the children field
     *
     * @return whether to include the children field
     */
    public boolean includeChildren() {
        return includeField(CHILDREN);
    }


    /**
     * Shortcut method that returns whether to include the geometry field
     *
     * @return whether to include the geometry field
     */
    public boolean includeGeometry() {
        return includeField(GEOMETRY);
    }

    /**
     * Shortcut method that returns whether to include the details field
     *
     * @return whether to include the details field
     */
    public boolean includeDetails() {
        return includeField(DETAILS);
    }


    /**
     * If an implementation of this Interface is associated with the
     * DataFilter, checks can be made on the current user and roles.
     */
    public interface UserResolver {
        /**
         * Gets principal.
         *
         * @return the principal
         */
        Principal getPrincipal();

        /**
         * Is user in role boolean.
         *
         * @param role the role
         * @return the boolean
         */
        boolean isUserInRole(String role);
    }
}
