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
