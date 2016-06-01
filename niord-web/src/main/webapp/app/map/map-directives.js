/**
 * Base map directives.
 * <p/>
 * Inspiration:
 * https://github.com/tombatossals/angular-openlayers-directive
 * <p/>
 * Example usage:
 * <pre>
 *   <ol-map class="message-list-map" map-state="state.map">
 *      <map-tile-layer name="OSM" visible="true" layer-switcher="false" source="OSM"></map-tile-layer>
 *      <map-aton-layer name="AtoN" visible="false" layer-switcher="true"></map-aton-layer>
 *      <map-layer-switcher></map-layer-switcher>
 *   </ol-map>
 * </pre>
 */
angular.module('niord.map')

    /**
     * Defines the parent ol-map directive.
     */
    .directive('olMap', ['$rootScope', '$q', '$timeout', 'MapService', function ($rootScope, $q, $timeout, MapService) {
        return {
            restrict: 'EA',
            replace: true,
            transclude: true,
            template: '<div class="map {{class}}" ng-transclude></div>',
            scope: {
                mapState: '=',
                readonly: '='
            },

            controller: function($scope) {
                var _map = $q.defer();

                $scope.getMap = function() {
                    return _map.promise;
                };

                $scope.setMap = function(map) {
                    _map.resolve(map);
                };

                this.getOpenlayersScope = function() {
                    return $scope;
                };
            },

            link: function(scope, element, attrs) {
                var isDefined = angular.isDefined;
                var updateSizeTimer;


                // Clean-up
                element.on('$destroy', function() {
                    if (isDefined(updateSizeTimer)) {
                        $timeout.cancel(updateSizeTimer);
                        updateSizeTimer = null;
                    }
                });


                // Set width and height if they are defined
                if (isDefined(attrs.width)) {
                    if (isNaN(attrs.width)) {
                        element.css('width', attrs.width);
                    } else {
                        element.css('width', attrs.width + 'px');
                    }
                }


                if (isDefined(attrs.height)) {
                    if (isNaN(attrs.height)) {
                        element.css('height', attrs.height);
                    } else {
                        element.css('height', attrs.height + 'px');
                    }
                }


                // Disable rotation on mobile devices
                var controls = scope.readonly ? [] : ol.control.defaults({ rotate: false });
                var interactions = scope.readonly ? [] : ol.interaction.defaults({ altShiftDragRotate: false, pinchRotate: false});

                var layers = [];
                var view = new ol.View();
                var map = new ol.Map({
                    target: angular.element(element)[0],
                    layers: layers,
                    view: view,
                    controls: controls,
                    interactions: interactions
                });


                // Set extent (center and zoom) of the map.
                scope.updateMapExtent = function (initial) {
                    // Default values
                    var center = MapService.defaultCenterLonLat();
                    var zoom = MapService.defaultZoomLevel();

                    // Check if the center is defined in the directive attributes or in the mapState
                    if (initial && isDefined(attrs.lat) && isDefined(attrs.lon)) {
                        center = [  parseFloat(attrs.lon), parseFloat(attrs.lat) ];
                    } else if (isDefined(scope.mapState) && isDefined(scope.mapState.center)) {
                        center = scope.mapState.center;
                    }

                    // Check if the zoom is defined in the directive attributes or in the mapState
                    if (initial && isDefined(attrs.zoom)) {
                        zoom = parseFloat(attrs.zoom);
                    } else if (isDefined(scope.mapState) && isDefined(scope.mapState.zoom)) {
                        zoom = scope.mapState.zoom;
                    }

                    // Update the map
                    view.setCenter(MapService.fromLonLat(center));
                    view.setZoom(zoom);
                };
                scope.updateMapExtent(true);


                // Check for the map reload flag
                if (isDefined(scope.mapState) && isDefined(scope.mapState.reloadMap)) {
                    scope.$watch("mapState.reloadMap", function (reload) {
                        if (reload) {
                            scope.mapState['reloadMap'] = false;
                            scope.updateMapExtent(false);
                        }
                    }, true);
                }


                // Whenever the map extent is changed, record the new extent in the mapState
                if (isDefined(scope.mapState)) {
                    scope.mapChanged = function (evt) {
                        var extent = view.calculateExtent(map.getSize());
                        scope.mapState['zoom'] = view.getZoom();
                        scope.mapState['center'] = MapService.round(MapService.toLonLat(view.getCenter()), 4);
                        scope.mapState['extent'] = MapService.round(MapService.toLonLatExtent(extent), 4);
                        scope.$$phase || scope.$apply();
                    };
                    map.on('moveend', scope.mapChanged);
                }


                // Update the map size if the element size changes.
                // In theory, this should not be necessary, but it seems to fix a problem
                // where maps are sometimes distorted
                scope.updateSize = function () {
                    updateSizeTimer = null;
                    map.updateSize();
                };
                scope.$watchGroup([
                    function() { return element[0].clientWidth; },
                    function() { return element[0].clientHeight; }
                ], function () {
                    if (isDefined(updateSizeTimer)) {
                        $timeout.cancel(updateSizeTimer);
                    }
                    updateSizeTimer = $timeout(scope.updateSize, 100);
                });


                // Resolve the map object to the promises
                scope.setMap(map);
            }
        };
    }])


    /**
     * Used as a child-directive to ol-map and will convert the map to a cesium map.
     *
     * See: http://openlayers.org/ol3-cesium/
     */
    .directive('cesiumMap', [function () {
        return {
            restrict: 'E',
            replace: false,
            require: '^olMap',
            scope: {
            },
            link: function(scope, element, attrs, ctrl) {
                var olScope = ctrl.getOpenlayersScope();

                olScope.getMap().then(function(map) {
                    var ol3d = new olcs.OLCesium({map: map});

                    // Important: Will prevent CPU from maxing out because of high frame rate
                    ol3d.enableAutoRenderLoop();

                    ol3d.setEnabled(true);
                });

            }
        };
    }])


    /**
     * The map-layer-switcher directive will add a layer switcher to the map.
     *
     * This directive must be added after the last layer-directive for the map,
     * as the list of layer-switcher layers is only computed once.
     * A future improvement would be to compute the list of layers on-the-fly.
     */
    .directive('mapLayerSwitcher', [function () {
        return {
            restrict: 'E',
            replace: false,
            require: '^olMap',
            template:
                "<ul class='map-layer-switcher' ng-if='switcherLayers.length > 0'>"
                + "  <ul>"
                + "    <li ng-repeat='l in switcherLayers'>"
                + "      <input type='checkbox' ng-model='l.visible' ng-change='updateVisibility(l)'>&nbsp;{{l.name}}"
                + "    </li>"
                + "  </ul>"
                + "</div>",
            scope: {
            },
            link: function(scope, element, attrs, ctrl) {
                var olScope     = ctrl.getOpenlayersScope();

                scope.switcherLayers = [];

                olScope.getMap().then(function(map) {

                    // Update the list of layers to display in the layer switcher
                    scope.updateLayerSwitcher = function () {
                        scope.switcherLayers.length = 0;
                        angular.forEach(map.getLayers(), function (layer) {
                            if (layer.get('displayInLayerSwitcher')) {
                                scope.switcherLayers.push({
                                    layer : layer,
                                    name : layer.get('name'),
                                    visible : layer.getVisible()
                                });
                            }
                        });
                    };
                    scope.updateLayerSwitcher();

                    // Function called when displayInLayerSwitcher changes
                    scope.displayInLayerSwitcherChanged = function (evt) {
                        if (evt.key == 'displayInLayerSwitcher') {
                            scope.updateLayerSwitcher();
                        }
                    };

                    // Listen for 'displayInLayerSwitcher' change events
                    angular.forEach(map.getLayers(), function (layer) {
                        layer.on('propertychange', scope.displayInLayerSwitcherChanged);
                    });

                    // When destroyed, un-listen for 'displayInLayerSwitcher' change events
                    scope.$on('$destroy', function() {
                        angular.forEach(map.getLayers(), function (layer) {
                            layer.un('propertychange', scope.displayInLayerSwitcherChanged);
                        });
                    });

                });

                scope.updateVisibility = function (l) {
                    l.layer.setVisible(l.visible)
                };
            }
        };
    }])


    /**
     * Used as a child-directive to ol-map and display a static image in the map.
     *
     * See: http://openlayers.org/en/v3.6.0/examples/static-image.html
     */
    .directive('staticImageLayer', ['MapService', function (MapService) {
        return {
            restrict: 'E',
            replace: false,
            require: '^olMap',
            scope: {
                url:        '=',
                extent:     '='
            },
            link: function(scope, element, attrs, ctrl) {
                var olScope = ctrl.getOpenlayersScope();
                var olLayer;

                olScope.getMap().then(function(map) {

                    scope.$on('$destroy', function() {
                        if (angular.isDefined(olLayer)) {
                            map.removeLayer(olLayer);
                        }
                    });

                    olLayer = new ol.layer.Image({
                        source: new ol.source.ImageStatic({
                            url: scope.url,
                            imageExtent: MapService.fromLonLatExtent(scope.extent)
                        })
                    });

                    map.addLayer(olLayer);
                });

            }
        };
    }])


    /**
     * The map-tile-layer directive will add a simple tile layer to the map
     */
    .directive('mapTileLayer', ['$rootScope', 'MapService', function ($rootScope, MapService) {
        return {
            restrict: 'E',
            replace: false,
            require: '^olMap',
            scope: {
                name: '@',
                visible: '=',
                layerSwitcher: '=',
                source: '@',
                sourceProperties: '='
            },
            link: function(scope, element, attrs, ctrl) {
                var olScope = ctrl.getOpenlayersScope();
                var olLayer;

                olScope.getMap().then(function(map) {

                    scope.$on('$destroy', function() {
                        if (angular.isDefined(olLayer)) {
                            map.removeLayer(olLayer);
                        }
                    });


                    // Supports dynamically adding and removing the layer from the layer switcher
                    scope.$watch("layerSwitcher", function (layerSwitcher) {
                        olLayer.set('displayInLayerSwitcher', layerSwitcher);
                    }, true);


                    switch (scope.source) {
                        case 'MapQuest':
                            olLayer = new ol.layer.Tile({
                                source: new ol.source.MapQuest(scope.sourceProperties)
                            });
                            break;

                        case 'OSM':
                            olLayer = new ol.layer.Tile({
                                source: new ol.source.OSM()
                            });
                            if ($rootScope['osmSourceUrl'] && $rootScope['osmSourceUrl'].length > 0) {
                                olLayer.getSource().setUrl($rootScope['osmSourceUrl']);
                            }
                            break;

                        case 'WMS':
                            olLayer = new ol.layer.Tile({
                                source: new ol.source.TileWMS(scope.sourceProperties)
                            });
                            break;
                    }

                    // If the layer got created, add it
                    if (olLayer) {
                        olLayer = MapService.initLayer(olLayer, scope.name, scope.visible, scope.layerSwitcher);
                        map.addLayer(olLayer);
                    }

                });

            }
        };
    }])


    /**
     * The map-charts-layer directive will outline the given sea charts
     */
    .directive('mapChartsLayer', ['$rootScope', 'MapService', function ($rootScope, MapService) {
        return {
            restrict: 'E',
            replace: false,
            require: '^olMap',
            scope: {
                name: '@',
                visible: '=',
                layerSwitcher: '=',
                charts: '='
            },
            link: function(scope, element, attrs, ctrl) {
                var olScope = ctrl.getOpenlayersScope();
                var olLayer;
                var chartColor = 'rgba(255, 50, 0, 0.8)';

                olScope.getMap().then(function(map) {

                    scope.$on('$destroy', function() {
                        if (angular.isDefined(olLayer)) {
                            map.removeLayer(olLayer);
                        }
                    });

                    // Construct the layer
                    var features = new ol.Collection();
                    olLayer = new ol.layer.Vector({
                        source: new ol.source.Vector({
                            features: features,
                            wrapX: false
                        })
                    });
                    olLayer = MapService.initLayer(olLayer, scope.name, scope.visible, scope.layerSwitcher);
                    map.addLayer(olLayer);


                    // Supports dynamically adding and removing the layer from the layer switcher
                    scope.$watch("layerSwitcher", function (layerSwitcher) {
                        olLayer.set('displayInLayerSwitcher', layerSwitcher);
                    }, true);


                    // Creates a style with a label in the corner
                    scope.stylesForChart = function (feature, chart) {
                        var styles = [];

                        // Add extent outline style
                        styles.push(new ol.style.Style({
                            stroke: new ol.style.Stroke({
                                color: chartColor,
                                width: 2,
                                lineDash: [4,4]
                            })
                        }));

                        // Add the chart label style
                        var chartName = chart['chartNumber'];
                        if (chart['internationalNumber']) {
                            chartName += ' (INT ' + chart['internationalNumber'] + ')';
                        }
                        styles.push(new ol.style.Style({
                            text: new ol.style.Text({
                                font: '10px Arial',
                                text: chartName,
                                fill: new ol.style.Fill({color: chartColor}),
                                textAlign: 'left',
                                offsetX: 3,
                                offsetY: -7
                            }),
                            geometry: function(feature) {
                                // Place the label in the corner of the map extent
                                var extent = feature.getGeometry().getExtent();
                                return new ol.geom.Point([extent[0], extent[1]]);
                            }
                        }));
                        return styles;
                    };


                    // Updates the layer with the list of charts
                    scope.updateCharts = function () {
                        olLayer.getSource().clear();
                        if (scope.charts && scope.charts.length > 0) {
                            angular.forEach(scope.charts, function (chart) {

                                if (chart.geometry) {

                                    var geometry = MapService.gjToOlGeometry(chart.geometry);
                                    var feature = new ol.Feature();
                                    feature.setGeometry(geometry);
                                    feature.setStyle(scope.stylesForChart(feature, chart));
                                    olLayer.getSource().addFeature(feature);
                                }
                            });
                        }
                    };

                    scope.$watchCollection("charts", scope.updateCharts, true);

                });

            }
        };
    }])


    /**
     * The map-areas-layer directive will outline the given areas
     */
    .directive('mapAreasLayer', ['$rootScope', 'MapService', function ($rootScope, MapService) {
        return {
            restrict: 'E',
            replace: false,
            require: '^olMap',
            scope: {
                name: '@',
                visible: '=',
                layerSwitcher: '=',
                areas: '='
            },
            link: function(scope, element, attrs, ctrl) {
                var olScope = ctrl.getOpenlayersScope();
                var olLayer;
                var areaColor = 'rgba(100, 100, 255, 0.8)';

                olScope.getMap().then(function(map) {

                    scope.$on('$destroy', function() {
                        if (angular.isDefined(olLayer)) {
                            map.removeLayer(olLayer);
                        }
                    });

                    // Construct the layer
                    var features = new ol.Collection();
                    olLayer = new ol.layer.Vector({
                        source: new ol.source.Vector({
                            features: features,
                            wrapX: false
                        })
                    });
                    olLayer = MapService.initLayer(olLayer, scope.name, scope.visible, scope.layerSwitcher);
                    map.addLayer(olLayer);


                    // Supports dynamically adding and removing the layer from the layer switcher
                    scope.$watch("layerSwitcher", function (layerSwitcher) {
                        olLayer.set('displayInLayerSwitcher', layerSwitcher);
                    }, true);


                    // Creates a style with a label in the center
                    scope.stylesForArea = function (feature, area) {
                        var styles = [];

                        // Add extent outline style
                        styles.push(new ol.style.Style({
                            stroke: new ol.style.Stroke({
                                color: areaColor,
                                width: 2,
                                lineDash: [4,4]
                            })
                        }));

                        // Add the area label style
                        var areaName = area.descs[0].name;
                        styles.push(new ol.style.Style({
                            text: new ol.style.Text({
                                font: '10px Arial',
                                text: areaName,
                                fill: new ol.style.Fill({color: areaColor}),
                                textAlign: 'center'
                            }),
                            geometry: function(feature) {
                                // Place the label in the center of the map extent
                                var point = MapService.getGeometryCenter(feature.getGeometry());
                                return (point) ? new ol.geom.Point(point) : null;
                            }
                        }));
                        return styles;
                    };


                    // Updates the layer with the list of areas
                    scope.updateAreas = function () {
                        olLayer.getSource().clear();
                        if (scope.areas && scope.areas.length > 0) {
                            angular.forEach(scope.areas, function (area) {

                                if (area.geometry) {

                                    var geometry = MapService.gjToOlGeometry(area.geometry);
                                    var feature = new ol.Feature();
                                    feature.setGeometry(geometry);
                                    feature.setStyle(scope.stylesForArea(feature, area));
                                    olLayer.getSource().addFeature(feature);
                                }
                            });
                        }
                    };

                    scope.$watchCollection("areas", scope.updateAreas, true);

                });

            }
        };
    }])


    /**
     * The map-position-tooltip will display a position tooltip for the mouse position
     */
    .directive('mapPositionTooltip', ['$rootScope', 'MapService', function ($rootScope, MapService) {
        return {
            restrict: 'E',
            replace: false,
            template: '<div id="pos-tooltip"/>',
            require: '^olMap',
            scope: {
            },
            link: function(scope, element, attrs, ctrl) {
                var olScope = ctrl.getOpenlayersScope();

                olScope.getMap().then(function(map) {

                    // Prepare the tooltip
                    var posTooltip = $('#pos-tooltip');
                    posTooltip.tooltip({
                        animation: false,
                        trigger: 'manual',
                        placement : 'right'
                    });

                    // Show tooltip posTooltip
                    var updatePositionTooltip = function(pixel, lonlat) {

                        // Update the tooltip
                        posTooltip.css({
                            left: (pixel[0] + 15) + 'px',
                            top: pixel[1] + 'px'
                        });
                        posTooltip.tooltip('hide')
                            .attr('data-original-title', formatLatLon({ lon: lonlat[0], lat: lonlat[1]}))
                            .tooltip('fixTitle')
                            .tooltip('show');
                    };


                    // Update the tooltip whenever the mouse is moved
                    map.on('pointermove', function(evt) {
                        //if (evt.dragging) {
                        //    posTooltip.tooltip('hide');
                        //    return;
                        //}
                        updatePositionTooltip(map.getEventPixel(evt.originalEvent), MapService.toLonLat(evt.coordinate));
                    });

                    $(map.getViewport()).on('mouseout', function() {
                        posTooltip.tooltip('hide');
                    });
                });

            }
        };
    }]);
