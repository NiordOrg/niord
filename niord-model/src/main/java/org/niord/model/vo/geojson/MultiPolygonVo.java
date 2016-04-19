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
package org.niord.model.vo.geojson;

import io.swagger.annotations.ApiModel;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.function.Consumer;

/**
 * GeoJSON MultiPolygon, as defined in the specification:
 * http://geojson.org/geojson-spec.html#multipolygon
 */
@ApiModel(value = "MultiPolygon", description = "GeoJson MultiPolygon type")
@XmlRootElement(name = "multiPolygon")
public class MultiPolygonVo extends GeometryVo {

    private double[][][][] coordinates;

    @SuppressWarnings("unused")
    public MultiPolygonVo() {
    }

    public MultiPolygonVo(double[][][][] coordinates) {
        this.coordinates = coordinates;
    }

    /** {@inheritDoc} */
    @Override
    public void visitCoordinates(Consumer<double[]> handler) {
        visitCoordinates(coordinates, handler);
    }

    public double[][][][] getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(double[][][][] coordinates) {
        this.coordinates = coordinates;
    }
}

