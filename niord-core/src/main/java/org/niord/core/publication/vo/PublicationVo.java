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

package org.niord.core.publication.vo;

import org.niord.model.IJsonSerializable;
import org.niord.model.ILocalizable;

import java.util.List;

/**
 * Defines a publication that may be associated with a message.
 */
public class PublicationVo implements ILocalizable<PublicationDescVo>, IJsonSerializable {

    String publicationId;
    PublicationType type;
    boolean active;
    boolean languageSpecific;
    List<PublicationDescVo> descs;


    /** {@inheritDoc} */
    @Override
    public PublicationDescVo createDesc(String lang) {
        PublicationDescVo desc = new PublicationDescVo();
        desc.setLang(lang);
        checkCreateDescs().add(desc);
        return desc;
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/


    public String getPublicationId() {
        return publicationId;
    }

    public void setPublicationId(String publicationId) {
        this.publicationId = publicationId;
    }

    public PublicationType getType() {
        return type;
    }

    public void setType(PublicationType type) {
        this.type = type;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isLanguageSpecific() {
        return languageSpecific;
    }

    public void setLanguageSpecific(boolean languageSpecific) {
        this.languageSpecific = languageSpecific;
    }

    @Override
    public List<PublicationDescVo> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<PublicationDescVo> descs) {
        this.descs = descs;
    }
}
