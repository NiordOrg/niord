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
package org.niord.core.area;

import org.niord.core.model.DescEntity;
import org.niord.model.ILocalizedDesc;
import org.niord.model.message.AreaDescVo;

import javax.persistence.Cacheable;
import javax.persistence.Entity;
import javax.validation.constraints.NotNull;

/**
 * Localized contents for the Area entity
 */
@Entity
@Cacheable
@SuppressWarnings("unused")
public class AreaDesc extends DescEntity<Area> {

    @NotNull
    private String name;

    /** Constructor */
    public AreaDesc() {
    }


    /** Constructor */
    public AreaDesc(AreaDescVo area) {
        super(area);
        this.name = area.getName();
    }


    /** Converts this entity to a value object */
    public AreaDescVo toVo() {
        AreaDescVo desc = new AreaDescVo();
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
        this.name = ((AreaDesc)desc).getName();
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
