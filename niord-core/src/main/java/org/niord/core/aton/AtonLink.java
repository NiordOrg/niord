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
package org.niord.core.aton;

import org.hibernate.annotations.Type;
import org.niord.core.aton.vo.AtonLinkVo;
import org.niord.core.model.VersionedEntity;

import jakarta.persistence.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * An AtoN Link object as per the requirements of the IHO S-125 and IALA S-201
 * data products.
 *
 * @author Nikolaos Vastardis (email: Nikolaos.Vastardis@gla-rad.org)
 */
@Entity
@Table(
        indexes = {
                @Index(name = "aton_link_id_k", columnList="linkId"),
                @Index(name = "aton_link_type_k", columnList="linkType"),
                @Index(name = "aton_link_category_k", columnList="linkCategory"),
        }
)
@NamedQueries({
        @NamedQuery(name="AtonLink.findAtonLinksByLinkIds",
                query="SELECT l FROM AtonLink l where l.linkId in (:linkIds)"),
        @NamedQuery(name="AtonLink.findAtonLinksByTypeAndName",
                query="SELECT l FROM AtonLink l where l.name in (:names) and l.linkType = :type"),
        @NamedQuery(name= "AtonLink.findAtonLinksByAtonTag",
                query="SELECT l FROM AtonLink l join l.peers p inner join p.tags t where t.k = :key and t.v in :values")
})
public class AtonLink extends VersionedEntity<Integer> {

    // Class Variable
    @Column(length = 36)
    private UUID linkId;
    private String name;
    private AtonLinkType linkType;
    private AtonLinkTypeCategory linkCategory;
    private int atonCount;
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "aton_link_join_table",
            joinColumns = { @JoinColumn(name = "aton_id") },
            inverseJoinColumns = { @JoinColumn(name = "link_id") }
    )
    private Set<AtonNode> peers;

    /**
     * Populate some AtoN link fields automatically before persisting.
     * One example is the AtoN count that can be used for sorting.
     */
    @PrePersist
    @PreUpdate
    protected void onPersistAndUpdate() {
        this.atonCount = Objects.nonNull(this.peers) ? this.peers.size() : 0;
    }

    /**
     * Constructor
     */
    public AtonLink() {

    }

    /**
     * If no AtoN Link ID is defined, create one.
     */
    public AtonLink checkAssignLinkId() {
        if (Objects.isNull(this.linkId)) {
            this.linkId = UUID.randomUUID();
        }
        return this;
    }

    /**
     * Constructor
     * @param node the node
     */
    public AtonLink(AtonLinkVo node) {
        Objects.requireNonNull(node);
        this.setLinkId(node.getLinkId());
        this.setName(node.getName());
        this.setLinkType(node.getLinkType());
        this.setLinkCategory(node.getLinkCategory());
        if(node.getPeers() != null) {
            setPeers(Arrays.stream(node.getPeers())
                    .map(uid -> new AtonTag(AtonTag.TAG_ATON_UID, uid))
                    .map(uidTag -> {
                        AtonNode ref = new AtonNode();
                        ref.setTags(Collections.singletonList(uidTag));
                        return ref;
                    })
                    .collect(Collectors.toSet()));
        }
    }

    /**
     * Converts this entity to a value object
     * @return the aton aggregation vo
     */
    public AtonLinkVo toVo() {
        AtonLinkVo vo = new AtonLinkVo();
        vo.setLinkId(this.getLinkId());
        vo.setName(this.getName());
        vo.setLinkType(this.getLinkType());
        vo.setLinkCategory(this.getLinkCategory());
        vo.setCreated(this.getCreated());
        vo.setPeers(this.peers.stream()
                .map(AtonNode::getAtonUid)
                .toArray(String[]::new));
        return vo;
    }

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
     * Getters and Setters
     **/

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
     * Sets link category.
     *
     * @param linkCategory the link category
     */
    public void setLinkCategory(AtonLinkTypeCategory linkCategory) {
        this.linkCategory = linkCategory;
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
     * Gets aton count.
     *
     * @return the aton count
     */
    public int getAtonCount() {
        return atonCount;
    }

    /**
     * Sets aton count.
     *
     * @param atonCount the aton count
     */
    public void setAtonCount(int atonCount) {
        this.atonCount = atonCount;
    }

    /**
     * Gets peers.
     *
     * @return the peers
     */
    public Set<AtonNode> getPeers() {
        return peers;
    }

    /**
     * Sets peers.
     *
     * @param peers the peers
     */
    public void setPeers(Set<AtonNode> peers) {
        this.peers = peers;
    }
}
