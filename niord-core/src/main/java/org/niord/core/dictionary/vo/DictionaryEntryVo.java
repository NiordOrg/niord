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
package org.niord.core.dictionary.vo;

import org.niord.model.IJsonSerializable;
import org.niord.model.ILocalizable;

import java.util.List;

/**
 * Models a named dictionary entry
 */

public class DictionaryEntryVo implements ILocalizable<DictionaryEntryDescVo>, IJsonSerializable {

    String key;
    List<DictionaryEntryDescVo> descs;

    /** {@inheritDoc} */
    @Override
    public DictionaryEntryDescVo createDesc(String lang) {
        DictionaryEntryDescVo desc = new DictionaryEntryDescVo();
        desc.setLang(lang);
        checkCreateDescs().add(desc);
        return desc;
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    @Override
    public List<DictionaryEntryDescVo> getDescs() {
        return descs;
    }

    public void setDescs(List<DictionaryEntryDescVo> descs) {
        this.descs = descs;
    }
}
