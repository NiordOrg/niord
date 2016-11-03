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

package org.niord.core.message;

import org.apache.commons.lang.StringUtils;
import org.niord.core.message.vo.MessagePublicationVo;
import org.niord.core.model.BaseEntity;
import org.niord.core.model.IndexedEntity;
import org.niord.core.publication.Publication;
import org.niord.core.publication.PublicationDesc;
import org.niord.core.util.TextUtils;
import org.niord.model.DataFilter;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotNull;

/**
 * Defines a message publication.
 *
 * The parameters specified by this entity should contain a
 * comma-separated list of parameters, i.e. ID's, page number, etc.
 */
@Entity
@SuppressWarnings("unused")
public class MessagePublication extends BaseEntity<Integer> implements IndexedEntity {

    @NotNull
    @ManyToOne
    Message message;

    @NotNull
    @ManyToOne
    Publication publication;

    int indexNo;

    /**
     * Contains a comma-separated list of parameters, i.e. ID's, page number, etc.
     */
    String parameters;

    /**
     * Optionally contains a message specific link
     */
    String link;

    /** Constructor */
    public MessagePublication() {
    }


    /** Constructor */
    public MessagePublication(MessagePublicationVo msgPub) {
        this.parameters = msgPub.getParameters();
        this.link = msgPub.getLink();

        // Initially, set a template publication
        this.publication = new Publication(msgPub.getPublication());
    }


    /** Updates this message publication from another message publication */
    public void updatePublication(MessagePublication msgPub) {
        this.indexNo = msgPub.getIndexNo();
        this.parameters = msgPub.getParameters();
        this.link = msgPub.getLink();
        this.publication = msgPub.getPublication();
    }


    /** Converts this entity to a value object */
    public MessagePublicationVo toVo(DataFilter filter) {
        MessagePublicationVo msgPub = new MessagePublicationVo();
        msgPub.setPublication(publication.toVo(filter));
        msgPub.setParameters(parameters);
        msgPub.setLink(link);
        return msgPub;
    }


    /**
     * Computes the message publication text
     * @param lang the language
     * @param includeExternal whether to include external publications or not
     * @param includeInternal whether to include internal publications or not
     * @return the message publication text
     */
    public String computeMessagePublication(String lang, boolean includeExternal, boolean includeInternal) {

        // Only return a text if the publication is external
        if (publication == null ||
                (publication.isInternal() && !includeInternal) ||
                (!publication.isInternal() && !includeExternal)) {
            return null;
        }

        String result = null;
        PublicationDesc desc = publication.getDesc(lang);
        if (desc != null && StringUtils.isNotBlank(desc.getFormat())) {
            String params = StringUtils.defaultIfBlank(parameters, "");
            result = desc.getFormat().replace("${parameters}", params);
            if (publication.isInternal()) {
                result = "[" + result + "]";
            }
            result = TextUtils.trailingDot(result);

            String link = StringUtils.defaultIfBlank(getLink(), desc.getLink());

            if (StringUtils.isNotBlank(link)) {
                result = String.format("<a href=\"%s\" target=\"_blank\">%s</a>", link, result);
            }
        }

        return result;
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }

    public Publication getPublication() {
        return publication;
    }

    public void setPublication(Publication publication) {
        this.publication = publication;
    }

    @Override
    public int getIndexNo() {
        return indexNo;
    }

    @Override
    public void setIndexNo(int indexNo) {
        this.indexNo = indexNo;
    }

    public String getParameters() {
        return parameters;
    }

    public void setParameters(String parameters) {
        this.parameters = parameters;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }
}
