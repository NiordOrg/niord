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

import javax.xml.bind.annotation.XmlRootElement;
import java.util.function.Consumer;

/**
 * GeoJSON Point, as defined in the specification:
 * http://geojson.org/geojson-spec.html#linestring
 */
@ApiModel(
        value = "LineString",
        parent = GeoJsonVo.class,
        description = "GeoJson LineString type"
)
@XmlRootElement(name = "lineString")
public class LineStringVo extends GeometryVo {

    private double[][] coordinates;

    /**
     * Instantiates a new Line string vo.
     */
    @SuppressWarnings("unused")
    public LineStringVo() {
        setType("LineString");
    }

    /**
     * Instantiates a new Line string vo.
     *
     * @param coordinates the coordinates
     */
    public LineStringVo(double[][] coordinates) {
        this();
        this.coordinates = coordinates;
    }

    /** {@inheritDoc} */
    @Override
    public void visitCoordinates(Consumer<double[]> handler) {
        visitCoordinates(coordinates, handler);
    }

    /**
     * Get coordinates double [ ] [ ].
     *
     * @return the double [ ] [ ]
     */
    public double[][] getCoordinates() {
        return coordinates;
    }

    /**
     * Sets coordinates.
     *
     * @param coordinates the coordinates
     */
    public void setCoordinates(double[][] coordinates) {
        this.coordinates = coordinates;
    }
}

