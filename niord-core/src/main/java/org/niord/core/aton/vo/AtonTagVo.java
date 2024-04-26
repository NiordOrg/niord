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

import org.apache.commons.lang.StringUtils;
import org.niord.model.IJsonSerializable;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;

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
@SuppressWarnings("unused")
@XmlRootElement(name = "tag")
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
    @SuppressWarnings("all")
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

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "AtonTagVo [k=" + k + ", v=" + v + "]";
    }
    
    
}
