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
package org.niord.core.message;

import org.niord.core.model.DescEntity;
import org.niord.model.ILocalizedDesc;
import org.niord.model.message.AttachmentDescVo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;

/**
 * Localized contents for the Attachment entity
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
    /***/

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

}

