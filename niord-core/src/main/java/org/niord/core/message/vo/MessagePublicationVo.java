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

package org.niord.core.message.vo;

import org.niord.core.publication.vo.PublicationVo;
import org.niord.model.IJsonSerializable;

/**
 * Defines a message publication value object.
 *
 * The parameters specified by this entity should contain a
 * comma-separated list of parameters, i.e. ID's, page number, etc.
 */
@SuppressWarnings("unused")
public class MessagePublicationVo implements IJsonSerializable {

    PublicationVo publication;
    String parameters;
    String link;

    /** Returns if the message publication is valid **/
    public boolean publicationDefined() {
        return publication != null && publication.getId() != null;
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public PublicationVo getPublication() {
        return publication;
    }

    public void setPublication(PublicationVo publication) {
        this.publication = publication;
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
