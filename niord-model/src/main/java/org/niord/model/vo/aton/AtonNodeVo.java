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
package org.niord.model.vo.aton;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.apache.commons.lang.StringUtils;
import org.niord.model.IJsonSerializable;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.util.Arrays;
import java.util.Date;

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
@XmlRootElement(name = "node")
public class AtonNodeVo implements IJsonSerializable {

    int id;
    double lat;
    double lon;
    String user;
    int uid;
    boolean visible;
    int version;
    int changeset;
    Date timestamp;
    private AtonTagVo[] tags;

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

        if (id != that.id) return false;
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
        result = (int) (id ^ (id >>> 32));
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
    /** Getters and Setters **/
    /*************************/

    @XmlAttribute
    public int getId() {
        return id;
    }

    public void setId(int id) {
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
}
