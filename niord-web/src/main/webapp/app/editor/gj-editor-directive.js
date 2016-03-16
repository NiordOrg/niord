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
 * The GeoJson feature collection is divided into three lists:
 * <ul>
 *     <li>features: List of non-buffered OpenLayers Features, that can be edited in the data tree-view or map view.</li>
 *     <li>bufferFeatures: List of buffered OpenLayers Features that cannot be edited directly. They are tied to
 *         a non-buffered Feature via a parentFeatureId attribute + radius attributes that define the buffer.</li>
 *     <li>featureContexts: Angular is not really suited to $watch Features, which may be really large. Thus, for each
 *         feature, a feature context will be maintained, which contains editable attributes and a possible relation
 *         to a buffered feature.</li>
 * </ul>
 */
angular.module('niord.editor')

    /**
     * Defines the GeoJSON FeatureCollection Editor
     */
    .directive('gjEditor', ['$document', '$rootScope', 'MapService', function ($document, $rootScope, MapService) {
        return {
            restrict: 'E',
            templateUrl: '/app/editor/gj-editor-directive.html',
            replace: true,
            transclude: true,
            scope: {
                class:              '@',
                openEditorMessage:  "@",
                editType:           "@",
                featureCollection:  "="
            },
            link: function(scope, element, attrs) {

                scope.mode = 'viewer';
                scope.openEditorMessage = scope.openEditorMessage || 'Edit';
                scope.drawControl = 'select';
                scope.editorId = attrs['id'] + '-editor';
                scope.languages = $rootScope.modelLanguages;
                scope.editType = scope.editType || 'features';

                /** The corresponding non-buffered OpenLayer features being edited **/
                scope.features = [];

                /** Meta-data for the non-buffered OpenLayer features being edited **/
                scope.featureContexts = [];
                var featureCtxPrefixes = [ 'name', 'restriction', 'atonUid' ];
                var bufferFeatureCtxPrefixes = [ 'bufferRadius', 'bufferRadiusType', 'restriction', 'atonUid' ];

                /** The buffered OpenLayer features being edited **/
                scope.bufferFeatures = [];

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
                    if (arr && val && $.inArray(val, arr) != -1) {
                        arr.splice( $.inArray(val, arr), 1 );
                    }
                }

                /** Finds the feature with the given ID */
                function findByFeatureId(features, id) {
                    for (var x = 0; x < features.length; x++) {
                        if (features[x].getId() == id) {
                            return features[x];
                        }
                    }
                    return null;
                }

                /** Finds the item with the given property value */
                function findByProperty(list, key, value) {
                    for (var x = 0; x < list.length; x++) {
                        if (list[x][key] == value) {
                            return list[x];
                        }
                    }
                    return null;
                }


                /** Copy named properties from an OL feature to the feature context **/
                function copyPropertiesFromFeature(feature, featureCtx, namePrefixes) {
                    angular.forEach(feature.getKeys(), function (key) {
                        angular.forEach(namePrefixes, function (prefix) {
                            if (key.indexOf(prefix) == 0) {
                                featureCtx[key] = feature.get(key);
                            }
                        });
                    });
                }


                /** Copy named properties from a feature context to the OL feature **/
                function copyPropertiesToFeature(feature, featureCtx, namePrefixes) {
                    angular.forEach(featureCtx, function (value, key) {
                        angular.forEach(namePrefixes, function (prefix) {
                            if (key.indexOf(prefix) == 0) {
                                if (value) {
                                    feature.set(key, value);
                                } else {
                                    feature.unset(key);
                                }
                            }
                        });
                    });
                }

                /** Updates the feature ,and buffer feature if present, from the feature context **/
                function updateFeaturesFromFeatureCtx(featureCtx) {
                    var feature = findByFeatureId(scope.features, featureCtx.id);
                    copyPropertiesToFeature(feature, featureCtx, featureCtxPrefixes);
                    if (featureCtx.bufferFeatureId) {
                        var bufferFeature = findByFeatureId(scope.bufferFeatures, featureCtx.bufferFeatureId);
                        copyPropertiesToFeature(bufferFeature, featureCtx, bufferFeatureCtxPrefixes);
                    }
                }


                /** Creates a feature context based on the given feature **/
                function createFeatureCtxFromFeature(feature) {
                    if (feature) {
                        var featureCtx = {
                            id: feature.getId(),
                            selected: false,
                            restriction: undefined,
                            bufferFeatureId: undefined,
                            bufferRadius: undefined,
                            bufferRadiusType: undefined
                        };
                        copyPropertiesFromFeature(feature, featureCtx, featureCtxPrefixes);
                        return featureCtx;
                    }
                    return null;
                }


                /** Returns the list of selected features **/
                function selectedFeatures() {
                    var selected = [];
                    angular.forEach(scope.features, function (feature) {
                        if (feature.get('selected') === true) {
                            selected.push(feature);
                        }
                    });
                    return selected;
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
                    scope.bufferFeatures.length = 0;
                    scope.featureContexts.length = 0;

                    // Convert the GeoJson features to OpenLayers features and buffer features
                    angular.forEach(scope.featureCollection.features, function (gjFeature) {
                        var olFeature = MapService.gjToOlFeature(gjFeature);
                        MapService.checkCreateId(olFeature);
                        if (!olFeature.get('parentFeatureId')) {
                            scope.features.push(olFeature);
                        } else {
                            scope.bufferFeatures.push(olFeature);
                        }
                    });

                    // Build the list of feature contexts containing all non-buffer features
                    angular.forEach(scope.features, function (olFeature) {
                        var featureCtx = createFeatureCtxFromFeature(olFeature);
                        scope.featureContexts.push(featureCtx);
                    });

                    // Get rid of buffer features where the parentFeatureId does not exist (should never happen!)
                    scope.bufferFeatures = $.grep(scope.bufferFeatures, function (olFeature) {
                        return findByProperty(scope.featureContexts, 'id', olFeature.get('parentFeatureId')) != null;
                    });

                    // Chain feature contexts to buffered features
                    angular.forEach(scope.bufferFeatures, function (olFeature) {
                        var featureCtx = findByProperty(scope.featureContexts, 'id', olFeature.get('parentFeatureId'));
                        featureCtx.bufferFeatureId = olFeature.getId();
                        copyPropertiesFromFeature(olFeature, featureCtx, bufferFeatureCtxPrefixes);
                    });

                    // By default, display only the types of data that are filled out
                    angular.forEach(scope.featureContexts, function (featureCtx) {
                        featureCtx.showRestriction = featureCtx.restriction && featureCtx.restriction.length > 0;
                        featureCtx.showName = false;
                        angular.forEach(scope.languages, function (lang) {
                            if (featureCtx['name#' + lang] && featureCtx['name#' + lang].length > 0) {
                                featureCtx.showName = true;
                            }
                        });
                        featureCtx.showRadius = toMeters(featureCtx.bufferRadius, featureCtx.bufferRadiusType) > 0;
                        featureCtx.showAtoN = featureCtx.atonUid && featureCtx.atonUid.length > 0;
                    });

                    // Enter editor mode
                    scope.mode = 'editor';
                };


                /** Closes the editor and updates the locations if the save flag is true **/
                scope.exitEditor = function (save) {
                    if (save) {

                        // Convert the features into GeoJSON
                        scope.featureCollection.features.length = 0;

                        // Check if we need to merge all features into one
                        if (scope.editType == 'feature') {
                            scope.selectAll();
                            scope.merge();
                        }

                        // Convert to GeoJson feature collection
                        angular.forEach(scope.features, function (olFeature) {

                            var geoJsonFormat = new ol.format.GeoJSON();
                            var test = geoJsonFormat.writeGeometryObject(olFeature.getGeometry(), {
                                dataProjection: 'EPSG:4326',
                                featureProjection: 'EPSG:3857'
                            });

                            var gjFeature = MapService.olToGjFeature(olFeature);
                            scope.featureCollection.features.push(gjFeature);
                        });

                        // Merge in the buffered features, (TODO: right after the parent feature)
                        angular.forEach(scope.bufferFeatures, function (olFeature) {
                            var gjFeature = MapService.olToGjFeature(olFeature);
                            scope.featureCollection.features.push(gjFeature);
                        });

                    }
                    scope.mode = 'viewer';
                };


                /** ************************ **/
                /** Action Menu Functions    **/
                /** ************************ **/


                /** Clears the feature collection **/
                scope.clearAll = function () {
                    scope.features.length = 0;
                    scope.bufferFeatures.length = 0;
                    scope.featureContexts.length = 0;
                    broadcast('refresh-all');
                };


                /** Selects all features **/
                scope.selectAll = function () {
                    angular.forEach(scope.features, function (feature) {
                        feature.set('selected', true);
                    });
                };


                /** Zooms to the extent of the feature collection **/
                scope.zoomToExtent = function () {
                    broadcast('fit-extent');
                };


                /** Creates a new feature ***/
                scope.createNewFeature = function (geom) {
                    var f = new ol.Feature();
                    MapService.checkCreateId(f);
                    f.setGeometry(geom);

                    scope.features.push(f);
                    var featureCtx = createFeatureCtxFromFeature(f);
                    scope.featureContexts.push(featureCtx);
                    return f;
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


                /** Merges a g2 into g1 **/
                function mergeGeometries(g1, g2) {
                    if (g1.getType() == 'GeometryCollection') {
                        var geometries = g1.getGeometries() || [];
                        var g = (g2.getType() == 'GeometryCollection') ? g2.getGeometries() : [ g2 ];
                        angular.forEach(g, function (gg) { geometries.push(gg.clone()); });
                        g1.setGeometries(geometries);
                        //g1 = new ol.geom.GeometryCollection(geometries);
                        return g1;
                    } else {
                        switch (g2.getType()) {
                            case 'Point':
                            case 'MultiPoint':
                                var points = g2.getType() == 'Point' ? [ g2 ] : g2.getPoints();
                                angular.forEach(points, function (pt) { g1.appendPoint(pt.clone()); });
                                break;
                            case 'LineString':
                            case 'MultiLineString':
                                var lineStrings = g2.getType() == 'LineString' ? [ g2 ] : g2.getLineStrings();
                                angular.forEach(lineStrings, function (ls) { g1.appendLineString(ls.clone()); });
                                break;
                            case 'Polygon':
                            case 'MultiPolygon':
                                var polygons = g2.getType() == 'Polygon' ? [ g2 ] : g2.getPolygons();
                                angular.forEach(polygons, function (pol) { g1.appendPolygon(pol.clone()); });
                                break;
                        }
                        return g1;
                    }
                }


                /** Merges the selected features **/
                scope.merge = function () {
                    var selected = selectedFeatures();
                    if (selected.length < 2) {
                        return;
                    }
                    // NB: This could have been implemented easily with JSTS's union() feature.
                    //     However, in some cases this causes an error.

                    // Compute the type of the merged feature
                    var type = undefined;
                    angular.forEach(selected, function (feature) {
                        type = computeMergeType(type, feature.getGeometry().getType());
                    });

                    // Create a merged geometry
                    var geom = new ol.geom[type]();
                    if (type == 'GeometryCollection') {
                        geom.setGeometries([]);
                    }
                    angular.forEach(selected, function (feature) {
                        geom = mergeGeometries(geom, feature.getGeometry());
                        scope.deleteFeature(feature.getId());
                        // TODO: Copy name properties
                    });

                    // Create a feature for the merged geometry
                    scope.createNewFeature(geom);
                    broadcast('refresh-all');
                };


                /** Splits the selected features into sub-geometries **/
                scope.split = function () {
                    var selected = selectedFeatures();

                    angular.forEach(selected, function (feature) {
                        var geom = feature.getGeometry();
                        if (geom.getType() == 'Point' || geom.getType() == 'LineString' || geom.getType() == 'Polygon') {
                            return;
                        }
                        scope.deleteFeature(feature.getId());
                        var geoms = [];
                        switch (geom.getType()) {
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
                        angular.forEach(geoms, function (g) {
                            scope.createNewFeature(g.clone());
                        });
                    });
                    broadcast('refresh-all');
                };


                /** Creates the difference between two geometries **/
                scope.difference = function () {
                    var selected = selectedFeatures();
                    if (selected.length != 2) {
                        return;
                    }

                    var jstsOlParser = new jsts.io.olParser();
                    var f1 = selected[0];
                    var f2 = selected[1];
                    var g1 = jstsOlParser.read(f1.getGeometry());
                    var g2 = jstsOlParser.read(f2.getGeometry());

                    // Create the difference geometry
                    var geom = jstsOlParser.write(g1.difference(g2));

                    // Delete the old features
                    scope.deleteFeature(f1.getId());
                    scope.deleteFeature(f2.getId());

                    // Create the feature for the geometry
                    scope.createNewFeature(geom);
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
                    var feature = findByFeatureId(scope.features, id);
                    if (feature) {
                        removeFromArray(scope.features, feature);
                        broadcast('feature-removed', id);
                    }

                    var featureCtx = findByProperty(scope.featureContexts, "id", id);
                    if (featureCtx) {
                        removeFromArray(scope.featureContexts, featureCtx);


                        // Check if there was an associated buffer-feature
                        // TODO: Consider merging the two broadcasts into one...
                        var bufferFeature = findByFeatureId(scope.bufferFeatures, featureCtx.bufferFeatureId);
                        if (bufferFeature) {
                            removeFromArray(scope.bufferFeatures, bufferFeature);
                            broadcast('feature-removed', bufferFeature.getId());
                        }
                    }
                };


                /** Swaps the feature with the previous feature **/
                scope.moveFeatureUp = function (featureCtx) {
                    var index = $.inArray(featureCtx, scope.featureContexts);
                    if (index != -1) {
                        swapElements(scope.features, index, index - 1);
                        swapElements(scope.featureContexts, index, index - 1);
                        broadcast('feature-order-changed', featureCtx.id);
                    }
                };


                /** Swaps the feature with the next feature **/
                scope.moveFeatureDown = function (featureCtx) {
                    var index = $.inArray(featureCtx, scope.featureContexts);
                    if (index != -1) {
                        swapElements(scope.features, index, index + 1);
                        swapElements(scope.featureContexts, index, index + 1);
                        broadcast('feature-order-changed', featureCtx.id);
                    }
                };


                /** Called when the feature radius is updated **/
                scope.radiusUpdated = function (featureCtx) {
                    var dist = toMeters(featureCtx.bufferRadius, featureCtx.bufferRadiusType);

                    var feature = findByFeatureId(scope.features, featureCtx.id);
                    var bufferFeature = findByFeatureId(scope.bufferFeatures, featureCtx.bufferFeatureId);

                    if (dist > 0) {

                        if (bufferFeature) {
                            bufferFeature.setGeometry(MapService.bufferedOLGeometry(feature.getGeometry(), dist));
                            updateFeaturesFromFeatureCtx(featureCtx);
                            broadcast('feature-modified', bufferFeature.getId());
                        } else {
                            bufferFeature = MapService.bufferedOLFeature(feature, dist);
                            MapService.checkCreateId(bufferFeature);
                            bufferFeature.set('parentFeatureId', feature.getId());
                            featureCtx.bufferFeatureId = bufferFeature.getId();
                            scope.bufferFeatures.push(bufferFeature);
                            updateFeaturesFromFeatureCtx(featureCtx);
                            broadcast('feature-added', bufferFeature.getId());
                        }

                    } else {
                        if (bufferFeature) {
                            featureCtx.bufferFeatureId = null;
                            removeFromArray(scope.bufferFeatures, bufferFeature);
                            broadcast('feature-removed', bufferFeature.getId());
                        }
                    }
                };


                /** Called when the feature name is updated **/
                scope.nameUpdated = function (featureCtx) {
                    updateFeaturesFromFeatureCtx(featureCtx);
                    broadcast('name-updated', featureCtx.id);
                };


                /** Called when the feature restriction is updated **/
                scope.restrictionUpdated = function (featureCtx) {
                    updateFeaturesFromFeatureCtx(featureCtx);
                };


                /** Called when the feature AtoN is updated **/
                scope.atonUpdated = function (featureCtx) {
                    updateFeaturesFromFeatureCtx(featureCtx);
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
                    if (scope.mode == 'editor' && evt.which == 27) {
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
                    if (msg.scope == scope.$id || msg.origScope == scope.$id) {
                        return;
                    }

                    event.preventDefault();
                    event.stopPropagation();

                    var featureCtx, feature;
                    switch (msg.type) {
                        case 'feature-added':
                            featureCtx = createFeatureCtxFromFeature(findByFeatureId(scope.features, msg.featureId));
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

                            // Check if we need to update an associated buffered feature
                            featureCtx = findByProperty(scope.featureContexts, "id", msg.featureId);
                            if (featureCtx && featureCtx.bufferFeatureId) {
                                var dist = toMeters(featureCtx.bufferRadius, featureCtx.bufferRadiusType);
                                feature = findByFeatureId(scope.features, msg.featureId);
                                var bufferFeature = findByFeatureId(scope.bufferFeatures, featureCtx.bufferFeatureId);
                                bufferFeature.setGeometry(MapService.bufferedOLGeometry(feature.getGeometry(), dist));
                                broadcast('feature-modified', bufferFeature.getId());
                            }
                            break;

                        case 'feature-order-changed':
                            break;

                        case 'name-updated':
                            feature = findByFeatureId(scope.features, msg.featureId);
                            featureCtx = findByProperty(scope.featureContexts, "id", msg.featureId);
                            if (feature && featureCtx) {
                                copyPropertiesFromFeature(feature, featureCtx, featureCtxPrefixes);
                            }
                            // Notify other sub-directives
                            broadcast('name-updated', msg.featureId, msg.scope);
                            break;

                        case 'feature-selected':
                        case 'feature-unselected':
                            featureCtx = findByProperty(scope.featureContexts, "id", msg.featureId);
                            if (featureCtx) {
                                featureCtx.selected = (msg.type == 'feature-selected');
                                scope.$$phase || $rootScope.$$phase || scope.$apply();
                            }
                            break;
                    }
                });


                /** Listens for a 'gj-editor-edit' event for opening the editor **/
                scope.$on('gj-editor-edit', function(event, msg) {
                    // Only respond to the event if the ID sent along matches the ID of the directive
                    if (msg == attrs['id']) {
                        scope.openEditor();
                    }
                });
            }
        };
    }]);

