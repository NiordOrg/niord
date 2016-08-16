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
package org.niord.model.message.geojson;

import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;

/**
 * Base Geometry object as defined in the specification:
 * http://geojson.org/geojson-spec.html#geometry-objects
 */
@XmlType(name = "geometry")
@XmlSeeAlso({ PointVo.class, MultiPointVo.class, LineStringVo.class, MultiLineStringVo.class,
        PolygonVo.class, MultiPolygonVo.class, GeometryCollectionVo.class })
public abstract class GeometryVo extends GeoJsonVo {
}
