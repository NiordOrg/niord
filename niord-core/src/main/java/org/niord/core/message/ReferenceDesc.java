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
package org.niord.core.message;

import org.niord.core.model.DescEntity;
import org.niord.model.ILocalizedDesc;
import org.niord.model.vo.AttachmentDescVo;
import org.niord.model.vo.ReferenceDescVo;

import javax.persistence.Column;
import javax.persistence.Entity;

/**
 * Localized contents for the Reference entity
 */
@Entity
@SuppressWarnings("unused")
public class ReferenceDesc extends DescEntity<Reference> {

    @Column(length = 512)
    String description;

    /** Constructor */
    public ReferenceDesc() {
    }


    /** Constructor */
    public ReferenceDesc(AttachmentDescVo desc) {
        super(desc);
        this.description = desc.getCaption();
    }


    /** Converts this entity to a value object */
    public ReferenceDescVo toVo() {
        ReferenceDescVo desc = new ReferenceDescVo();
        desc.setLang(lang);
        desc.setDescription(description);
        return desc;
    }


    /** {@inheritDoc} */
    @Override
    public boolean descDefined() {
        return ILocalizedDesc.fieldsDefined(description);
    }


    /** {@inheritDoc} */
    @Override
    public void copyDesc(ILocalizedDesc localizedDesc) {
        ReferenceDesc desc = (ReferenceDesc)localizedDesc;
        this.lang = desc.getLang();
        this.description = desc.getDescription();
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

}

