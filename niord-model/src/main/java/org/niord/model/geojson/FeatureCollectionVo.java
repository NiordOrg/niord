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
package org.niord.model.geojson;


import io.swagger.annotations.ApiModel;

import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * GeoJSON FeatureCollection, as defined in the specification:
 * hhttp://geojson.org/geojson-spec.html#feature-objects
 */
@ApiModel(value = "FeatureCollection", description = "GeoJson FeatureCollection type")
@XmlRootElement(name = "featureCollection")
public class FeatureCollectionVo extends GeoJsonVo {

    // NB: "id" not directly mentioned in specification, but it's useful...
    private Object id;
    private FeatureVo[] features;

    /** {@inheritDoc} */
    @Override
    public void visitCoordinates(Consumer<double[]> handler) {
        if (features != null) {
            for (FeatureVo feature : features) {
                feature.visitCoordinates(handler);
            }
        }
    }

    /**
     * Returns a combined geometry for the whole feature collection. If no feature is defined, null is returned.
     * If a single feature is defined, the geometry feature is returned.
     * Otherwise, a combined geometry collection of all feature geometries is returned.
     * @return the combined geometry for the feature
     */
    public GeometryVo toGeometry() {
        if (features == null || features.length == 0) {
            return null;
        } else if (features.length == 1) {
            return features[0].getGeometry();
        }

        GeometryCollectionVo geometry = new GeometryCollectionVo();
        geometry.setType("GeometryCollection");
        geometry.setGeometries(Arrays.stream(features)
            .map(FeatureVo::getGeometry)
            .filter(g -> g != null)
            .toArray(GeometryVo[]::new));
        return geometry;
    }


    public Object getId() {
        return id;
    }

    public void setId(Object id) {
        this.id = id;
    }

    @XmlElementRef
    public FeatureVo[] getFeatures() {
        return features;
    }

    public void setFeatures(FeatureVo[] features) {
        this.features = features;
    }
}

