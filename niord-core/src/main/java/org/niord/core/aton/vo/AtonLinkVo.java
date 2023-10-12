/*
 * Copyright 2023 GLA UK Research and Development Directive
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
package org.niord.core.aton.vo;

import org.niord.core.aton.AtonLinkType;
import org.niord.core.aton.AtonLinkTypeCategory;
import org.niord.model.IJsonSerializable;

import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.Date;
import java.util.UUID;

/**
 * An AtoN Link VO Type.
 *
 * @author Nikolaos Vastardis (email: Nikolaos.Vastardis@gla-rad.org)
 */
@XmlRootElement(name = "aggregation")
public class AtonLinkVo implements IJsonSerializable  {

    // Class Variables
    private UUID linkId;
    private String name;
    private AtonLinkType linkType;
    private AtonLinkTypeCategory linkCategory;
    private Date created;
    private String[] peers;

    /*************************/
    /** Getters and Setters **/
    /*************************/

    /**
     * Gets link id.
     *
     * @return the link id
     */
    public UUID getLinkId() {
        return linkId;
    }

    /**
     * Sets link id.
     *
     * @param linkId the link id
     */
    public void setLinkId(UUID linkId) {
        this.linkId = linkId;
    }

    /**
     * Gets name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets name.
     *
     * @param name the name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets link type.
     *
     * @return the link type
     */
    public AtonLinkType getLinkType() {
        return linkType;
    }

    /**
     * Sets link type.
     *
     * @param linkType the link type
     */
    public void setLinkType(AtonLinkType linkType) {
        this.linkType = linkType;
    }

    /**
     * Gets link category.
     *
     * @return the link category
     */
    public AtonLinkTypeCategory getLinkCategory() {
        return linkCategory;
    }

    /**
     * Sets link category.
     *
     * @param linkCategory the link category
     */
    public void setLinkCategory(AtonLinkTypeCategory linkCategory) {
        this.linkCategory = linkCategory;
    }

    /**
     * Gets created.
     *
     * @return the created
     */
    public Date getCreated() {
        return created;
    }

    /**
     * Sets created.
     *
     * @param created the created
     */
    public void setCreated(Date created) {
        this.created = created;
    }

    /**
     * Get peers string [ ].
     *
     * @return the string [ ]
     */
    public String[] getPeers() {
        return peers;
    }

    /**
     * Sets peers.
     *
     * @param peers the peers
     */
    public void setPeers(String[] peers) {
        this.peers = peers;
    }
}
