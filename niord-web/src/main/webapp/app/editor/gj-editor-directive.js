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
 * The GeoJSON FeatureCollection Editor directive.
 * <p>
 * The editor can be initialized with "edit-type" set to the following values:
 * <ul>
 *     <li>"features": You edit a GeoJson feature collection.</li>
 *     <li>"feature": You edit a GeoJson feature collection, but when you save, all features are merged
 *                   into a single feature.</li>
 *     <li>"message": You edit the feature collection of a message, which means that there are a lot of extra
 *                    functionality, such as allowing the user to specify names for each feature and position,
 *                    an affected radius, etc.</li>
 * </ul>
 * The GeoJson feature collection is divided into two lists:
 * <ul>
 *     <li>features: List of OpenLayers Features that can be edited in the data tree-view or map view.</li>
 *     <li>featureContexts: Angular is not really suited to $watch Features, which may be really large. Thus, for each
 *         feature, a feature context will be maintained, which contains editable attributes and a possible relation
 *         to a buffered feature.</li>
 * </ul>
 */
angular.module('niord.editor')

    /**
     * Defines the GeoJSON FeatureCollection Editor
     */
    .directive('gjEditor', ['$document', '$rootScope', '$timeout', '$uibModal', 'growl', 'MapService', 'AtonService',
        function ($document, $rootScope, $timeout, $uibModal, growl, MapService, AtonService) {

        return {
            restrict: 'E',
            templateUrl: '/app/editor/gj-editor-directive.html',
            replace: true,
            transclude: true,
            scope: {
                openEditorMessage:  "@",
                editAsTextMessage:  "@",
                editType:           "@",
                drawControls:       "@",
                featureCollection:  "=",
                showWmsLayer:       "=",
                onSave:             '&',
                tabIndex:           '='
            },
            link: function(scope, element, attrs) {

                scope.mode = 'viewer';
                scope.openEditorMessage = scope.openEditorMessage || 'Edit';
                scope.editAsTextMessage = scope.editAsTextMessage || 'Edit as text';
                scope.drawControls  =  scope.drawControls || 'point,polyline,polygon,select,remove';
                scope.drawControl = 'select';
                scope.editorId = attrs['id'] + '-editor';
                scope.languages = $rootScope.modelLanguages;
                scope.editType = scope.editType || 'features';
                scope.wmsLayerEnabled = $rootScope.wmsLayerEnabled;
                scope.showWmsLayer = scope.showWmsLayer && $rootScope.wmsLayerEnabled;
                scope.atonLayerEnabled = $rootScope.domain && $rootScope.domain.atons;
                scope.openSeaMapLayerEnabled = $rootScope.openSeaMapLayerEnabled;

                /** The OpenLayer features being edited **/
                scope.features = [];

                /** The currently selected features **/
                scope.selection = [];

                /** Meta-data for the features being edited **/
                scope.featureContexts = [];
                var featureCtxPrefixes = [ 'name', 'restriction', 'parentFeatureIds',
                    'bufferRadius', 'bufferRadiusType', 'restriction', 'aton' ];

                scope.showDrawControls = {
                    point:      scope.drawControls.indexOf('point') !== -1,
                    polyline:   scope.drawControls.indexOf('polyline') !== -1,
                    polygon:    scope.drawControls.indexOf('polygon') !== -1,
                    box:        scope.drawControls.indexOf('box') !== -1,
                    select:     scope.drawControls.indexOf('select') !== -1,
                    remove:     scope.drawControls.indexOf('remove') !== -1
                };

                if (scope.tabIndex) {
                    $timeout(function () {
                        element.find("#editorBtn").attr('tabindex', scope.tabIndex);
                        element.find("#editAsTxtBtn").attr('tabindex', scope.tabIndex);
                    });
                }

                /** ************************ **/
                /** Utility functions        **/
                /** ************************ **/

                /** Utility function that swaps two elements of an array **/
                function swapElements(arr, index1, index2) {
                    if (index1 >= 0 && index1 < arr.length &&
                        index2 >= 0 && index2 < arr.length) {
                        var tmp = arr[index1];
                        arr[index1] = arr[index2];
                        arr[index2] = tmp;
                    }
                }


                /** Removes an element from an array **/
                function removeFromArray(arr, val) {
                    if (arr && val && $.inArray(val, arr) !== -1) {
                        arr.splice( $.inArray(val, arr), 1 );
                    }
                }

                /** Finds the feature with the given ID */
                function findFeatureById(id) {
                    for (var x = 0; x < scope.features.length; x++) {
                        if (scope.features[x].getId() === id) {
                            return scope.features[x];
                        }
                    }
                    return null;
                }

                /** Finds the feature context with the given ID */
                function findFeatureContextById(id) {
                    for (var x = 0; x < scope.featureContexts.length; x++) {
                        if (scope.featureContexts[x].id === id) {
                            return scope.featureContexts[x];
                        }
                    }
                    return null;
                }

                /** Finds the buffered feature contexts for the feature with the given ID */
                function findBufferedFeatureContexts(featureId) {
                    return $.grep(scope.featureContexts, function (featureCtx) {
                        return featureCtx['parentFeatureIds'] && featureCtx['parentFeatureIds'].indexOf(featureId) !== -1;
                    });
                }

                /** Finds the parent features defined by the comma-separated parentFeatureIds string **/
                function findParentFeatures(parentFeatureIds) {
                    if (parentFeatureIds) {
                        var idLookup = {};
                        angular.forEach(parentFeatureIds.split(','), function (id) { idLookup[id] = id; });
                        return $.grep(scope.features, function (feature) {
                            return idLookup[feature.getId()] !== undefined;
                        });
                    }
                    return [];
                }

                /** Copy named properties from an OL feature to the feature context **/
                function copyPropertiesFromFeature(feature, featureCtx) {
                    angular.forEach(feature.getKeys(), function (key) {
                        angular.forEach(featureCtxPrefixes, function (prefix) {
                            if (key.indexOf(prefix) === 0) {
                                featureCtx[key] = feature.get(key);
                            }
                        });
                    });
                }

                /** Copy named properties from a feature context to the OL feature **/
                function copyPropertiesToFeature(feature, featureCtx) {
                    angular.forEach(featureCtx, function (value, key) {
                        angular.forEach(featureCtxPrefixes, function (prefix) {
                            if (key.indexOf(prefix) === 0) {
                                if (value) {
                                    feature.set(key, value);
                                } else {
                                    feature.unset(key);
                                }
                            }
                        });
                    });
                }

                /** Updates the feature from the feature context **/
                function updateFeatureFromFeatureCtx(featureCtx) {
                    var feature = findFeatureById(featureCtx.id);
                    copyPropertiesToFeature(feature, featureCtx);
                }


                /** Creates a feature context based on the given feature **/
                function createFeatureCtxFromFeature(feature) {
                    if (feature) {
                        var featureCtx = {
                            id: feature.getId(),
                            type: feature.getGeometry().getType(),
                            selected: false,
                            restriction: undefined,
                            bufferRadius: undefined,
                            bufferRadiusType: undefined,
                            aton: undefined
                        };
                        copyPropertiesFromFeature(feature, featureCtx);
                        return featureCtx;
                    }
                    return null;
                }

                /** Initializes the featureCtx.showXXX flags based on feature properties **/
                function initFeatureCtxShowFlags(featureCtx) {
                    featureCtx.showRestriction = featureCtx.restriction && featureCtx.restriction.length > 0;
                    featureCtx.showName = false;
                    angular.forEach(scope.languages, function (lang) {
                        if (featureCtx['name:' + lang] && featureCtx['name:' + lang].length > 0) {
                            featureCtx.showName = true;
                        }
                    });
                    featureCtx.showRadius = toMeters(featureCtx.bufferRadius, featureCtx.bufferRadiusType) > 0;
                    featureCtx.showAton = featureCtx.aton !== undefined;
                }

                /** Updates the list of selected features **/
                function recomputeFeatureSelection() {
                    scope.selection.length = 0;
                    angular.forEach(scope.features, function (feature) {
                        if (feature.get('selected') === true) {
                            scope.selection.push(feature);
                        }
                    });
                    return scope.selection;
                }


                /** Broadcasts a 'gj-editor-update' message to sub-directives **/
                function broadcast(type, featureId, origScope) {
                    scope.$broadcast('gj-editor-update', {
                        type: type,
                        featureId: featureId,
                        scope: scope.$id,
                        origScope: origScope
                    });
                }

                /** ************************ **/
                /** Editor state changes     **/
                /** ************************ **/


                /** Opens the editor **/
                scope.openEditor = function () {

                    scope.features.length = 0;
                    scope.featureContexts.length = 0;

                    // Convert the GeoJson features to OpenLayers features and buffer features
                    scope.importGjFeatures(scope.featureCollection.features);

                    // Enter editor mode
                    scope.mode = 'editor';
                };


                /** Closes the editor and updates the locations if the save flag is true **/
                scope.exitEditor = function (save) {
                    if (save) {

                        // Convert the features into GeoJSON
                        scope.featureCollection.features.length = 0;

                        // Check if we need to merge all features into one
                        if (scope.editType === 'feature') {
                            scope.selectAll();
                            scope.merge();
                        }

                        // Convert to GeoJson feature collection
                        angular.forEach(scope.features, function (olFeature) {
                            var gjFeature = MapService.olToGjFeature(olFeature);

                            // Clear non-supported feature properties
                            delete gjFeature.properties.selected;

                            scope.featureCollection.features.push(gjFeature);
                        });

                        // Check if we need to call onSave callback
                        if (scope.onSave) {
                            scope.onSave({featureCollection: scope.featureCollection});
                        }

                    }
                    scope.mode = 'viewer';
                };


                /** Opens the plain-text editor **/
                scope.openPlainTextEditor = function () {

                    // TODO:
                    // 1) Only make option available for simple GeoJSON geometries
                    // 2) When editType === 'feature', the result should be merged

                    var features = scope.featureCollection.features.slice();
                    scope.openGeometryImportDialog("plain-text-editor-dialog.html", 'lg', features)
                        .result.then(function (features) {
                            scope.featureCollection.features = features;

                            // Check if we need to call onSave callback
                            if (scope.onSave) {
                                scope.onSave({featureCollection: scope.featureCollection});
                            }
                        });
                };


                /** ************************ **/
                /** Action Menu Functions    **/
                /** ************************ **/

                /** Clears the feature collection **/
                scope.clearAll = function () {
                    scope.features.length = 0;
                    scope.featureContexts.length = 0;
                    recomputeFeatureSelection();
                    broadcast('refresh-all');
                };


                /** Selects all features **/
                scope.selectAll = function () {
                    angular.forEach(scope.features, function (feature) {
                        feature.set('selected', true);
                    });
                    recomputeFeatureSelection();
                };


                /** Zooms to the extent of the feature collection **/
                scope.zoomToExtent = function () {
                    broadcast('fit-extent');
                };


                /** Creates a new feature ***/
                scope.createNewFeature = function (geom, properties) {
                    var f = new ol.Feature();
                    MapService.checkCreateId(f);
                    f.setGeometry(geom);

                    if (properties) {
                        f.setProperties(properties, true);
                    }

                    scope.features.push(f);
                    var featureCtx = createFeatureCtxFromFeature(f);
                    scope.featureContexts.push(featureCtx);
                    return f;
                };


                /** If the feature has an associated AtoN, return maximum range. Otherwise return default range */
                function checkGetAtonRange(feature, defaultRange) {
                    var range = defaultRange;
                    var aton = feature.get('aton');
                    if (aton && aton.tags) {
                        var atonRange = 0;
                        angular.forEach(aton.tags, function (v, k) {
                            if (k && k.match('range$') === 'range' && !isNaN(v)) {
                                atonRange = Math.max(atonRange, +v);
                            }
                        });
                        if (atonRange > 0) {
                            range = atonRange;
                        }
                    }
                    return range;
                }


                /** Computes the affected geometry of the parent features */
                function updateBufferedFeatureGeometry(feature) {
                    var parentFeatureIds = feature.get('parentFeatureIds');
                    var bufferType = feature.get('bufferType') || 'radius';
                    var bufferRadius = feature.get('bufferRadius');
                    var bufferRadiusType = feature.get('bufferRadiusType');

                    var features = findParentFeatures(parentFeatureIds);
                    var allPoints = true;
                    angular.forEach(features, function (feature) {
                        allPoints = allPoints && feature.getGeometry() != null && feature.getGeometry().getType() === 'Point';
                    });

                    var geom;
                    if (bufferType === 'radius') {
                        geom = mergeFeatureGeometries(features);
                    } else if (allPoints) {
                        var coords = [];
                        angular.forEach(features, function (f) {
                            coords.push(f.getGeometry().getCoordinates());
                        });
                        if (bufferType === 'path') {
                            geom = new ol.geom.LineString(coords);
                        } else if (bufferType === 'area') {
                            coords.push(coords[0]);
                            geom = new ol.geom.Polygon([ coords ]);
                        }
                    } else {
                        throw "Invalid type " + bufferType;
                    }

                    if (geom) {
                        if (bufferRadius && bufferRadius > 0) {
                            geom = MapService.bufferedOLGeometry(geom, toMeters(bufferRadius, bufferRadiusType))
                        }
                        feature.setGeometry(geom);
                    }
                }


                /** Checks if the current selection can be used to add a buffered feature of the given type */
                scope.canAddBufferedFeature = function (bufferType) {
                    if (scope.editType === 'message' && scope.selection.length > 0) {
                        var allPoints = true;
                        angular.forEach(scope.selection, function (feature) {
                            allPoints = allPoints && feature.getGeometry() != null && feature.getGeometry().getType() === 'Point';
                        });
                        if (bufferType === 'radius') {
                            return true;
                        } else if (bufferType === 'path' && scope.selection.length > 1 && allPoints) {
                            return true;
                        } else if (bufferType === 'area' && scope.selection.length > 2 && allPoints) {
                            return true;
                        }
                    }
                    return false;
                };


                /** Creates buffered features for the current selection **/
                scope.addBufferedFeature = function (bufferType) {
                    if (scope.selection.length === 0) {
                        return;
                    }

                    var featureIds = [];
                    angular.forEach(scope.selection, function (feature) { featureIds.push(feature.getId()) });

                    var bufferFeature = new ol.Feature();
                    MapService.checkCreateId(bufferFeature);
                    bufferFeature.set('parentFeatureIds', featureIds.join());
                    bufferFeature.set('restriction', 'affected');
                    bufferFeature.set('bufferType', bufferType);
                    if (bufferType === 'radius') {
                        bufferFeature.set('bufferRadius', checkGetAtonRange(scope.selection[0], 1.0));
                        bufferFeature.set('bufferRadiusType', 'nm');
                    }
                    try {
                        updateBufferedFeatureGeometry(bufferFeature);
                    } catch (err) {
                        growl.error("Error creating affected " + bufferType + ":\n" + err, { ttl: 5000 });
                        return;
                    }
                    scope.features.push(bufferFeature);

                    var featureCtx = createFeatureCtxFromFeature(bufferFeature);
                    featureCtx.showRadius = bufferType === 'radius';
                    featureCtx.showRestriction = true;
                    scope.featureContexts.push(featureCtx);
                    broadcast('feature-added', bufferFeature.getId());
                };


                /** Imports the list of GeoJSON features **/
                scope.importGjFeatures = function (features) {
                    var olFeatures = [];
                    if (features && features.length > 0) {
                        angular.forEach(features, function (feature) {
                            // Convert to an OpenLayer feature
                            var olFeature = MapService.gjToOlFeature(feature);

                            // Ensure that the feature has an ID
                            MapService.checkCreateId(olFeature);

                            olFeatures.push(olFeature);
                            scope.features.push(olFeature);

                            // Build the list of feature contexts containing all non-buffer features
                            var featureCtx = createFeatureCtxFromFeature(olFeature);

                            // By default, display only the types of data that are filled out
                            initFeatureCtxShowFlags(featureCtx);
                            scope.featureContexts.push(featureCtx);
                        })
                    }
                    return olFeatures;
                };


                /** Opens the import dialog **/
                scope.openGeometryImportDialog = function (templateUrl, size, features) {
                    return $uibModal.open({
                        controller: "GeometryImportDialogCtrl",
                        templateUrl: '/app/editor/' + templateUrl,
                        size: size,
                        resolve: {
                            features: function () { return features;  }
                        }
                    })
                };


                /** Imports geometries from various sources **/
                scope.importGeometry = function () {
                    scope.openGeometryImportDialog("geometry-import-dialog.html", 'md')
                        .result.then(function (features) {
                            var olFeatures = scope.importGjFeatures(features);
                            angular.forEach(olFeatures, function (olFeature) {
                                broadcast('feature-added', olFeature.getId());
                            });
                        });
                };


                /** Edits geometries as plain-text **/
                scope.editAsPlainText = function () {

                    var features = [];
                    angular.forEach(scope.features, function (olFeature) {
                        features.push(MapService.olToGjFeature(olFeature))
                    });

                    scope.openGeometryImportDialog("plain-text-editor-dialog.html", 'lg', features)
                        .result.then(function (features) {
                            scope.clearAll();
                            scope.importGjFeatures(features);
                            broadcast('refresh-all');
                        });
                };


                /** Computed the type of two merged geometries **/
                function computeMergeType(type1, type2) {
                    type1 = type1 || type2;
                    if (type2.indexOf('Point') >= 0 && type1.indexOf('Point') >= 0) {
                        return 'MultiPoint';
                    } else if (type2.indexOf('LineString') >= 0 && type1.indexOf('LineString') >= 0) {
                        return 'MultiLineString';
                    } else if (type2.indexOf('Polygon') >= 0 && type1.indexOf('Polygon') >= 0) {
                        return 'MultiPolygon';
                    }
                    return 'GeometryCollection';
                }


                /**
                 * Merges a g2 into g1.
                 * Will mainly handle merging of the same type of geometries, e.g. MultiPoint + Point -> MultiPoint.
                 **/
                function mergeGeometries(g1, g2) {
                    if (g1.getType() === 'GeometryCollection') {
                        var geometries = g1.getGeometries() || [];
                        var g = (g2.getType() === 'GeometryCollection') ? g2.getGeometries() : [ g2 ];
                        angular.forEach(g, function (gg) { geometries.push(gg.clone()); });
                        g1.setGeometries(geometries);
                        //g1 = new ol.geom.GeometryCollection(geometries);
                        return g1;
                    } else {
                        switch (g2.getType()) {
                            case 'Point':
                            case 'MultiPoint':
                                var points = g2.getType() === 'Point' ? [ g2 ] : g2.getPoints();
                                angular.forEach(points, function (pt) { g1.appendPoint(pt.clone()); });
                                break;
                            case 'LineString':
                            case 'MultiLineString':
                                var lineStrings = g2.getType() === 'LineString' ? [ g2 ] : g2.getLineStrings();
                                angular.forEach(lineStrings, function (ls) { g1.appendLineString(ls.clone()); });
                                break;
                            case 'Polygon':
                            case 'MultiPolygon':
                                var polygons = g2.getType() === 'Polygon' ? [ g2 ] : g2.getPolygons();
                                angular.forEach(polygons, function (pol) { g1.appendPolygon(pol.clone()); });
                                break;
                        }
                        return g1;
                    }
                }


                /** Merges the given features **/
                function mergeFeatureGeometries(features) {

                    // NB: This could have been implemented easily with JSTS's union() feature.
                    //     However, in some cases this causes an error.

                    // Compute the type of the merged feature
                    var type = undefined;
                    angular.forEach(features, function (feature) {
                        type = computeMergeType(type, feature.getGeometry().getType());
                    });

                    // Create a merged geometry
                    var geom = new ol.geom[type]();
                    if (type === 'GeometryCollection') {
                        geom.setGeometries([]);
                    }
                    angular.forEach(features, function (feature) {
                        geom = mergeGeometries(geom, feature.getGeometry());
                    });
                    return geom;
                }


                /** Merges the selected features **/
                scope.merge = function () {
                    var selected = scope.selection.slice();
                    if (selected.length < 2) {
                        return;
                    }

                    var properties = {};
                    var coordOffset = 0;
                    var geom = mergeFeatureGeometries(selected);
                    angular.forEach(selected, function (feature) {

                        // Store the old feature names
                        var featureNames = FeatureName.readFeatureNames(feature);
                        FeatureName.offsetFeatureNames(featureNames, coordOffset);
                        FeatureName.featureNamesToProperties(featureNames, properties);
                        coordOffset += MapService.getCoordinateNo(feature);

                        // Delete the original feature
                        scope.deleteFeature(feature.getId());
                    });

                    // Create a feature for the merged geometry
                    scope.createNewFeature(geom, properties);
                    broadcast('refresh-all');
                };


                /** Checks if the current geometry selection can be converted to the given type **/
                scope.canConvert = function (toType) {
                    if (scope.selection.length !== 1) {
                        return false;
                    }
                    var fromType = scope.selection[0].getGeometry().getType();
                    switch (toType) {
                        case 'MultiPoint': return fromType === 'LineString' || fromType === 'Polygon';
                        case 'LineString': return fromType === 'MultiPoint' || fromType === 'Polygon';
                        case 'Polygon':    return fromType === 'MultiPoint' || fromType === 'LineString';
                    }
                    return false;
                };


                /** Converts the currently selected geometries to the given type **/
                scope.convert = function (toType) {
                    if (!scope.canConvert(toType)) {
                        return;
                    }

                    var feature = scope.selection[0];
                    var fromType = feature.getGeometry().getType();
                    var featureNames = FeatureName.readFeatureNames(feature);

                    // Get MultiPoint or LineString coordinates (same cardinality)
                    var coordinates = feature.getGeometry().getCoordinates();

                    // Convert coordinates if target type is Polygon
                    if (toType === 'Polygon') {
                        coordinates.push(coordinates[0]);
                        coordinates = [ coordinates ];
                    } else if (fromType === 'Polygon') {
                        coordinates = coordinates[0];
                        coordinates.pop();
                    }

                    // Delete the original feature
                    scope.deleteFeature(feature.getId());

                    // Construct the resulting geometry
                    var geom = new ol.geom[toType]();
                    geom.setCoordinates(coordinates);

                    // Create a feature for the converted geometry
                    scope.createNewFeature(geom, FeatureName.featureNamesToProperties(featureNames));
                    broadcast('refresh-all');
                };


                /** Splits the selected features into sub-geometries **/
                scope.split = function () {
                    var selected = scope.selection.slice();

                    angular.forEach(selected, function (feature) {
                        var geom = feature.getGeometry();
                        if (geom.getType() === 'Point') {
                            return;
                        }

                        var origFeatureNames = FeatureName.readFeatureNames(feature);
                        scope.deleteFeature(feature.getId());
                        var geoms = [];
                        switch (geom.getType()) {
                            case 'LineString':
                                angular.forEach(geom.getCoordinates(), function (coord) {
                                    geoms.push(new ol.geom.Point(coord));
                                });
                                break;
                            case 'Polygon':
                                // Note to self: Only the exterior ring has feature names associated.
                                // Thus, we do not have to offset the coord-indexes of the feature names
                                angular.forEach(geom.getCoordinates(), function (ringCoords) {
                                    // For linear rings, skip the last coordinate (same as the first)
                                    for (var x = 0; x < ringCoords.length - 1; x++) {
                                        geoms.push(new ol.geom.Point(ringCoords[x]));
                                    }
                                });
                                break;
                            case 'MultiPoint':
                                geoms = geom.getPoints();
                                break;
                            case 'MultiLineString':
                                geoms = geom.getLineStrings();
                                break;
                            case 'MultiPolygon':
                                geoms = geom.getPolygons();
                                break;
                            case 'GeometryCollection':
                                geoms = geom.getGeometries();
                                break;
                        }

                        var coordOffset = 0;
                        angular.forEach(geoms, function (g) {

                            // Determine the feature names to use for this geometry
                            var coordNo = MapService.getCoordinateNo(g);
                            var featureNames = FeatureName.featureNamesByOffset(origFeatureNames, coordOffset, coordOffset + coordNo);
                            FeatureName.offsetFeatureNames(featureNames, -coordOffset);
                            var properties = FeatureName.featureNamesToProperties(featureNames);
                            coordOffset += coordNo;

                            scope.createNewFeature(g.clone(), properties);
                        });
                    });
                    broadcast('refresh-all');
                };


                /** Compute the operation ("difference", "intersection", "union") between geometries **/
                scope.jstsOp = function (op) {
                    var selected = scope.selection.slice();
                    if (selected.length < 2) {
                        return;
                    }

                    var jstsOlParser = new jsts.io.OL3Parser();
                    var geom = null;
                    for (var x = 0; x < selected.length; x++) {
                        var feature = selected[x];
                        var g = jstsOlParser.read(feature.getGeometry());

                        if (geom == null) {
                            geom = g;
                        } else {
                            // Create the new geometry
                            switch (op) {
                                case "difference":
                                    geom = geom.difference(g);
                                    break;
                                case "intersection":
                                    geom = geom.intersection(g);
                                    break;
                                case "union":
                                    geom = geom.union(g);
                                    break;
                            }
                        }

                        // Delete the old features
                        scope.deleteFeature(feature.getId());
                    }

                    // Create the feature for the geometry
                    if (geom !== null) {
                        scope.createNewFeature(jstsOlParser.write(geom));
                    }
                    broadcast('refresh-all');
                };


                /** ************************ **/
                /** Feature modifications    **/
                /** ************************ **/


                /** Zooms to the feature **/
                scope.zoomFeature = function (id) {
                    broadcast('zoom-feature', id);
                };


                /** Deletes the feature **/
                scope.deleteFeature = function (id) {
                    var feature = findFeatureById(id);
                    if (feature) {
                        removeFromArray(scope.features, feature);
                        broadcast('feature-unselected', id);
                        broadcast('feature-removed', id);
                    }

                    var featureCtx = findFeatureContextById(id);
                    if (featureCtx) {
                        removeFromArray(scope.featureContexts, featureCtx);

                        // Check if there was an associated buffer-features
                        angular.forEach(findBufferedFeatureContexts(featureCtx.id), function (bufferFeatureCtx) {
                            scope.deleteFeature(bufferFeatureCtx.id);
                        });
                    }

                    // Update the list of selected features
                    recomputeFeatureSelection();
                };


                /** Called when a feature has been dragged to a new position **/
                scope.updateFeatureOrder = function (evt) {
                    // Feature contexts have already been updated. Swap features
                    var oldIndex = evt.oldIndex;
                    var newIndex = evt.newIndex;
                    console.log("DND MOVE " + oldIndex + " -> " + newIndex);
                    swapElements(scope.features, oldIndex, newIndex);
                    recomputeFeatureSelection();
                    broadcast('feature-order-changed', scope.featureContexts[newIndex].id);

                    // Check if we need to update an associated buffered features
                    angular.forEach(findBufferedFeatureContexts(scope.featureContexts[newIndex].id), function (bufferFeatureCtx) {
                        scope.checkUpdateBufferedFeature(bufferFeatureCtx);
                    });
                };

                scope.featureSortableCfg = {
                    group: 'feature',
                    handle: '.move-feature-btn',
                    onEnd: scope.updateFeatureOrder
                };


                /** Updates the buffered feature */
                scope.checkUpdateBufferedFeature = function (featureCtx) {
                    var feature = findFeatureById(featureCtx.id);
                    if (feature && feature.get('bufferType')) {
                        updateFeatureFromFeatureCtx(featureCtx);
                        updateBufferedFeatureGeometry(feature);
                        broadcast('feature-modified', feature.getId());
                    }
                };


                /** Called when the feature radius is updated **/
                scope.radiusUpdated = function (featureCtx) {
                    scope.checkUpdateBufferedFeature(featureCtx);
                };


                /** Called when the feature name is updated **/
                scope.nameUpdated = function (featureCtx) {
                    updateFeatureFromFeatureCtx(featureCtx);
                    broadcast('name-updated', featureCtx.id);
                };


                /** Called when the feature restriction is updated **/
                scope.restrictionUpdated = function (featureCtx) {
                    updateFeatureFromFeatureCtx(featureCtx);
                };


                /** Called when an AtoN has been selected on the AtoN map layer */
                scope.atonSelected = function (origAton) {
                    var g = new ol.geom.Point(MapService.fromLonLat([origAton.lon, origAton.lat]));
                    var feature = scope.createNewFeature(g);
                    var featureCtx = findFeatureContextById(feature.getId());
                    featureCtx.aton = angular.copy(origAton);
                    featureCtx.origAton = origAton;
                    featureCtx.showAton = true;
                    updateFeatureFromFeatureCtx(featureCtx);
                    broadcast('feature-added', feature.getId());
                };


                /** Called when an AtoN has been edited in the AtoN editor dialog **/
                scope.atonEdited = function (aton, featureCtx) {
                    var feature = findFeatureById(featureCtx.id);
                    if (aton && Object.keys(aton.tags).length > 0) {
                        featureCtx.aton = angular.copy(aton);

                        // Update the Point position of the feature from the AtoN
                        var geom = new ol.geom.Point();
                        geom.setCoordinates(MapService.fromLonLat([aton.lon, aton.lat]));
                        feature.setGeometry(geom);

                        updateFeatureFromFeatureCtx(featureCtx);
                        broadcast('feature-modified', feature.getId());

                        // Check if we need to update an associated buffered features
                        angular.forEach(findBufferedFeatureContexts(featureCtx.id), function (bufferFeatureCtx) {
                            scope.checkUpdateBufferedFeature(bufferFeatureCtx);
                        });

                    } else if (featureCtx.aton) {
                        featureCtx.aton = undefined;
                        featureCtx.origAton = undefined;
                        updateFeatureFromFeatureCtx(featureCtx);
                        broadcast('feature-modified', feature.getId());
                    }
                };


                /** Creates an AtoN associated with the given point **/
                scope.createAton = function (featureCtx) {
                    var feature = findFeatureById(featureCtx.id);
                    if (feature && featureCtx.type === 'Point') {
                        var lonLat = MapService.toLonLat(feature.getGeometry().getCoordinates());
                        var aton = {
                            lon: lonLat[0],
                            lat: lonLat[1],
                            tags: {}
                        };
                        AtonService.atonEditorDialog(aton, angular.copy(aton)).result
                            .then(function(editedAton) {
                                featureCtx.origAton = editedAton;
                                scope.atonEdited(editedAton, featureCtx);
                            });
                    }
                };


                /** Updates an associated AtoN whenever a feature has been updated **/
                scope.updateAton = function (featureCtx) {
                    var feature = findFeatureById(featureCtx.id);
                    if (feature && featureCtx.aton) {
                        var lonLat = MapService.toLonLat(feature.getGeometry().getCoordinates());
                        featureCtx.aton.lon = lonLat[0];
                        featureCtx.aton.lat = lonLat[1];
                        updateFeatureFromFeatureCtx(featureCtx);
                        scope.$$phase || scope.$apply();
                    }
                };


                /** Called when the selection is changed **/
                scope.selectionUpdated = function (featureCtx) {
                    broadcast(featureCtx.selected ? 'feature-selected' : 'feature-unselected', featureCtx.id);
                };


                /** ************************ **/
                /** Event handling           **/
                /** ************************ **/


                // Hook up a key listener that closes the editor when Escape is pressed
                function keydownListener(evt) {
                    if (evt.isDefaultPrevented()) {
                        return evt;
                    }
                    if (scope.mode === 'editor' && evt.which === 27) {
                        scope.exitEditor();
                        evt.preventDefault();
                        evt.stopPropagation();
                        scope.$$phase || scope.$apply();
                    }
                }

                $document.on('keydown', keydownListener);

                element.on('$destroy', function() {
                    $document.off('keydown', keydownListener);
                });


                /** Listens for a 'gj-editor-update' event  **/
                scope.$on('gj-editor-update', function(event, msg) {
                    // Do now process own events
                    if (msg.scope === scope.$id || msg.origScope === scope.$id) {
                        return;
                    }

                    event.preventDefault();
                    event.stopPropagation();

                    var featureCtx, feature;
                    switch (msg.type) {
                        case 'feature-added':
                            featureCtx = createFeatureCtxFromFeature(findFeatureById(msg.featureId));
                            if (featureCtx) {
                                scope.featureContexts.push(featureCtx);
                                scope.$$phase || scope.$apply();
                            }
                            break;

                        case 'feature-removed':
                            scope.deleteFeature(msg.featureId);
                            scope.$$phase || scope.$apply();
                            break;

                        case 'feature-modified':
                            // Notify other sub-directives
                            broadcast('feature-modified', msg.featureId, msg.scope);

                            // Check if any associated AtoN needs to be updated
                            featureCtx = createFeatureCtxFromFeature(findFeatureById(msg.featureId));
                            scope.updateAton(featureCtx);

                            // Check if we need to update an associated buffered feature
                            angular.forEach(findBufferedFeatureContexts(msg.featureId), function (bufferFeatureCtx) {
                                scope.checkUpdateBufferedFeature(bufferFeatureCtx);
                            });
                            break;

                        case 'feature-order-changed':
                            break;

                        case 'name-updated':
                            feature = findFeatureById(msg.featureId);
                            featureCtx = findFeatureContextById(msg.featureId);
                            if (feature && featureCtx) {
                                copyPropertiesFromFeature(feature, featureCtx);
                            }
                            // Notify other sub-directives
                            broadcast('name-updated', msg.featureId, msg.scope);
                            break;

                        case 'feature-selected':
                        case 'feature-unselected':
                            featureCtx = findFeatureContextById(msg.featureId);
                            if (featureCtx) {
                                featureCtx.selected = (msg.type === 'feature-selected');
                                recomputeFeatureSelection();
                                scope.$$phase || $rootScope.$$phase || scope.$apply();
                            }
                            break;
                    }
                });


                /** Listens for a 'gj-editor-edit' event for opening the editor **/
                scope.$on('gj-editor-edit', function(event, msg) {
                    // Only respond to the event if the ID sent along matches the ID of the directive
                    if (msg === attrs['id']) {
                        scope.openEditor();
                    }
                });
            }
        };
    }])



    /*******************************************************************
     * Controller that handles importing geometries from various
     * sources.
     *******************************************************************/
    .controller('GeometryImportDialogCtrl', ['$scope', '$rootScope', '$http', 'MapService', 'features',
        function ($scope, $rootScope, $http, MapService, features) {
            'use strict';

            $scope.featureCollection = {
                type: 'FeatureCollection',
                features: features || []
            };
            $scope.data = {
                message: undefined,
                area: undefined,
                geometryText: ''
            };
            $scope.domain = $rootScope.domain;

            // Initialize the editor from the features
            if (features && features.length > 0) {
                MapService.formatAsPlainText($scope.featureCollection)
                    .success(function (geometryText) {
                        $scope.data.geometryText = geometryText || '';
                        if (!geometryText) {
                            $scope.featureCollection.features.length = 0;
                            $scope.data.message = 'Could not parse original geometry as plain text!';
                        }
                    });
            }


            /** Called when an area has been selected **/
            $scope.areaSelected = function () {
                $scope.featureCollection.features.length = 0;
                if ($scope.data.area) {
                    // Load the area details including geometry
                    $http.get('/rest/areas/area/' + $scope.data.area.id)
                        .success(function (area) {
                            if (area && area.geometry) {
                                var feature = {
                                    id: MapService.uuid(),
                                    type: 'Feature',
                                    properties: {
                                        restriction: 'affected',
                                        areaId: area.id
                                    },
                                    geometry: area.geometry
                                };
                                angular.forEach(area.descs, function (desc) {
                                    feature.properties['name:' + desc.lang] = desc.name;
                                });
                                $scope.featureCollection.features.push(feature);
                            }
                        })
                }
            };


            /** Checks if the geometry text is valid **/
            $scope.geometryTextValid = function () {
                return $scope.data.geometryText && $scope.data.geometryText.length > 0
                    && $scope.featureCollection.features.length > 0;
            };


            /** Called whenever the geometry text changes **/
            $scope.geometryTextChanged = function () {
                $scope.featureCollection.features.length = 0;

                MapService.parsePlainText($scope.data.geometryText)
                    .success(function (fc) {
                        if (fc && fc.features && fc.features.length > 0) {
                            angular.forEach(fc.features, function (feature) {
                                $scope.featureCollection.features.push(feature);
                            });
                        }
                    });
            };


            /** Returns the fully loaded area to the callee **/
            $scope.importGeometry = function () {
                $scope.$close($scope.featureCollection.features);
            };
        }]);


