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
package org.niord.core.model;

import org.apache.commons.lang.StringUtils;
import org.niord.model.vo.aton.AtonNodeVo;
import org.niord.model.vo.aton.AtonTagVo;

import javax.persistence.*;
import java.util.*;
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
@NamedQueries({
        @NamedQuery(name  = "AtonNode.deleteAll",
                query = "delete from AtonNode")
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
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = new Date();
        }
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
     * Adds or updates the tag with the given key
     * @param k the key of the tag
     * @param v the value of the tag
     * @return the updated tag
     */
    public AtonTag updateTag(String k, String v) {
        if (v == null) {
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
}
