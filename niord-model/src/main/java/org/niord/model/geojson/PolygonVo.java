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

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.function.Consumer;

/**
 * GeoJSON Polygon, as defined in the specification:
 * http://geojson.org/geojson-spec.html#polygon
 */
@Schema(
        name = "Polygon",
        description = "GeoJson Polygon type"
)
@XmlRootElement(name = "polygon")
public class PolygonVo extends GeometryVo {

    double[][][] coordinates;

    @SuppressWarnings("unused")
    public PolygonVo() {
        setType("Polygon");
    }

    public PolygonVo(double[][][] coordinates) {
        this();
        this.coordinates = coordinates;
    }

    /** {@inheritDoc} */
    @Override
    public void visitCoordinates(Consumer<double[]> handler) {
        visitCoordinates(coordinates, handler);
    }

    public double[][][] getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(double[][][] coordinates) {
        this.coordinates = coordinates;
    }
}

