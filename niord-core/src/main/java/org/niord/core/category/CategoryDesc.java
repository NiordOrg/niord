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
package org.niord.core.category;

import org.niord.core.model.DescEntity;
import org.niord.model.ILocalizedDesc;
import org.niord.model.vo.CategoryDescVo;

import javax.persistence.Cacheable;
import javax.persistence.Entity;
import javax.validation.constraints.NotNull;

/**
 * Localized contents for the Category entity
 */
@Entity
@Cacheable
@SuppressWarnings("unused")
public class CategoryDesc extends DescEntity<Category> {

    @NotNull
    private String name;

    /** Constructor */
    public CategoryDesc() {
    }


    /** Constructor */
    public CategoryDesc(CategoryDescVo desc) {
        super(desc);
        this.name = desc.getName();
    }


    /** Converts this entity to a value object */
    public CategoryDescVo toVo() {
        CategoryDescVo desc = new CategoryDescVo();
        desc.setLang(lang);
        desc.setName(name);
        return desc;
    }

    /** {@inheritDoc} */
    @Override
    public boolean descDefined() {
        return ILocalizedDesc.fieldsDefined(name);
    }


    /** {@inheritDoc} */
    @Override
    public void copyDesc(ILocalizedDesc desc) {
        this.name = ((CategoryDesc)desc).getName();
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
