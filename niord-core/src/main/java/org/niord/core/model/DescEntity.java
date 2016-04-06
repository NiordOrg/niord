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
package org.niord.core.model;

import org.niord.model.ILocalizable;
import org.niord.model.ILocalizedDesc;

import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;

/**
 * Base class for localizable description entities.
 */
@MappedSuperclass
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "lang", "entity_id" }))
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
