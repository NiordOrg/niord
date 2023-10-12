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

import org.niord.model.IJsonSerializable;

import jakarta.xml.bind.annotation.XmlAttribute;
import java.util.Arrays;

/**
 * An AtoN OSM bounds entity.
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
 *   &lt;osm version="0.6" generator="Niord"&gt;
 *     &lt;node id="672436827" lat="50.8070813" lon="-1.2841124" user="malcolmh" uid="128186" visible="true" version="11"
 *           changeset="9107813" timestamp="2011-08-23T21:22:36Z"&gt;
 *         &lt;bounds minlat="34.0662408634219" minlon="-118.736715316772" maxlat="34.0731374116421" maxlon="-118.73122215271" /&gt;
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
 *   &lt;/osm&gt;
 * </pre>
 */
public class AtonBoundsVo implements IJsonSerializable {

    double minlat;
    double minlon;
    double maxlat;
    double maxlon;

    /** Computes the bbox for the given list of AtoNs */
    public static AtonBoundsVo fromAtons(AtonNodeVo[] atons) {
        if (atons == null) {
            return null;
        }
        AtonBoundsVo bounds = new AtonBoundsVo();
        bounds.minlat = 90;
        bounds.minlon = 180;
        bounds.maxlat = -90;
        bounds.maxlon = -180;
        Arrays.stream(atons).forEach(a -> {
            bounds.minlat = Math.min(bounds.minlat, a.lat);
            bounds.minlon = Math.min(bounds.minlon, a.lon);
            bounds.maxlat = Math.max(bounds.maxlat, a.lat);
            bounds.maxlon = Math.max(bounds.maxlon, a.lon);
        });
        return bounds;
    }

    /*************************/
    /** Getters and Setters **/
    /***/

    @XmlAttribute
    public double getMinlat() {
        return minlat;
    }

    public void setMinlat(double minlat) {
        this.minlat = minlat;
    }

    @XmlAttribute
    public double getMinlon() {
        return minlon;
    }

    public void setMinlon(double minlon) {
        this.minlon = minlon;
    }

    @XmlAttribute
    public double getMaxlat() {
        return maxlat;
    }

    public void setMaxlat(double maxlat) {
        this.maxlat = maxlat;
    }

    @XmlAttribute
    public double getMaxlon() {
        return maxlon;
    }

    public void setMaxlon(double maxlon) {
        this.maxlon = maxlon;
    }
}
