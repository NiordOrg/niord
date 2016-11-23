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

package org.niord.model.publication;

import io.swagger.annotations.ApiModel;
import org.niord.model.IJsonSerializable;
import org.niord.model.ILocalizable;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.util.List;

/**
 * Publication Type
 */
@ApiModel(value = "PublicationType", description = "Publication type model")
@XmlRootElement(name = "publicationType")
@XmlType(propOrder = {
        "typeId", "priority", "publish", "descs"
})
public class PublicationTypeVo implements ILocalizable<PublicationTypeDescVo>, IJsonSerializable {

    String typeId;
    Integer priority;
    Boolean publish;
    List<PublicationTypeDescVo> descs;


    /** {@inheritDoc} */
    @Override
    public PublicationTypeDescVo createDesc(String lang) {
        PublicationTypeDescVo desc = new PublicationTypeDescVo();
        desc.setLang(lang);
        checkCreateDescs().add(desc);
        return desc;
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getTypeId() {
        return typeId;
    }

    public void setTypeId(String typeId) {
        this.typeId = typeId;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Boolean getPublish() {
        return publish;
    }

    public void setPublish(Boolean publish) {
        this.publish = publish;
    }

    @Override
    public List<PublicationTypeDescVo> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<PublicationTypeDescVo> descs) {
        this.descs = descs;
    }
}
