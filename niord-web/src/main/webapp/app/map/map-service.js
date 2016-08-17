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

/**
 * Map services.
 */
angular.module('niord.map')

    /**
     * The language service is used for changing language, etc.
     */
    .service('MapService', ['$rootScope',
        function ($rootScope) {
            'use strict';

            var that = this;
            var projMercator = 'EPSG:3857';
            var proj4326 = 'EPSG:4326';
            var geoJsonFormat = new ol.format.GeoJSON();


            /** Returns the data projection */
            this.dataProjection = function () {
                return proj4326;
            };


            /** Returns the feature projection */
            this.featureProjection = function () {
                return projMercator;
            };


            /** Rounds each value of the array to the given number of decimals */
            this.round = function (values, decimals) {
                for (var x = 0; values && x < values.length; x++) {
                    // NB: Prepending a '+' will convert from string to float
                    values[x] = +values[x].toFixed(decimals);
                }
                return values;
            };


            /** Returns the default center position of a map */
            this.defaultCenterLonLat = function () {
                if ($rootScope.domain && $rootScope.domain.lat && $rootScope.domain.lon) {
                    return [ $rootScope.domain.lon, $rootScope.domain.lat ];
                }
                return [ $rootScope.mapDefaultLongitude, $rootScope.mapDefaultLatitude ];
            };


            /** Returns the default zoom level of a map */
            this.defaultZoomLevel = function () {
                if ($rootScope.domain && $rootScope.domain.zoomLevel) {
                    return $rootScope.domain.zoomLevel;
                }
                return $rootScope.mapDefaultZoomLevel;
            };


            /** Converts lon-lat array to xy array in mercator */
            this.fromLonLat = function(lonLat) {
                return lonLat ? ol.proj.fromLonLat(lonLat) : null;
            };


            /** Converts xy array in mercator to a lon-lat array */
            this.toLonLat = function(xy) {
                return xy ? ol.proj.transform(xy, projMercator, proj4326) : null;
            };


            /** Converts lon-lat extent array to xy extent array in mercator */
            this.fromLonLatExtent = function(lonLatExtent) {
                if (lonLatExtent && lonLatExtent.length == 4) {
                    var minPos = this.fromLonLat([lonLatExtent[0], lonLatExtent[1]]);
                    var maxPos = this.fromLonLat([lonLatExtent[2], lonLatExtent[3]]);
                    return [minPos[0], minPos[1], maxPos[0], maxPos[1]];
                }
                return null;
            };


            /** Converts xy extent array in mercator to a lon-lat extent array */
            this.toLonLatExtent = function(xyExtent) {
                if (xyExtent && xyExtent.length == 4) {
                    var minPos = this.toLonLat([xyExtent[0], xyExtent[1]]);
                    var maxPos = this.toLonLat([xyExtent[2], xyExtent[3]]);
                    return [minPos[0], minPos[1], maxPos[0], maxPos[1]];
                }
                return null;
            };


            /** Returns the center of the extent */
            this.getExtentCenter = function (extent) {
                var x = extent[0] + (extent[2]-extent[0]) / 2.0;
                var y = extent[1] + (extent[3]-extent[1]) / 2.0;
                return [x, y];
            };


            /** Return a lon-lat center from the xy geometry */
            this.toCenterLonLat = function(geometry) {
                return this.toLonLat(this.getExtentCenter(geometry.getExtent()));
            };


            /**
             * Some geo-spatial library functions (e.g. the JSTS buffer function) only work properly at equator.
             * This function will compute boost factor based on the latitude of the given point.
             * As such, it will not work properly for very large features.
             * @param latLon the point to compute the boost factor at
             * @return the factor to multiply geometries with
             */
            this.computeLatitudeFactor = function (latLon) {
                return 1.0 / Math.cos(latLon[1] * Math.PI / 180.0);
            };


            /** Initializes a layer with a name, visibility and whether to display the layer in the layer switcher */
            this.initLayer = function (layer, name, visible, displayInLayerSwitcher) {
                layer.set('name', name);
                layer.setVisible(visible);
                layer.set('displayInLayerSwitcher', displayInLayerSwitcher);
                return layer;
            };


            /** Returns the list of active interactions */
            this.getActiveInteractions = function (map, clz) {
                var interactions = [];
                angular.forEach(map.getInteractions(), function (interaction) {
                    if (interaction.getActive() && (!clz || interaction instanceof clz)) {
                        interactions.push(interaction);
                    }
                });
                return interactions;
            };


            /** Some GeoJSON providers (e.g. marineregions.org) deliver data in YX order instead of XY **/
            this.swapXYCoordinates = function (geometry) {
                this.visitOLCoordinates(geometry, function (coords, index) {
                    var tmp = coords[index];
                    coords[index] = coords[index + 1];
                    coords[index + 1] = tmp;
                });
            };


            /** Recursively visits all coordinates of the OpenLayers geometry and executes the handler on each */
            this.visitOLCoordinates = function (geometry, handler) {
                if (geometry) {
                    if (geometry && geometry instanceof ol.Feature) {
                        that.visitOLCoordinates(geometry.getGeometry(), handler);
                    } else {
                        geometry.applyTransform(function (coords, coords2, stride) {
                            for (var j = 0; j < coords.length; j += stride) {
                                handler(coords, j);
                            }
                        });
                    }
                }
            };


            /** Creates a geometry that has been buffered with the given distance. **/
            this.bufferedOLGeometry = function (olGeometry, distInMeters) {
                // Sanity check
                if (distInMeters <= 0) {
                    return olGeometry;
                }

                var jstsOlParser = new jsts.io.olParser();

                // convert the GeoJson geometry to a JSTS geometry
                var jstsGeom = jstsOlParser.read(olGeometry);

                // JSTS seems to use the distance of longitude degree at Equator. So, a polygon
                // at, say, Denmark, will only be half its proper size
                var boostFactor = this.computeLatitudeFactor(this.toCenterLonLat(olGeometry));

                // create a buffer of 100 km around each line
                var buffered = jstsGeom.buffer(distInMeters * boostFactor);

                // convert back from JSTS and back to the OL geometry
                return jstsOlParser.write(buffered);
            };


            /** Creates a feature that has been buffered with the given distance. **/
            this.bufferedOLFeature = function (feature, distInMeters) {

                var olGeometry = this.bufferedOLGeometry(feature.getGeometry(), distInMeters);

                feature = new ol.Feature();
                feature.setGeometry(olGeometry);
                return feature;
            };


            /** Returns the number of coordinates in the given geometry **/
            this.getCoordinateNo = function (g) {
                var n = 0;
                if (g) {
                    if (g instanceof Array) {
                        if (g.length >= 2 && $.isNumeric(g[0])) {
                            n++;
                        } else {
                            for (var x = 0; x < g.length; x++) {
                                n += this.getCoordinateNo(g[x]);
                            }
                        }
                    } else if (g instanceof ol.Feature) {
                        n += this.getCoordinateNo(g.getGeometry());
                    } else if (g instanceof ol.geom.GeometryCollection) {
                        n += this.getCoordinateNo(g.getGeometries());
                    } else {
                        n += this.getCoordinateNo(g.getCoordinates());
                    }
                }
                return n;
            };


            /** Returns the n'th coordinate, or undefined if not found **/
            this.getCoordinateAtIndex = function (g, n) {
                var coords;
                if (g && n >= 0) {
                    if (g instanceof Array) {
                        if (g.length >= 2 && $.isNumeric(g[0])) {
                            if (n == 0) {
                                coords = g;
                            }
                        } else {
                            for (var x = 0; !coords && n >= 0 && x < g.length; x++) {
                                coords = this.getCoordinateAtIndex(g[x], n);
                                n -= this.getCoordinateNo(g[x]);
                            }
                        }
                    } else if (g instanceof ol.Feature) {
                        coords = this.getCoordinateAtIndex(g.getGeometry(), n);
                    } else if (g instanceof ol.geom.GeometryCollection) {
                        coords = this.getCoordinateAtIndex(g.getGeometries(), n);
                    } else {
                        coords = this.getCoordinateAtIndex(g.getCoordinates(), n);
                    }

                }
                return coords;
            };


            /** Returns a "sensible" center point of the geometry. Used e.g. for placing labels **/
            this.getGeometryCenter = function (g) {
                var point;
                try {
                    switch (g.getType()) {
                        case 'MultiPolygon':
                            var poly = g.getPolygons().reduce(function(left, right) {
                                return left.getArea() > right.getArea() ? left : right;
                            });
                            point = poly.getInteriorPoint().getCoordinates();
                            break;
                        case 'MultiLineString':
                            var lineString = g.getLineStrings().reduce(function(left, right) {
                                return left.getLength() > right.getLength() ? left : right;
                            });
                            point = MapService.getExtentCenter(lineString.getExtent());
                            break;
                        case 'Polygon':
                            point = g.getInteriorPoint().getCoordinates();
                            break;
                        case 'Point':
                            point = g.getCoordinates();
                            break;
                        case 'LineString':
                        case 'MultiPoint':
                        case 'GeometryCollection':
                            point = this.getExtentCenter(g.getExtent());
                            break;
                    }
                } catch (ex) {
                }
                return point;
            };


            /**
             * Sets the name associated with the OL feature
             * @param olFeature the OL feature
             * @param lang the language code
             * @param name the name to set
             */
            this.setFeatureName = function (olFeature, lang, name) {
                var langKey = 'name:' + lang;
                if (name && name.trim().length > 0) {
                    olFeature.set(langKey, name);
                } else {
                    olFeature.unset(langKey);
                }
            };


            /**
             * Sets the name associated with the OL feature coordinate
             * @param olFeature the OL feature
             * @param coordIndex the coordinate index
             * @param lang the language code
             * @param name the name to set
             */
            this.setFeatureCoordName = function (olFeature, coordIndex, lang, name) {
                var langKey = 'name:' + coordIndex + ':' + lang;
                if (name && name.trim().length > 0) {
                    olFeature.set(langKey, name);
                } else {
                    olFeature.unset(langKey);
                }
            };


            /**
             * Clears all feature coordinate names from the OL feature
             * @param olFeature the feature
             */
            this.clearFeatureCoordNames = function (olFeature) {
                angular.forEach(FeatureName.readFeatureNames(olFeature), function (name) {
                    olFeature.unset(name.key);
                });
            };


            /** ************************ **/
            /** GeoJSON Functionality    **/
            /** ************************ **/

            /**
             * Serializes the coordinates of a geometry
             * <p>
             * When serializing coordinates, adhere to a couple of rules:
             * <ul>
             *     <li>If the "parentFeatureIds" feature property is defined, skip the coordinates.</li>
             *     <li>If the "restriction" feature property has the value "affected", skip the coordinates.</li>
             *     <li>For polygon linear rings, skip the last coordinate (which is identical to the first).</li>
             *     <li>For (multi-)polygons, only include the exterior ring, not the interior ring.</li>
             * </ul>
             * This implementation should be kept in sync with the GeoJsonUtils.serializeFeatureCollection()
             * back-end function.
             */
            this.serializeCoordinates = function (g, coords, props, index, polygonType) {
                var that = this;
                props = props || {};
                index = index || 0;
                if (g) {
                    if (g instanceof Array) {
                        if (g.length >= 2 && $.isNumeric(g[0])) {
                            var bufferFeature = props['parentFeatureIds'];
                            var affectedArea = props['restriction'] == 'affected';
                            var includeCoord = (polygonType != 'Exterior');
                            if (includeCoord && !bufferFeature && !affectedArea) {
                                coords.push({
                                    lon: g[0],
                                    lat: g[1],
                                    name: props['name:' + index + ':' + $rootScope.language]
                                });
                            }
                            index++;
                        } else {
                            for (var x = 0; x < g.length; x++) {
                                polygonType = (polygonType == 'Interior' && x == g.length - 1) ? 'Exterior' : polygonType;
                                index = that.serializeCoordinates(g[x], coords, props, index, polygonType);
                            }
                        }
                    } else if (g.type == 'FeatureCollection') {
                        for (var x = 0; g.features && x < g.features.length; x++) {
                            index = that.serializeCoordinates(g.features[x], coords);
                        }
                    } else if (g.type == 'Feature') {
                        index = that.serializeCoordinates(g.geometry, coords, g.properties, 0);
                    } else if (g.type == 'GeometryCollection') {
                        for (var x = 0; g.geometries && x < g.geometries.length; x++) {
                            index = that.serializeCoordinates(g.geometries[x], coords, props, index);
                        }
                    } else if (g.type == 'MultiPolygon') {
                        for (var p = 0; p < g.coordinates.length; p++) {
                            // For polygons, do not include coordinates for interior rings
                            for (var x = 0; x < g.coordinates[p].length; x++) {
                                index = that.serializeCoordinates(g.coordinates[p][x], coords, props, index, x == 0 ? 'Interior' : 'Exterior');
                            }
                        }
                    } else if (g.type == 'Polygon') {
                        // For polygons, do not include coordinates for interior rings
                        for (var x = 0; x < g.coordinates.length; x++) {
                            index = that.serializeCoordinates(g.coordinates[x], coords, props, index, x == 0 ? 'Interior' : 'Exterior');
                        }
                    } else if (g.type) {
                        index = that.serializeCoordinates(g.coordinates, coords, props, index);
                    }
                }
                return index;
            };


            /** Converts an OL geometry to GeoJSON **/
            this.olToGjGeometry = function (g) {
                if (g.getType() != 'GeometryCollection') {
                    return geoJsonFormat.writeGeometryObject(g, {
                        dataProjection: proj4326,
                        featureProjection: projMercator
                    });
                }

                // ol.format.GeoJSON does not properly convert the coordinates of a 'GeometryCollection'
                // Handle geometry collections manually instead.
                var gc = {
                    type: 'GeometryCollection',
                    geometries: []
                };
                angular.forEach(g.getGeometries(), function (geom) {
                    gc.geometries.push(that.olToGjGeometry(geom));
                });
                return gc;
            };


            /** Converts an OL feature to GeoJSON **/
            this.olToGjFeature = function (feature) {
                var gjFeature = geoJsonFormat.writeFeatureObject(feature, {
                    dataProjection: proj4326,
                    featureProjection: projMercator
                });
                if (!gjFeature.properties) {
                    gjFeature.properties = {};
                }

                // Hack to circumvent bug:
                // ol.format.GeoJSON does not properly convert "GeometryCollection"
                if (feature.getGeometry().getType() == 'GeometryCollection') {
                    gjFeature.geometry = this.olToGjGeometry(feature.getGeometry());
                }

                return gjFeature;
            };


            /** Converts a GeoJSON geometry to an OL geometry **/
            this.gjToOlGeometry = function (g) {
                return geoJsonFormat.readGeometry(g, {
                    dataProjection: proj4326,
                    featureProjection: projMercator
                });
            };


            /** Converts a GeoJSON feature to an OL feature **/
            this.gjToOlFeature = function (feature) {
                return geoJsonFormat.readFeature(feature, {
                    dataProjection: proj4326,
                    featureProjection: projMercator
                });
            };


            /**
             * Fast UUID generator, RFC4122 version 4 compliant.
             * @author Jeff Ward (jcward.com).
             * @license MIT license
             * @link http://stackoverflow.com/questions/105034/how-to-create-a-guid-uuid-in-javascript/21963136#21963136
             **/
            var UUID = (function() {
                var self = {};
                var lut = []; for (var i=0; i<256; i++) { lut[i] = (i<16?'0':'')+(i).toString(16); }
                self.generate = function() {
                    var d0 = Math.random()*0xffffffff|0;
                    var d1 = Math.random()*0xffffffff|0;
                    var d2 = Math.random()*0xffffffff|0;
                    var d3 = Math.random()*0xffffffff|0;
                    return lut[d0&0xff]+lut[d0>>8&0xff]+lut[d0>>16&0xff]+lut[d0>>24&0xff]+'-'+
                        lut[d1&0xff]+lut[d1>>8&0xff]+'-'+lut[d1>>16&0x0f|0x40]+lut[d1>>24&0xff]+'-'+
                        lut[d2&0x3f|0x80]+lut[d2>>8&0xff]+'-'+lut[d2>>16&0xff]+lut[d2>>24&0xff]+
                        lut[d3&0xff]+lut[d3>>8&0xff]+lut[d3>>16&0xff]+lut[d3>>24&0xff];
                };
                return self;
            })();

            /** Returns a UUID **/
            this.uuid = function() {
                return UUID.generate();
            };

            /** Ensures that the feature has an ID **/
            this.checkCreateId = function(feature) {
                if (feature && !feature.getId()) {
                    feature.setId(this.uuid());
                }
                return feature;
            }

        }]);


/** ****************** **/
/** Utility classes    **/
/** ****************** **/

var featureNameKey = new RegExp("^name:([a-zA-Z_]+)$");
var coordinateNameKey = new RegExp("^name:(\\d+):([a-zA-Z_]+)$");

function FeatureName (key, val) {
    this.key = key;
    this.value = val;
    if (key) {
        if (featureNameKey.test(key)) {
            this.type = 'feature-name';
            this.language = featureNameKey.exec(key)[1];
        } else if (coordinateNameKey.test(key)) {
            this.type = 'feature-coord-name';
            this.coordIndex = parseInt(coordinateNameKey.exec(key)[1]);
            this.language = coordinateNameKey.exec(key)[2];
        }
    }
}

FeatureName.prototype.isFeatureName = function() {
    return this.type == 'feature-name';
};

FeatureName.prototype.isFeatureCoordName = function() {
    return this.type == 'feature-coord-name';
};

FeatureName.prototype.isValid = function() {
    return this.type !== undefined;
};

FeatureName.prototype.getLanguage = function() {
    return this.language;
};

FeatureName.prototype.getCoordIndex = function() {
    return this.coordIndex;
};

FeatureName.prototype.getKey = function() {
    return this.key;
};

FeatureName.prototype.getValue = function() {
    return this.value;
};

FeatureName.prototype.offset = function(offset) {
    if (this.isFeatureCoordName()) {
        this.coordIndex += offset;
        this.key = 'name:' + this.coordIndex + ':' + this.language;
    }
};

FeatureName.readFeatureNames = function (feature) {
    var featureNames = [];
    angular.forEach(feature.getKeys(), function (key) {
        var name = new FeatureName(key, feature.get(key));
        if (name.isValid()) {
            featureNames.push(name);
        }
    });
    return featureNames;
};

