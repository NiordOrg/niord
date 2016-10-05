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
package org.niord.core.aton;

import com.vividsolutions.jts.geom.Geometry;
import org.apache.commons.lang.StringUtils;
import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.annotations.IndexedEmbedded;
import org.niord.core.aton.vo.AtonNodeVo;
import org.niord.core.aton.vo.AtonTagVo;
import org.niord.core.geojson.JtsConverter;
import org.niord.core.model.BaseEntity;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Transient;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * An AtoN OSM seamark node entity.
 *
 * The AtoN model adheres to the OSM seamark specification, please refer to:
 * http://wiki.openstreetmap.org/wiki/Key:seamark
 * and sub-pages.
 *
 * The OSM Node attributes are described at:
 * http://wiki.openstreetmap.org/wiki/Elements
 */
@Entity
@Indexed
@NamedQueries({
        @NamedQuery(name  = "AtonNode.deleteAll",
                query = "delete from AtonNode"),
        @NamedQuery(name  = "AtonNode.findByTag",
                query = "select n from AtonNode n inner join n.tags t where t.k = :key and t.v = :value"),
        @NamedQuery(name  = "AtonNode.findByTagValues",
                query = "select n from AtonNode n inner join n.tags t where t.k = :key and t.v in :values"),
        @NamedQuery(name  = "AtonNode.findByIds",
                query = "select distinct n from AtonNode n left join fetch n.tags t where n.id in :ids")
})
@SuppressWarnings("unused")
public class AtonNode extends BaseEntity<Integer> {

    double lat;
    double lon;
    String user;
    int uid;
    boolean visible;
    int version;
    int changeset;
    Date timestamp;

    @Column(columnDefinition = "GEOMETRY", nullable = false)
    Geometry geometry;

    @IndexedEmbedded
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "atonNode", orphanRemoval = true)
    List<AtonTag> tags = new ArrayList<>();


    /** Constructor */
    public AtonNode() {
    }


    /** Constructor */
    public AtonNode(AtonNodeVo node) {
        Objects.requireNonNull(node);
        this.id = node.getId();
        this.lat = node.getLat();
        this.lon = node.getLon();
        this.user = node.getUser();
        this.uid = node.getUid();
        this.visible = node.isVisible();
        this.version = node.getVersion();
        this.changeset = node.getChangeset();
        this.changeset = node.getChangeset();
        if (node.getTags() != null) {
            setTags(Arrays.stream(node.getTags())
                    .map(t -> new AtonTag(t, AtonNode.this))
                    .collect(Collectors.toList()));

        }
    }


    /** Converts this entity to a value object */
    public AtonNodeVo toVo() {
        AtonNodeVo vo = new AtonNodeVo();
        vo.setId(id == null ? 0 : id);
        vo.setLat(lat);
        vo.setLon(lon);
        vo.setUser(user);
        vo.setUid(uid);
        vo.setVisible(visible);
        vo.setVersion(version);
        vo.setChangeset(changeset);
        vo.setTimestamp(timestamp);
        vo.setTags(tags.stream()
                .map(AtonTag::toVo)
                .toArray(AtonTagVo[]::new));
        return vo;
    }


    /** Ensure that the timestamp is defined */
    @PrePersist
    @PreUpdate
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = new Date();
        }
        geometry = JtsConverter.toJtsPoint(lat, lon);
    }


    /**
     * Returns the AtoN UID
     * @return the AtoN UID
     */
    @Transient
    public String getAtonUid() {
        return getTagValue(AtonTag.TAG_ATON_UID);
    }


    /**
     * Returns the value of the tag with the given key. Returns null if the tag does not exist
     * @param k the key
     * @return the value of the tag with the given key. Returns null if the tag does not exist
     */
    @Transient
    public String getTagValue(String k) {
        AtonTag atonUidTag = getTag(k);
        return atonUidTag == null ? null : atonUidTag.getV();
    }


    /**
     * Returns the tag with the given key. Returns null if the tag does not exist
     * @param k the key
     * @return the tag with the given key. Returns null if the tag does not exist
     */
    @Transient
    public AtonTag getTag(String k) {
        return StringUtils.isBlank(k)
                ? null
                : tags.stream()
                    .filter(t -> k.equals(t.getK()))
                    .findFirst()
                    .orElse(null);
    }


    /**
     * Returns all tags matching the given key pattern
     * @param keyPattern the regular expression
     * @return the elements matching the pattern
     */
    public List<AtonTag> matchingTags(String keyPattern) {
        return tags.stream()
                .filter(t -> t.getK().matches(keyPattern))
                .collect(Collectors.toList());
    }


    /**
     * Adds or updates the tag with the given key
     * @param k the key of the tag
     * @param v the value of the tag
     * @return the updated tag
     */
    public AtonTag updateTag(String k, String v) {
        if (StringUtils.isBlank(v)) {
            return null;
        }

        AtonTag tag = getTag(k);
        if (tag == null) {
            tag = new AtonTag(k, v);
            tag.setAtonNode(this);
            tags.add(tag);
        } else {
            tag.setV(v);
        }
        return tag;
    }


    /**
     * Removes all tags matching the given key pattern
     * @param keyPattern the regular expression
     * @return if any element was removed
     */
    public boolean removeTags(String keyPattern) {
        return tags.removeIf(t -> t.getK().matches(keyPattern));
    }


    /**
     * Checks if the values of the template has changed.
     * Only checks relevant values, such as position and the tags of the template.
     *
     * @param template the template to compare with
     * @return if the AtoN has changed
     */
    @Transient
    public boolean hasChanged(AtonNode template) {
        return Math.abs(template.getLat() - lat) > 0.00001 ||
                Math.abs(template.getLon() - lon) > 0.00001 ||
                template.isVisible() != visible ||
                template.getTags().stream()
                        .anyMatch(t -> !Objects.equals(t.getV(), getTagValue(t.getK())));
    }


    /**
     * Updates the node with values of the template.
     * Tags not present in the template are left unchanged.
     *
     * @param template the template to update with
     */
    public void updateNode(AtonNode template) {
        this.lat = template.getLat();
        this.lon = template.getLon();
        this.user = template.getUser();
        this.uid = template.getUid();
        this.visible = template.isVisible();
        this.version = template.getVersion();
        this.changeset = template.getChangeset();
        this.timestamp = template.getTimestamp();
        template.getTags().forEach(t -> updateTag(t.getK(), t.getV()));
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLon() {
        return lon;
    }

    public void setLon(double lon) {
        this.lon = lon;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public int getUid() {
        return uid;
    }

    public void setUid(int uid) {
        this.uid = uid;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public int getChangeset() {
        return changeset;
    }

    public void setChangeset(int changeset) {
        this.changeset = changeset;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public List<AtonTag> getTags() {
        return tags;
    }

    public void setTags(List<AtonTag> tags) {
        this.tags = tags;
    }

    public Geometry getGeometry() {
        return geometry;
    }

    public void setGeometry(Geometry geometry) {
        this.geometry = geometry;
    }
}
