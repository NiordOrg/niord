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
 * Aid-to-navigation map layer directive.
 */
angular.module('niord.atons')

    /**
     * The aton-layer directive will add a vector layer that displays aid-to-navigation features.
     * Should be used within the ol-map directive, e.g.:
     * <pre>
     *   <ol-map class="message-list-map" map-state="state.map">
     *      <map-tile-layer name="OSM" visible="true" layer-switcher="false" source="OSM"></map-tile-layer>
     *      <map-aton-layer name="AtoN" visible="false" layer-switcher="true"></map-aton-layer>
     *   </ol-map>
     * </pre>
     *
     * <p/>
     * The aton-layer actually contains two layers - a vector layer and a raster (tile) layer. The vector layer
     * is used when the following conditions are met:
     * <ul>
     *     <li>The currently displayed map contains less than maxAtonNo AtoN's. Defaults to 2000.</li>
     *     <li>The zoom level is at least minZoomLevel. Defaults to 10.</li>
     * </ul>
     */
    .directive('mapAtonLayer', ['$rootScope', '$timeout', 'MapService', 'AtonService',
        function ($rootScope, $timeout, MapService, AtonService) {
            return {
                restrict: 'E',
                replace: false,
                template: '<div id="aton-info"/>',
                require: '^olMap',
                scope: {
                    name:           '@',
                    atons:          '=',
                    selection:      '=',
                    visible:        '=',
                    layerSwitcher:  '=',
                    maxAtonNo:      '=',
                    minZoomLevel:   '=',
                    atonSelected:   '&'
                },
                link: function(scope, element, attrs, ctrl) {
                    var olScope = ctrl.getOpenlayersScope();
                    var atonVectorLayer;
                    var atonSelectionLayer;
                    var atonTileLayer;
                    var atonLayers;
                    var loadTimer;

                    var maxAtonNo = scope.maxAtonNo | 2000;
                    var minZoomLevel = scope.minZoomLevel | 10;


                    // Wait for the parent map to be initialized before instantiation the layer, etc.
                    olScope.getMap().then(function(map) {


                        /***************************/
                        /** Updating Layers       **/
                        /***************************/


                        // Clean up when the directive is destroyed
                        scope.$on('$destroy', function() {
                            if (angular.isDefined(atonLayers)) {
                                map.removeLayer(atonLayers);
                            }
                            if (angular.isDefined(loadTimer)) {
                                $timeout.cancel(loadTimer);
                                loadTimer = undefined;
                            }
                        });


                        // Called in order to update the AtoN selection layer
                        scope.updateAtonSelectionLayer = function () {
                            if (atonSelectionLayer && atonSelectionLayer.getVisible()) {
                                // Convert the AtoN's to features
                                var features = [];
                                scope.selection.each(function (atonUid) {
                                    var atonCtx = scope.selection.get(atonUid);
                                    var aton = atonCtx.aton;
                                    var selectionFeature = new ol.Feature({
                                        geometry: new ol.geom.Point(MapService.fromLonLat([aton.lon, aton.lat]))
                                    });
                                    var selectionStyle = new ol.style.Style({
                                        image: AtonService.getAtonSelectionOLIcon(aton)
                                    });
                                    selectionFeature.setStyle(selectionStyle);
                                    features.push(selectionFeature);
                                });
                                atonSelectionLayer.getSource().clear(true);
                                atonSelectionLayer.getSource().addFeatures(features);
                            }
                        };


                        // Called in order to update the AtoN vector layer with the list of AtoN's
                        scope.updateAtonVectorLayer = function (atons) {
                            var zoom = map.getView().getZoom();

                            // Convert the AtoN's to features
                            var features = [];
                            angular.forEach(atons, function (aton) {
                                var atonFeature = new ol.Feature({
                                    geometry: new ol.geom.Point(MapService.fromLonLat([aton.lon, aton.lat])),
                                    aton: aton
                                });
                                var iconStyle = new ol.style.Style({
                                    image: AtonService.getAtonOLIcon(aton, zoom),
                                    text: AtonService.getAtonLabel(aton, zoom)
                                });
                                atonFeature.setStyle(iconStyle);
                                features.push(atonFeature);
                            });

                            atonTileLayer.setVisible(false);
                            atonVectorLayer.setVisible(true);
                            atonVectorLayer.getSource().clear(true);
                            atonVectorLayer.getSource().addFeatures(features);
                            if (atonSelectionLayer) {
                                atonSelectionLayer.setVisible(true);
                                atonSelectionLayer.getSource().clear(true);
                                scope.updateAtonSelectionLayer();
                            }
                        };


                        // Called when the AtoN tile layer should be enabled
                        scope.updateAtonTileLayer = function () {
                            atonTileLayer.setVisible(true);
                            atonVectorLayer.setVisible(false);
                            atonVectorLayer.getSource().clear(true);
                            if (atonSelectionLayer) {
                                atonSelectionLayer.setVisible(false);
                                atonSelectionLayer.getSource().clear(true);
                            }
                        };


                        /***************************/
                        /** Layer creation        **/
                        /***************************/

                        var layers = [];

                        // Create the selection layer
                        if (scope.selection) {
                            atonSelectionLayer = new ol.layer.Vector({
                                source: new ol.source.Vector({
                                    features: []
                                })
                            });
                            layers.push(atonSelectionLayer);
                        }

                        // Create the vector layer
                        atonVectorLayer = new ol.layer.Vector({
                            source: new ol.source.Vector({
                                features: []
                            })
                        });
                        layers.push(atonVectorLayer);

                        // Create AtoN tile layer
                        atonTileLayer = new ol.layer.Tile({
                            source: new ol.source.XYZ({
                                url: '/rest/aton-tiles/{z}/{x}/{y}.png',
                                crossOrigin: 'anonymous'
                            })
                        });
                        layers.push(atonTileLayer);

                        // Create the group layer for the tile and vector layers and add it to the map
                        atonLayers = new ol.layer.Group({
                            layers: layers
                        });
                        atonLayers = MapService.initLayer(atonLayers, scope.name, scope.visible, scope.layerSwitcher);
                        map.addLayer(atonLayers);


                        /***************************/
                        /** AtoN Selection        **/
                        /***************************/

                        if (scope.selection) {

                            scope.updateFeatureSelection = function (feature) {
                                var aton = feature.get('aton');
                                if (scope.selection.get(AtonService.getAtonUid(aton))) {
                                    scope.selection.remove(AtonService.getAtonUid(aton));
                                } else {
                                    scope.selection.put(AtonService.getAtonUid(aton), {
                                        aton: angular.copy(aton),
                                        orig: aton
                                    });
                                }
                                scope.$$phase || $rootScope.$$phase || scope.$apply();
                            };

                            var select = new ol.interaction.Select({
                                filter: function (feature, layer) {
                                    return layer == atonVectorLayer;
                                }
                            });

                            select.on('select', function (e) {
                                if (e.selected && e.selected.length > 0) {
                                    var feature = e.selected[0];
                                    var aton = feature.get('aton');
                                    AtonService.atonDetailsDialog(aton, true)
                                        .result.then(function() {
                                            scope.updateFeatureSelection(feature);
                                        });
                                }
                            });
                            select.setActive(true);
                            map.addInteraction(select);

                            var dragBox = new ol.interaction.DragBox({
                                condition: ol.events.condition.platformModifierKeyOnly
                            });

                            dragBox.on('boxend', function() {
                                var extent = dragBox.getGeometry().getExtent();
                                atonVectorLayer.getSource().forEachFeatureIntersectingExtent(extent, scope.updateFeatureSelection);
                            });
                            dragBox.setActive(true);
                            map.addInteraction(dragBox);

                            // Listen for selection changes
                            scope.$watchCollection("selection.keys", scope.updateAtonSelectionLayer, true);
                        }


                        /***************************/
                        /** Auto-load AtoNs       **/
                        /***************************/

                        if (!scope.atons) {
                            // If no "atons" list is passed to the directive, the directive is responsible
                            // for loading the atons itself.

                            // Reload the AtoN's
                            scope.loadAtons = function () {
                                loadTimer = undefined;
                                var extent = map.getView().calculateExtent(map.getSize());
                                extent = MapService.toLonLatExtent(extent);

                                // We only ever update the vector layer when we have reached a certain zoom level
                                if (map.getView().getZoom() < minZoomLevel) {
                                    scope.updateAtonTileLayer();
                                    return;
                                }

                                // Load at most scope.maxAtonNo AtoN's
                                AtonService.searchAtonsByExtent(extent, maxAtonNo).success(
                                    function (result) {
                                        if (result.data != null) {
                                            scope.updateAtonVectorLayer(result.data);
                                        } else {
                                            if (result.total > 0) {
                                                scope.updateAtonTileLayer();
                                            } else {
                                                scope.updateAtonVectorLayer([]);
                                            }
                                        }
                                    });
                            };


                            // When the map extent changes, reload the AtoN's using a timer to batch up changes
                            scope.mapChanged = function () {
                                if (atonLayers.getVisible()) {
                                    // Make sure we reload at most every half second
                                    if (loadTimer) {
                                        $timeout.cancel(loadTimer);
                                    }
                                    loadTimer = $timeout(scope.loadAtons, 500);
                                }
                            };
                            map.on('moveend', scope.mapChanged);

                            // Listen for visibility changes of the AtoN layer group
                            atonLayers.on('change:visible', scope.mapChanged);
                        }


                        /***************************/
                        /** Provided AtoNs        **/
                        /***************************/

                        if (scope.atons) {
                            // If an "atons" list is passed to the directive, the directive will display this list.

                            scope.atonsChanged = function () {
                                if (scope.atons.length > 0 && scope.atons.length < maxAtonNo) {
                                    scope.updateAtonVectorLayer(scope.atons);
                                } else {
                                    scope.updateAtonTileLayer();
                                }
                            };

                            // Hook up a collection listener
                            scope.$watchCollection("atons", scope.atonsChanged, true);
                        }


                        /***************************/
                        /** Tooltips              **/
                        /***************************/


                        // Returns the list of atons for the given pixel
                        scope.getAtonsForPixel = function (pixel) {
                            var atons = [];
                            map.forEachFeatureAtPixel(pixel, function(feature, layer) {
                                if (layer  == atonVectorLayer && feature.get('aton')) {
                                    atons.push(feature.get('aton'));
                                }
                            });
                            return atons;
                        };


                        // Prepare the tooltip
                        var info = $('#aton-info');
                        info.tooltip({
                            animation: false,
                            trigger: 'manual',
                            html: true
                        });


                        // Show tooltip info
                        var updateAtonTooltip = function(pixel) {
                            var atons = scope.getAtonsForPixel(pixel);

                            if (atons.length > 0 && atons.length <= 4) {
                                // Build the html to display in the tooltip
                                var html = '';
                                angular.forEach(atons, function (aton) {
                                    html +=
                                        '<div><strong>' + AtonService.getAtonUid(aton) + '</strong></div>' +
                                        '<div><small>' + aton.tags['seamark:name'] + '</small></div>';
                                });

                                // Update the tooltip
                                info.css({
                                    left: pixel[0] + 'px',
                                    top: (pixel[1] - 10) + 'px'
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
                            updateAtonTooltip(map.getEventPixel(evt.originalEvent));
                        });


                        /***************************/
                        /** Details dialog        **/
                        /***************************/


                        // Open the AotN info dialog
                        scope.showAtonInfo = function (aton) {
                            var selectable = attrs.atonSelected !== undefined;
                            AtonService.atonDetailsDialog(aton, selectable).result
                                .then(function (aton) {
                                    if (aton && selectable) {
                                        scope.atonSelected({aton: aton});
                                    }
                                });
                        };


                        // Show AtoN info dialog when an AtoN is clicked
                        if (!atonSelectionLayer) {
                            map.on('click', function(evt) {
                                var atons = scope.getAtonsForPixel(map.getEventPixel(evt.originalEvent));
                                if (atons.length >= 1) {
                                    // Do not handle the click event if a Draw interaction is active
                                    var interactions = MapService.getActiveInteractions(map, ol.interaction.Draw);
                                    if (interactions.length > 0) {
                                        return;
                                    }

                                    info.tooltip('hide');
                                    scope.showAtonInfo(atons[0]);
                                }
                            });
                        }

                        /***************************/
                        /** Event handling        **/
                        /***************************/


                        /** Listens for a 'zoom-to-aton' event **/
                        scope.$on('zoom-to-aton', function(event, msg) {
                            var point = new ol.geom.Point(MapService.fromLonLat([msg.lon, msg.lat]));
                            map.getView().fit(point, map.getSize(), {
                                padding: [20, 20, 20, 20],
                                maxZoom: 12
                            });
                        });

                    });
                }
            };
        }]);
