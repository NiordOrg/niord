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
import java.util.function.Consumer;

/**
 * GeoJSON GeometryCollection, as defined in the specification:
 * http://geojson.org/geojson-spec.html#geometry-collection
 */
@ApiModel(
        value = "GeometryCollection",
        parent = GeoJsonVo.class,
        description = "GeoJson GeometryCollection type"
)
@XmlRootElement(name = "geometryCollection")
public class GeometryCollectionVo extends GeometryVo {

    private GeometryVo[] geometries;

    /**
     * Instantiates a new Geometry collection vo.
     */
    @SuppressWarnings("unused")
    public GeometryCollectionVo() {
        setType("GeometryCollection");
    }

    /**
     * Instantiates a new Geometry collection vo.
     *
     * @param geometries the geometries
     */
    public GeometryCollectionVo(GeometryVo[] geometries) {
        this();
        this.geometries = geometries;
    }

    /** {@inheritDoc} */
    @Override
    public void visitCoordinates(Consumer<double[]> handler) {
        if (geometries != null) {
            for (GeometryVo geometry : geometries) {
                geometry.visitCoordinates(handler);
            }
        }
    }

    /**
     * Get geometries geometry vo [ ].
     *
     * @return the geometry vo [ ]
     */
    @XmlElementRef
    public GeometryVo[] getGeometries() {
        return geometries;
    }

    /**
     * Sets geometries.
     *
     * @param geometries the geometries
     */
    public void setGeometries(GeometryVo[] geometries) {
        this.geometries = geometries;
    }
}
