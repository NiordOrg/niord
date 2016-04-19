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

import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.function.Consumer;

/**
 * GeoJSON GeometryCollection, as defined in the specification:
 * http://geojson.org/geojson-spec.html#geometry-collection
 */
@ApiModel(value = "GeometryCollection", description = "GeoJson GeometryCollection type")
@XmlRootElement(name = "geometryCollection")
public class GeometryCollectionVo extends GeometryVo {

    private GeometryVo[] geometries;

    @SuppressWarnings("unused")
    public GeometryCollectionVo() {
    }

    public GeometryCollectionVo(GeometryVo[] geometries) {
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

    @XmlElementRef
    public GeometryVo[] getGeometries() {
        return geometries;
    }

    public void setGeometries(GeometryVo[] geometries) {
        this.geometries = geometries;
    }
}
