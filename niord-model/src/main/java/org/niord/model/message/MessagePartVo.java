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
import org.apache.commons.lang.StringUtils;
import org.niord.model.IJsonSerializable;
import org.niord.model.ILocalizable;

import javax.xml.bind.annotation.XmlType;
import java.util.List;

/**
 * Value object for the {@code MessagePart} model entity
 */
@ApiModel(value = "MessagePart", description = "Main NW and NM message part class")
@XmlType(propOrder = {
        "descs"
})
@SuppressWarnings("unused")
public class MessagePartVo implements ILocalizable<MessagePartDescVo>, IJsonSerializable {

    List<MessagePartDescVo> descs;


    /** {@inheritDoc} */
    @Override
    public MessagePartDescVo createDesc(String lang) {
        MessagePartDescVo desc = new MessagePartDescVo();
        desc.setLang(lang);
        checkCreateDescs().add(desc);
        return desc;
    }

    /**
     * Rewrites the rich text description from one repository path to another.
     * This happens when a message is edited and its associated repository folder gets
     * copied to a temporary folder until the message is saved.
     * <p>
     * The repository paths may occur in e.g. embedded images and links.
     */
    public void rewriteDescs(String repoPath1, String repoPath2) {
        if (getDescs() != null && StringUtils.isNotBlank(repoPath1) && StringUtils.isNotBlank(repoPath2)) {
            getDescs().forEach(desc -> {
                if (desc.getDetails() != null && desc.getDetails().contains(repoPath1)) {
                    desc.setDetails(desc.getDetails().replace(repoPath1, repoPath2));
                }
            });
        }
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    @Override
    public List<MessagePartDescVo> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<MessagePartDescVo> descs) {
        this.descs = descs;
    }
}
