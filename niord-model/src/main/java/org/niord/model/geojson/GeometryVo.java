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

import org.eclipse.microprofile.openapi.annotations.media.DiscriminatorMapping;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.xml.bind.annotation.XmlSeeAlso;
import jakarta.xml.bind.annotation.XmlType;

/**
 * Base Geometry object as defined in the specification:
 * http://geojson.org/geojson-spec.html#geometry-objects
 */
@Schema(
        name = "Geometry",
        description = "Superclass for Geometry types",
        oneOf = {PointVo.class, MultiPointVo.class, LineStringVo.class, MultiLineStringVo.class,
                PolygonVo.class, MultiPolygonVo.class, GeometryCollectionVo.class},
        discriminatorProperty = "type",
        discriminatorMapping = {
                @DiscriminatorMapping(value = "Point", schema = PointVo.class),
                @DiscriminatorMapping(value = "MultiPoint", schema = MultiPointVo.class),
                @DiscriminatorMapping(value = "LineString", schema = LineStringVo.class),
                @DiscriminatorMapping(value = "MultiLineString", schema = MultiLineStringVo.class),
                @DiscriminatorMapping(value = "Polygon", schema = PolygonVo.class),
                @DiscriminatorMapping(value = "MultiPolygon", schema = MultiPolygonVo.class),
                @DiscriminatorMapping(value = "GeometryCollection", schema = GeometryCollectionVo.class),
        }
)
@XmlType(name = "geometry")
@XmlSeeAlso({ PointVo.class, MultiPointVo.class, LineStringVo.class, MultiLineStringVo.class,
        PolygonVo.class, MultiPolygonVo.class, GeometryCollectionVo.class })
public abstract class GeometryVo extends GeoJsonVo {
}
