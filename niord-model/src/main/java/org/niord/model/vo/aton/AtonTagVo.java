package org.niord.model.vo.aton;

import org.apache.commons.lang.StringUtils;
import org.niord.model.IJsonSerializable;

import javax.xml.bind.annotation.XmlAttribute;

/**
 * An AtoN OSM seamark node tag.
 *
 * The AtoN model adheres to the OSM seamark specification, please refer to:
 * http://wiki.openstreetmap.org/wiki/Key:seamark
 * and sub-pages.
 * <p>
 * The model represents a subset of the OSM model, please refer to:
 * https://github.com/oschrenk/osm/blob/master/osm-io/src/main/resources/OSMSchema.xsd
 * <p>
 * Example:
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
 */
public class AtonTagVo implements IJsonSerializable {

    String k;
    String v;

    /** No-arg constructor */
    public AtonTagVo() {
    }

    /** Key-value constructor */
    public AtonTagVo(String k, String v) {
        this.k = k;
        this.v = v;
    }

    /**
     * Returns if this AtoN node tag is a valid seamark tag
     * @return if this AtoN node tag is a valid seamark tag
     */
    public boolean validSeamark() {
        return StringUtils.isNotBlank(k) &&
                StringUtils.isNotBlank(v) &&
                k.startsWith("seamark:");
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AtonTagVo atonTagVo = (AtonTagVo) o;

        if (k != null ? !k.equals(atonTagVo.k) : atonTagVo.k != null) return false;
        return v != null ? v.equals(atonTagVo.v) : atonTagVo.v == null;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        int result = k != null ? k.hashCode() : 0;
        result = 31 * result + (v != null ? v.hashCode() : 0);
        return result;
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    @XmlAttribute
    public String getK() {
        return k;
    }

    public void setK(String k) {
        this.k = k;
    }

    @XmlAttribute
    public String getV() {
        return v;
    }

    public void setV(String v) {
        this.v = v;
    }
}
