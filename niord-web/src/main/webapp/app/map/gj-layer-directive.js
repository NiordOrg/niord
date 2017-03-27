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
 * <p/>
 * Supports drawing a list of locations
 */
angular.module('niord.map')

    /**
     * The map-location-layer directive supports drawing a list of locations
     */
    .directive('mapGjLayer', ['$rootScope', 'MapService', function ($rootScope, MapService) {
        return {
            restrict: 'E',
            replace: false,
            template: '<div id="viewer-msg-info"/>',
            require: '^olMap',
            scope: {
                name: '@',
                visible: '=',
                layerSwitcher: '=',
                featureCollection: '=',
                fitExtent: '@',
                maxZoom: '@'
            },
            link: function(scope, element, attrs, ctrl) {
                var olScope = ctrl.getOpenlayersScope();
                var olLayer;
               var maxZoom = scope.maxZoom ? parseInt(scope.maxZoom) : 10;


                olScope.getMap().then(function(map) {

                    // Clean up when the layer is destroyed
                    scope.$on('$destroy', function() {
                        if (angular.isDefined(olLayer)) {
                            map.removeLayer(olLayer);
                        }
                    });

                    /***************************/
                    /** Construct Layer       **/
                    /***************************/


                    var style = new ol.style.Style({
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

                    var bufferedStyle = new ol.style.Style({
                        fill: new ol.style.Fill({
                            color: 'rgba(100, 50, 100, 0.2)'
                        }),
                        stroke: new ol.style.Stroke({
                            color: 'rgba(100, 50, 100, 0.8)',
                            width: 1
                        })
                    });

                    // Construct the layer
                    var features = new ol.Collection();
                    olLayer = new ol.layer.Vector({
                        source: new ol.source.Vector({
                            features: features,
                            wrapX: false
                        }),
                        style: function(feature) {
                            if (feature.get('parentFeatureIds')) {
                                return [ bufferedStyle ];
                            }
                            return [ style ];
                        }
                    });
                    olLayer = MapService.initLayer(olLayer, scope.name, scope.visible, scope.layerSwitcher);
                    map.addLayer(olLayer);


                    /***************************/
                    /** Locations             **/
                    /***************************/

                    scope.updateLayerFromFeatureCollection = function () {
                        olLayer.getSource().clear();
                        if (scope.featureCollection && scope.featureCollection.features.length > 0) {

                            angular.forEach(scope.featureCollection.features, function (gjFeature) {
                                var olFeature = MapService.gjToOlFeature(gjFeature);
                                olLayer.getSource().addFeature(olFeature);
                            });

                            if (scope.fitExtent === 'true') {
                                map.getView().fit(olLayer.getSource().getExtent(), {
                                    padding: [5, 5, 5, 5],
                                    size: map.getSize(),
                                    maxZoom: maxZoom
                                });
                            }

                        } else if (scope.fitExtent === 'true') {
                            map.getView().setCenter(MapService.fromLonLat(MapService.defaultCenterLonLat()));
                            map.getView().setZoom(MapService.defaultZoomLevel());
                        }
                    };

                    scope.$watch("featureCollection", scope.updateLayerFromFeatureCollection, true);


                });

            }
        };
    }]);

