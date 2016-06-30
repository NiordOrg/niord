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
package org.niord.model.vo;

import io.swagger.annotations.ApiModel;
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
