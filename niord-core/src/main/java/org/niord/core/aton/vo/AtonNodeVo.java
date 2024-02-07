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
package org.niord.core.aton.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.apache.commons.lang.StringUtils;
import org.niord.model.IJsonSerializable;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.util.Arrays;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * An AtoN OSM seamark node.
 *
 * The AtoN model adheres to the OSM seamark specification, please refer to:
 * http://wiki.openstreetmap.org/wiki/Key:seamark
 * and sub-pages.
 * <p>
 * The model represents a subset of the OSM model, please refer to:
 * https://github.com/oschrenk/osm/blob/master/osm-io/src/main/resources/OSMSchema.xsd
 * <p>
 * XML Example:
 * <pre>
 *     &lt;node id="672436827" lat="50.8070813" lon="-1.2841124" user="malcolmh" uid="128186" visible="true" version="11" 
 *           changeset="9107813" timestamp="2011-08-23T21:22:36Z"&gt;
 *         &lt;tag k="seamark:buoy_cardinal:category" v="north"/&gt;
 *         &lt;tag k="seamark:buoy_cardinal:colour" v="black;yellow"/&gt;
 *         &lt;tag k="seamark:buoy_cardinal:colour_pattern" v="horizontal"/&gt;
 *         &lt;tag k="seamark:buoy_cardinal:shape" v="pillar"/&gt;
 *         &lt;tag k="seamark:light:character" v="VQ"/&gt;
 *         &lt;tag k="seamark:light:colour" v="white"/&gt;
 *         &lt;tag k="seamark:name" v="Calshot"/&gt;
 *         &lt;tag k="seamark:topmark:colour" v="black"/&gt;
 *         &lt;tag k="seamark:topmark:shape" v="2 cones up"/&gt;
 *         &lt;tag k="seamark:type" v="buoy_cardinal"/&gt;
 *     &lt;/node&gt;
 * </pre>
 *
 * For JSON, we pick a slightly mode compact format, where the tags are serialized as a Map:
 * <pre>
 * {
 *  "id" : 672436827,
 *  "lat" : 50.8070813,
 *  "lon" : -1.2841124,
 *  "user" : "malcolmh",
 *  "uid" : 128186,
 *  "visible" : true,
 *  "version" : 11,
 *  "changeset" : 9107813,
 *  "timestamp" : 1314134556000,
 *  "tags" : {
 *    "seamark:buoy_cardinal:category" : "north",
 *    "seamark:buoy_cardinal:colour" : "black;yellow",
 *    "seamark:buoy_cardinal:colour_pattern" : "horizontal",
 *    "seamark:buoy_cardinal:shape" : "pillar",
 *    "seamark:light:character" : "VQ",
 *    "seamark:light:colour" : "white",
 *    "seamark:name" : "Calshot",
 *    "seamark:topmark:colour" : "black",
 *    "seamark:topmark:shape" : "2 cones up",
 *    "seamark:type" : "buoy_cardinal"
 *    }
 *  }
 * </pre>
 */
@SuppressWarnings("unused")
@XmlRootElement(name = "node")
public class AtonNodeVo implements IJsonSerializable {

    Integer id;
    double lat;
    double lon;
    String user;
    int uid;
    boolean visible;
    int version;
    int changeset;
    Date timestamp;
    AtonTagVo[] tags;

    /**
     * Returns if this AtoN node is a valid seamark
     * @return if this AtoN node is a valid seamark
     */
    public boolean validSeamark() {
        return StringUtils.isNotBlank(user) &&
                timestamp != null &&
                tags != null &&
                Arrays.stream(tags).allMatch(AtonTagVo::validSeamark);
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AtonNodeVo that = (AtonNodeVo) o;

        if (!Objects.equals(id, that.id)) return false;
        if (Double.compare(that.lat, lat) != 0) return false;
        if (Double.compare(that.lon, lon) != 0) return false;
        if (uid != that.uid) return false;
        if (visible != that.visible) return false;
        if (version != that.version) return false;
        if (changeset != that.changeset) return false;
        if (user != null ? !user.equals(that.user) : that.user != null) return false;
        if (timestamp != null ? !timestamp.equals(that.timestamp) : that.timestamp != null) return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        return Arrays.equals(tags, that.tags);

    }

    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("all")
    public int hashCode() {
        int result;
        long temp;
        result = Objects.hashCode(id);
        temp = Double.doubleToLongBits(lat);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(lon);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (user != null ? user.hashCode() : 0);
        result = 31 * result + (int) (uid ^ (uid >>> 32));
        result = 31 * result + (visible ? 1 : 0);
        result = 31 * result + version;
        result = 31 * result + changeset;
        result = 31 * result + (timestamp != null ? timestamp.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(tags);
        return result;
    }

    /*************************/
    /** Matcher functions   **/
    /*************************/

    /**
     * Returns if the AtoN matches the given key pattern
     * @param keyPattern the regular expression
     * @return if the AtoN matches the given key pattern
     */
    public boolean k(String keyPattern) {
        return tags != null && Arrays.stream(tags)
                .anyMatch(t -> t.getK().matches(keyPattern));
    }

    /**
     * Returns if the AtoN matches the given value pattern
     * @param valuePattern the regular expression
     * @return if the AtoN matches the given value pattern
     */
    public boolean v(String valuePattern) {
        return tags != null && Arrays.stream(tags)
                .anyMatch(t -> t.getV().matches(valuePattern));
    }

    /**
     * Returns if the AtoN matches the given key-value pattern
     * @param keyPattern the regular expression
     * @param valuePattern the regular expression
     * @return if the AtoN matches the given key-value pattern
     */
    public boolean kv(String keyPattern, String valuePattern) {
        return tags != null && Arrays.stream(tags)
                .anyMatch(t -> t.getK().matches(keyPattern) && t.getV().matches(valuePattern));
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    @XmlAttribute
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    @XmlAttribute
    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    @XmlAttribute
    public double getLon() {
        return lon;
    }

    public void setLon(double lon) {
        this.lon = lon;
    }

    @XmlAttribute
    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    @XmlAttribute
    public int getUid() {
        return uid;
    }

    public void setUid(int uid) {
        this.uid = uid;
    }

    @XmlAttribute
    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    @XmlAttribute
    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    @XmlAttribute
    public int getChangeset() {
        return changeset;
    }

    public void setChangeset(int changeset) {
        this.changeset = changeset;
    }

    @XmlJavaTypeAdapter(Iso8601DateXmlAdapter.class)
    @XmlAttribute
    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    @JsonProperty("tags")
    @JsonSerialize(using = AtonTagJsonSerialization.Serializer.class)
    @JsonDeserialize(using = AtonTagJsonSerialization.Deserializer.class)
    @XmlElement(name = "tag")
    public AtonTagVo[] getTags() {
        return tags;
    }

    public void setTags(AtonTagVo[] tags) {
        this.tags = tags;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "AtonNodeVo [id=" + id + ", lat=" + lat + ", lon=" + lon + ", user=" + user + ", uid=" + uid + ", visible=" + visible + ", version=" + version
                + ", changeset=" + changeset + ", timestamp=" + timestamp + ", tags=" + Arrays.toString(tags) + "]";
    }
    
    
}
