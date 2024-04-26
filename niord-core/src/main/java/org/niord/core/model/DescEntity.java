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
package org.niord.core.model;

import org.niord.model.ILocalizable;
import org.niord.model.ILocalizedDesc;

import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;

/**
 * Base class for localizable description entities.
 */
@MappedSuperclass
@SuppressWarnings("unused")
public abstract class DescEntity<E extends ILocalizable> extends BaseEntity<Integer> implements ILocalizedDesc {

    @NotNull
    protected String lang;

    @ManyToOne
    @NotNull
    E entity;

    /** Constructor **/
    public DescEntity() {
    }


    /** Constructor **/
    public DescEntity(ILocalizedDesc desc) {
        this.lang = desc.getLang();
    }


    /** Constructor **/
    public DescEntity(String lang, E entity) {
        this.lang = lang;
        this.entity = entity;
    }


    /** Truncates the string to at most len characters **/
    public static String trunc(String value, int len) {
        if (value != null && value.length() > len) {
            value = value.substring(0, len).trim();
        }
        return value;
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    @Override
    public String getLang() {
        return lang;
    }

    @Override
    public void setLang(String lang) {
        this.lang = lang;
    }

    public E getEntity() {
        return entity;
    }

    public void setEntity(E entity) {
        this.entity = entity;
    }

}
