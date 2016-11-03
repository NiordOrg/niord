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

package org.niord.core.source.vo;

import org.niord.model.IJsonSerializable;
import org.niord.model.ILocalizable;

import java.util.List;

/**
 * Defines a source that may be associated with a message.
 */
public class SourceVo implements ILocalizable<SourceDescVo>, IJsonSerializable {

    Integer id;
    boolean active;
    List<SourceDescVo> descs;


    /** {@inheritDoc} */
    @Override
    public SourceDescVo createDesc(String lang) {
        SourceDescVo desc = new SourceDescVo();
        desc.setLang(lang);
        checkCreateDescs().add(desc);
        return desc;
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public List<SourceDescVo> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<SourceDescVo> descs) {
        this.descs = descs;
    }
}
