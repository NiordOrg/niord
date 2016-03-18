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

import org.niord.model.IJsonSerializable;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.Arrays;

/**
 * An AtoN OSM seamark node list.
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
 *   &lt;osm version='0.6' generator='JOSM'&gt;
 *     &lt;bounds minlat='34.0662408634219' minlon='-118.736715316772'
 *                maxlat='34.0731374116421' maxlon='-118.73122215271' /&gt;
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
 *     ...
 *   &lt;/osm&gt;
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
@XmlRootElement(name = "osm")
@SuppressWarnings("unused")
public class AtonOsmVo implements IJsonSerializable {

    float version;
    String generator;
    AtonBoundsVo bounds;
    AtonNodeVo[] nodes;

    /**
     * Returns if this OSM entity is a list of valid AtoN seamarks
     * @return if this OSM entity is a list of valid AtoN seamarks
     */
    public boolean validSeamark() {
        return nodes != null &&
                Arrays.stream(nodes).allMatch(AtonNodeVo::validSeamark);
    }

    /** Computes the bbox for all AtoNs */
    public void computeBounds() {
        bounds = AtonBoundsVo.fromAtons(nodes);
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AtonOsmVo atonOsmVo = (AtonOsmVo) o;

        if (Float.compare(atonOsmVo.version, version) != 0) return false;
        if (generator != null ? !generator.equals(atonOsmVo.generator) : atonOsmVo.generator != null) return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        return Arrays.equals(nodes, atonOsmVo.nodes);

    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        int result = (version != +0.0f ? Float.floatToIntBits(version) : 0);
        result = 31 * result + (generator != null ? generator.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(nodes);
        return result;
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    @XmlAttribute
    public float getVersion() {
        return version;
    }

    public void setVersion(float version) {
        this.version = version;
    }

    @XmlAttribute
    public String getGenerator() {
        return generator;
    }

    public void setGenerator(String generator) {
        this.generator = generator;
    }

    @XmlElement(name = "bounds")
    public AtonBoundsVo getBounds() {
        return bounds;
    }

    public void setBounds(AtonBoundsVo bounds) {
        this.bounds = bounds;
    }

    @XmlElement(name = "node")
    public AtonNodeVo[] getNodes() {
        return nodes;
    }

    public void setNodes(AtonNodeVo[] nodes) {
        this.nodes = nodes;
    }
}
