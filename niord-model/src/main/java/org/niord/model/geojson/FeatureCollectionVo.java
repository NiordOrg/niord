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
package org.niord.model.geojson;


import io.swagger.annotations.ApiModel;

import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.Arrays;
import java.util.Objects;
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


    /** Constructor **/
    public FeatureCollectionVo() {
        setType("FeatureCollection");
    }


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
            .filter(Objects::nonNull)
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

