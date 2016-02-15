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
                            if (feature.get('parentFeatureId')) {
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

                            if (scope.fitExtent == 'true') {
                                map.getView().fit(olLayer.getSource().getExtent(), map.getSize(), {
                                    padding: [5, 5, 5, 5],
                                    maxZoom: maxZoom
                                });
                            }
                        }
                    };

                    scope.$watch("featureCollection", scope.updateLayerFromFeatureCollection, true);


                });

            }
        };
    }]);

