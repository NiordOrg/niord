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

package org.niord.core.source;

import org.niord.core.model.DescEntity;
import org.niord.core.source.vo.SourceDescVo;
import org.niord.model.ILocalizedDesc;

import javax.persistence.Entity;
import javax.validation.constraints.NotNull;

/**
 * Localized contents for the Source entity
 */
@Entity
@SuppressWarnings("unused")
public class SourceDesc extends DescEntity<Source> {

    @NotNull
    String name;

    String abbreviation;

    /** Constructor */
    public SourceDesc() {
    }


    /** Constructor */
    public SourceDesc(SourceDescVo desc) {
        super(desc);
        this.name = desc.getName();
        this.abbreviation = desc.getAbbreviation();
    }


    /** Converts this entity to a value object */
    public SourceDescVo toVo() {
        SourceDescVo desc = new SourceDescVo();
        desc.setLang(lang);
        desc.setName(name);
        desc.setAbbreviation(abbreviation);
        return desc;
    }


    /** {@inheritDoc} */
    @Override
    public boolean descDefined() {
        return ILocalizedDesc.fieldsDefined(name, abbreviation);
    }


    /** {@inheritDoc} */
    @Override
    public void copyDesc(ILocalizedDesc localizedDesc) {
        SourceDesc desc = (SourceDesc)localizedDesc;
        this.name = desc.getName();
        this.abbreviation = desc.getAbbreviation();
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

    public String getAbbreviation() {
        return abbreviation;
    }

    public void setAbbreviation(String abbreviation) {
        this.abbreviation = abbreviation;
    }
}
