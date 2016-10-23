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
package org.niord.model.message;

import io.swagger.annotations.ApiModel;
import org.niord.model.DataFilter;
import org.niord.model.IJsonSerializable;
import org.niord.model.ILocalizable;

import javax.xml.bind.annotation.XmlType;
import java.util.List;

/**
 * Defines a reference from one Message to another
 */
@ApiModel(value = "Reference", description = "A message reference")
@XmlType(propOrder = {
        "messageId", "type", "descs"
})
public class ReferenceVo implements ILocalizable<ReferenceDescVo>, IJsonSerializable {

    String messageId;
    ReferenceType type;
    List<ReferenceDescVo> descs;


    /** Returns a filtered copy of this entity **/
    public ReferenceVo copy(DataFilter filter) {
        ReferenceVo reference = new ReferenceVo();
        reference.setMessageId(messageId);
        reference.setType(type);
        reference.setDescs(getDescs(filter));
        return reference;
    }


    /** {@inheritDoc} */
    @Override
    public ReferenceDescVo createDesc(String lang) {
        ReferenceDescVo desc = new ReferenceDescVo();
        desc.setLang(lang);
        checkCreateDescs().add(desc);
        return desc;
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public ReferenceType getType() {
        return type;
    }

    public void setType(ReferenceType type) {
        this.type = type;
    }

    @Override
    public List<ReferenceDescVo> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<ReferenceDescVo> descs) {
        this.descs = descs;
    }
}
