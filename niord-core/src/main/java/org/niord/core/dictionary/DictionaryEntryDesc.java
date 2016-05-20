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
package org.niord.core.dictionary;

import org.niord.core.dictionary.vo.DictionaryEntryDescVo;
import org.niord.core.model.DescEntity;
import org.niord.model.ILocalizedDesc;

import javax.persistence.Cacheable;
import javax.persistence.Entity;
import javax.validation.constraints.NotNull;

/**
 * Localized contents for the DictionaryEntry entity
 */
@Entity
@Cacheable
@SuppressWarnings("unused")
public class DictionaryEntryDesc extends DescEntity<DictionaryEntry> {

    @NotNull
    private String value;

    /** Constructor */
    public DictionaryEntryDesc() {
    }


    /** Constructor */
    public DictionaryEntryDesc(DictionaryEntryDescVo desc) {
        super(desc);
        this.value = desc.getValue();
    }


    /** Converts this entity to a value object */
    public DictionaryEntryDescVo toVo() {
        DictionaryEntryDescVo desc = new DictionaryEntryDescVo();
        desc.setLang(lang);
        desc.setValue(value);
        return desc;
    }

    /** {@inheritDoc} */
    @Override
    public boolean descDefined() {
        return ILocalizedDesc.fieldsDefined(value);
    }


    /** {@inheritDoc} */
    @Override
    public void copyDesc(ILocalizedDesc desc) {
        this.value = ((DictionaryEntryDesc)desc).getValue();
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

}
