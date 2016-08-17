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
 * Location layer directive.
 * <p>
 */
angular.module('niord.editor')

    /**
     * The map-location-layer directive supports drawing and editing a list of features
     */
    .directive('mapOlEditorLayer', ['$rootScope', 'MapService', function ($rootScope, MapService) {
        return {
            restrict: 'E',
            replace: false,
            template: '<div id="msg-info"/>',
            require: '^olMap',
            scope: {
                name:           '@',
                visible:        '=',
                layerSwitcher:  '=',
                features:       '=',
                drawControl:    '='
            },
            link: function(scope, element, attrs, ctrl) {
                var olScope = ctrl.getOpenlayersScope();
                var olLayer;
                var bufferLayer;
                var interactions = [];


                olScope.getMap().then(function(map) {

                    // Clean up when the layer is destroyed
                    scope.$on('$destroy', function() {
                        if (angular.isDefined(olLayer)) {
                            map.removeLayer(olLayer);
                            map.removeLayer(bufferLayer);
                            angular.forEach(interactions, function (interaction) {
                                map.removeInteraction(interaction);
                            });
                        }
                    });

                    /** Emits a 'gj-editor-update' message to the parent directive **/
                    function emit(type, featureId, origScope) {
                        scope.$emit('gj-editor-update', {
                            type: type,
                            featureId: featureId,
                            scope: scope.$id,
                            origScope: origScope
                        });
                    }

                    /** ************************ **/
                    /** Buffer Feature Layer     **/
                    /** ************************ **/

                    var bufferedStyle = new ol.style.Style({
                        fill: new ol.style.Fill({
                            color: 'rgba(100, 70, 100, 0.1)'
                        }),
                        stroke: new ol.style.Stroke({
                            color: 'rgba(100, 70, 100, 0.5)',
                            width: 1
                        })
                    });

                    var bufferFeatures = new ol.Collection();
                    bufferLayer = new ol.layer.Vector({
                        source: new ol.source.Vector({
                            features: bufferFeatures,
                            wrapX: false
                        }),
                        style: [ bufferedStyle ]
                    });
                    map.addLayer(bufferLayer);


                    /** ************************ **/
                    /** Feature Layer            **/
                    /** ************************ **/


                    var normalStyle = new ol.style.Style({
                        fill: new ol.style.Fill({
                            color: 'rgba(255, 0, 255, 0.2)'
                        }),
                        stroke: new ol.style.Stroke({
                            color: '#8B008B',
                            width: 1
                        }),
                        image: new ol.style.Circle({
                            radius: 4,
                            stroke: new ol.style.Stroke({
                                color: '#8B008B',
                                width: 1
                            }),
                            fill: new ol.style.Fill({
                                color: 'rgba(255, 0, 255, 0.6)'
                            })
                        })
                    });

                    var selectedStyle = new ol.style.Style({
                        fill: new ol.style.Fill({
                            color: 'rgba(255, 0, 255, 0.2)'
                        }),
                        stroke: new ol.style.Stroke({
                            color: '#8B008B',
                            width: 3
                        }),
                        image: new ol.style.Circle({
                            radius: 4,
                            stroke: new ol.style.Stroke({
                                color: '#8B008B',
                                width: 3
                            }),
                            fill: new ol.style.Fill({
                                color: 'rgba(255, 0, 255, 0.6)'
                            })
                        })
                    });

                    var sourceOrder = function(feature1, feature2) {
                        return $.inArray(feature1, scope.features) - $.inArray(feature2, scope.features);
                    };

                    // Construct the layer
                    var features = new ol.Collection();
                    olLayer = new ol.layer.Vector({
                        source: new ol.source.Vector({
                            features: features,
                            renderOrder: sourceOrder,
                            wrapX: false
                        }),
                        style: [ normalStyle ]
                    });

                    // Surely an OpenLayers error. Render order only works if you call
                    // layer.setRenderOrder(), which only works in ol-debug.js :-(
                    //olLayer.setRenderOrder(sourceOrder);
                    olLayer = MapService.initLayer(olLayer, scope.name, scope.visible, scope.layerSwitcher);
                    map.addLayer(olLayer);


                    /***************************/
                    /** Drag and Drop         **/
                    /***************************/


                    var dragAndDropInteraction = new ol.interaction.DragAndDrop({
                        formatConstructors: [
                            ol.format.GPX,
                            ol.format.GeoJSON,
                            ol.format.IGC,
                            ol.format.KML,
                            ol.format.TopoJSON
                        ]
                    });
                    map.addInteraction(dragAndDropInteraction);
                    interactions.push(dragAndDropInteraction);

                    function updateNames(feature, name) {
                        angular.forEach($rootScope.modelLanguages, function (lang) {
                            feature.set('name:' + lang, name);
                        });
                    }

                    dragAndDropInteraction.on('addfeatures', function(event) {
                        angular.forEach(event.features, function (feature) {

                            // If this is from MarineRegions, coordinates are in the wrong order
                            if (feature.get('mrgid')) {
                                // First transform back to data projection
                                feature.getGeometry().transform(MapService.featureProjection(), MapService.dataProjection());
                                // Swap X and Y
                                MapService.swapXYCoordinates(feature);
                                // transform back to feature projection
                                feature.getGeometry().transform(MapService.dataProjection(), MapService.featureProjection());

                                if (feature.get('country')) {
                                    updateNames(feature, feature.get('country'));
                                }
                            }

                            if (feature.get('name')) {
                                updateNames(feature, feature.get('name'));
                            }

                            olLayer.getSource().addFeature(feature);
                        });
                        scope.$$phase || scope.$apply();
                    });


                    /***************************/
                    /** Draw Controls         **/
                    /***************************/


                    //var select = new ol.interaction.Select({condition: ol.events.condition.click});
                    var select = new ol.interaction.Select({
                        filter: function (feature, layer) {
                            return layer == olLayer;
                        }
                    });

                    select.getFeatures().on('add', function(evt) {
                        evt.element.set('selected', true);
                        scope.updateFeatureStyle(evt.element.getId());
                        emit('feature-selected', evt.element.getId());
                    });
                    select.getFeatures().on('remove', function(evt) {
                        evt.element.set('selected', false);
                        scope.updateFeatureStyle(evt.element.getId());
                        emit('feature-unselected', evt.element.getId());
                    });

                    var modify = new ol.interaction.Modify({
                        features: select.getFeatures(),
                        deleteCondition: function(event) {
                            return ol.events.condition.shiftKeyOnly(event)
                                && ol.events.condition.singleClick(event);
                        }
                    });

                    var drag = new niord_ol.Drag({source: olLayer.getSource()});

                    var remove = new niord_ol.Remove({ source: olLayer.getSource() });

                    var dragBox = new ol.interaction.DragBox({
                        condition: ol.events.condition.platformModifierKeyOnly
                    });

                    dragBox.on('boxstart', function(e) {
                        select.getFeatures().clear();
                        scope.$$phase || scope.$apply();
                    });

                    dragBox.on('boxend', function(e) {
                        var extent = dragBox.getGeometry().getExtent();
                        olLayer.getSource().forEachFeatureIntersectingExtent(extent, function(feature) {
                            select.getFeatures().push(feature);
                        });
                    });
                    dragBox.setActive(true);
                    map.addInteraction(dragBox);
                    interactions.push(dragBox);

                    var box = new ol.interaction.Draw({
                        source: olLayer.getSource(),
                        type: 'LineString',
                        geometryFunction: function(coordinates, geometry) {
                            if (!geometry) {
                                geometry = new ol.geom.Polygon(null);
                            }
                            var start = coordinates[0];
                            var end = coordinates[1];
                            geometry.setCoordinates([
                                [start, [start[0], end[1]], end, [end[0], start[1]], start]
                            ]);
                            return geometry;
                        }
                    });

                    var drawControls = {
                        point    : [ new ol.interaction.Draw({ source: olLayer.getSource(), type: 'Point' }) ],
                        polyline : [ new ol.interaction.Draw({ source: olLayer.getSource(), type: 'LineString' }) ],
                        polygon  : [ new ol.interaction.Draw({ source: olLayer.getSource(), type: 'Polygon' }) ],
                        box      : [ box ],
                        select   : [ select, drag, modify ],
                        remove   : [ remove ]
                    };

                    // Add the draw-controls to the map
                    angular.forEach(drawControls, function (drawCtrls) {
                        angular.forEach(drawCtrls, function (drawCtrl) {
                            drawCtrl.setActive(false);
                            map.addInteraction(drawCtrl);
                            interactions.push(drawCtrl);
                        })
                    });

                    // Enable the drawcontrol with the given name
                    scope.activateDrawControl = function (name) {
                        angular.forEach(drawControls, function (drawCtrls, key) {
                            angular.forEach(drawCtrls, function (drawCtrl) {
                                drawCtrl.setActive(name == key);
                            })
                        });
                        select.getFeatures().clear();
                    };

                    scope.$watch("drawControl", scope.activateDrawControl, true);


                    /***************************/
                    /** Feature changes       **/
                    /***************************/


                    scope.updatingFeatures = false;

                    /** Handle when a feature is added to the layer **/
                    scope.featureAdded = function (evt) {
                        if (!scope.updatingFeatures) {
                            var feature = evt.feature;
                            feature.setId(MapService.uuid());
                            scope.features.push(feature);
                            scope.updateFeatureStyle(feature.getId());
                            emit('feature-added', feature.getId());
                        } else {
                            scope.updateFeatureStyle(evt.feature.getId());
                        }
                    };

                    /** Handle when a feature is removed **/
                    scope.featureRemoved = function (evt) {
                        var features = evt.features.getArray();
                        angular.forEach(features, function (feature) {
                            if ($.inArray(feature, scope.features) != -1) {
                                scope.features.splice( $.inArray(feature, scope.features), 1 );
                            }
                            emit('feature-removed', feature.getId());
                        });
                    };

                    /** Handle when a feature has been modified **/
                    scope.featureModified = function (evt) {
                        var features = evt.features.getArray();
                        angular.forEach(features, function (feature) {
                            scope.updateFeatureStyle(feature.getId());
                            emit('feature-modified', feature.getId());
                        });
                    };

                    // For modifications, we only want to process the event when the change is complete
                    //    - not while the feature is being modified (i.e. one event instead of many).
                    modify.on("modifyend", scope.featureModified);
                    drag.on("modifyend", scope.featureModified);
                    remove.on("featureremove", scope.featureRemoved);
                    olLayer.getSource().on("addfeature", scope.featureAdded);
                    bufferLayer.getSource().on("addfeature", scope.featureAdded);


                    /***************************/
                    /** features             **/
                    /***************************/


                    /** Re-builds the feature layers of the map **/
                    scope.updateLayers = function () {

                        scope.updatingFeatures = true;
                        try {

                            olLayer.getSource().clear(true);
                            bufferLayer.getSource().clear(true);
                            if (scope.features) {
                                angular.forEach(scope.features, function (feature) {
                                    if (feature.get('parentFeatureIds')) {
                                        bufferLayer.getSource().addFeature(feature);
                                    } else {
                                        olLayer.getSource().addFeature(feature);
                                    }
                                });
                            }
                        } finally {
                            scope.updatingFeatures = false;
                        }
                    };


                    /** Fits the view to the features **/
                    scope.fitExtent = function () {
                        if (scope.features.length > 0) {
                            map.getView().fit(olLayer.getSource().getExtent(), map.getSize(), {
                                padding: [20, 20, 20, 20],
                                maxZoom: 12
                            });
                        }
                    };


                    /** Zooms in on the given feature **/
                    scope.zoomFeature = function (id) {
                        var feature = olLayer.getSource().getFeatureById(id);
                        if (feature) {
                            map.getView().fit(feature.getGeometry().getExtent(), map.getSize(), {
                                padding: [20, 20, 20, 20],
                                maxZoom: 12
                            });
                            select.getFeatures().clear();
                            select.getFeatures().push(feature);
                        }
                    };


                    /** Creates a feature style that displays the feature name in the "middle" of the feature **/
                    scope.styleForFeatureName = function (feature, name) {
                        return new ol.style.Style({
                            text: new ol.style.Text({
                                textAlign: 'center',
                                font: '11px Arial',
                                text: name,
                                fill: new ol.style.Fill({color: 'darkmagenta'}),
                                stroke: new ol.style.Stroke({color: 'white', width: 2.0}),
                                offsetX: 0,
                                offsetY: 5
                            }) ,
                            geometry: function(feature) {
                                var point = MapService.getGeometryCenter(feature.getGeometry());
                                return (point) ? new ol.geom.Point(point) : null;
                            }
                        });
                    };


                    /** Creates a features style that displays the name of a specific coordinate **/
                    scope.styleForFeatureCoordName = function (feature, name, coord) {
                        return new ol.style.Style({
                            text: new ol.style.Text({
                                textAlign: 'center',
                                font: '11px Arial',
                                text: name,
                                fill: new ol.style.Fill({color: 'darkmagenta'}),
                                stroke: new ol.style.Stroke({color: 'white', width: 2.0}),
                                offsetX: 0,
                                offsetY: 10
                            }),
                            image: new ol.style.Circle({
                                radius: 2,
                                fill: new ol.style.Fill({
                                    color: 'rgb(50, 0, 50)'
                                }),
                                stroke: new ol.style.Stroke({color: 'darkmagenta', width: 2})
                            }),
                            geometry: function() {
                                return new ol.geom.Point(coord);
                            }
                        });
                    };


                    /** Updates the styles for the given feature **/
                    scope.updateFeatureStyle = function (id) {
                        var styles;
                        var feature = olLayer.getSource().getFeatureById(id);

                        if (feature) {
                            styles = feature.get('selected') ? [ selectedStyle ] : [ normalStyle ];
                        } else {
                            feature = bufferLayer.getSource().getFeatureById(id);
                            styles = [ bufferedStyle ];
                        }

                        if (feature) {
                            var featureNames = FeatureName.readFeatureNames(feature);

                            angular.forEach(featureNames, function (name) {

                                if (name.isFeatureName() && name.getLanguage() == $rootScope.language) {
                                    var featureNameStyle = scope.styleForFeatureName(feature, name.getValue());
                                    styles.push(featureNameStyle);
                                } else if (name.isFeatureCoordName() && name.getLanguage() == $rootScope.language) {
                                    var coord = MapService.getCoordinateAtIndex(feature, name.getCoordIndex());
                                    if (coord) {
                                        var coordNameStyle = scope.styleForFeatureCoordName(feature, name.getValue(), coord);
                                        styles.push(coordNameStyle);
                                    }
                                }

                            });
                            feature.setStyle(styles);
                        }
                    };

                    // Bootstrap the layer update
                    scope.updateLayers();
                    scope.fitExtent();


                    /***************************/
                    /** Event handling        **/
                    /***************************/


                    /** Listens for a 'gj-editor-update' event **/
                    scope.$on('gj-editor-update', function(event, msg) {
                        // Do now process own events
                        if (msg.scope == scope.$id || msg.origScope == scope.$id) {
                            return;
                        }

                        switch (msg.type) {
                            case 'refresh-all':
                                select.getFeatures().clear();
                                scope.updateLayers();
                                break;

                            case 'feature-added':
                                scope.updateFeatureStyle(msg.featureId);
                                scope.updateLayers();
                                break;

                            case 'feature-removed':
                            case 'feature-order-changed':
                                scope.updateLayers();
                                break;

                            case 'feature-modified':
                                scope.updateFeatureStyle(msg.featureId);
                                // Geometry already updated
                                break;

                            case 'zoom-feature':
                                scope.zoomFeature(msg.featureId);
                                break;

                            case 'fit-extent':
                                scope.fitExtent();
                                break;

                            case 'name-updated':
                                scope.updateFeatureStyle(msg.featureId);
                                scope.updateLayers();
                                break;

                            case 'feature-selected':
                            case 'feature-unselected':
                                var feature = olLayer.getSource().getFeatureById(msg.featureId);
                                if (feature) {
                                    if  (msg.type == 'feature-selected') {
                                        select.getFeatures().push(feature);
                                    } else {
                                        select.getFeatures().remove(feature);
                                    }
                                }
                                break;
                        }
                    });


                    /***************************/
                    /** Tooltips              **/
                    /***************************/


                    // Returns the list of messages for the given pixel
                    scope.getFeatureForPixel = function (pixel) {
                        var msgs = [];
                        map.forEachFeatureAtPixel(pixel, function(feature, layer) {
                            // test
                            if (layer  == olLayer) {
                                msgs.push(feature);
                            }
                        });
                        return msgs;
                    };


                    // Prepare the tooltip
                    var info = $('#msg-info');
                    info.tooltip({
                        animation: false,
                        trigger: 'manual',
                        html: true,
                        placement: 'bottom'
                    });


                    // Show tooltip info
                    var updateMsgTooltip = function(pixel) {
                        var features = scope.getFeatureForPixel(pixel);
                        var langKey = "name:" + $rootScope.language;

                        // Build the html to display in the tooltip
                        var html = '';
                        angular.forEach(features, function (feature) {
                            var name = feature.get(langKey);
                            if (name) {
                                html += '<div><small>' + name + '</small></div>';
                            }
                        });

                        if (html.length > 0) {

                            // Update the tooltip
                            info.css({
                                left: pixel[0] + 'px',
                                top: (pixel[1] + 15) + 'px'
                            });
                            info.tooltip('hide')
                                .attr('data-original-title', html)
                                .tooltip('fixTitle')
                                .tooltip('show');

                        } else {
                            info.tooltip('hide');
                        }
                    };


                    // Update the tooltip whenever the mouse is moved
                    map.on('pointermove', function(evt) {
                        if (evt.dragging) {
                            info.tooltip('hide');
                            return;
                        }
                        updateMsgTooltip(map.getEventPixel(evt.originalEvent));
                    });

                });

            }
        };
    }]);

