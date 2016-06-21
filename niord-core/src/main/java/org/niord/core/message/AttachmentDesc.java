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

import javax.persistence.Column;
import javax.persistence.Entity;

/**
 * Localized contents for the Area entity
 */
@Entity
@SuppressWarnings("unused")
public class AttachmentDesc extends DescEntity<Attachment> {

    @Column(length = 1000)
    String caption;

    /** Constructor */
    public AttachmentDesc() {
    }


    /** Constructor */
    public AttachmentDesc(AttachmentDescVo desc) {
        super(desc);
        this.caption = desc.getCaption();
    }


    /** Converts this entity to a value object */
    public AttachmentDescVo toVo() {
        AttachmentDescVo desc = new AttachmentDescVo();
        desc.setLang(lang);
        desc.setCaption(caption);
        return desc;
    }


    /** {@inheritDoc} */
    @Override
    public boolean descDefined() {
        return ILocalizedDesc.fieldsDefined(caption);
    }


    /** {@inheritDoc} */
    @Override
    public void copyDesc(ILocalizedDesc localizedDesc) {
        AttachmentDesc desc = (AttachmentDesc)localizedDesc;
        this.lang = desc.getLang();
        this.caption = desc.getCaption();
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

}

