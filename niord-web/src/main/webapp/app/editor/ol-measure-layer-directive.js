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
 * Measure layer directive.
 * <p>
 * Inspiration: https://openlayers.org/en/latest/examples/measure.html
 */
angular.module('niord.editor')

    /**
     * The map-ol-measure-layer directive supports measuring distances along a path
     */
    .directive('mapOlMeasureLayer', ['$rootScope', 'MapService', function ($rootScope, MapService) {
        return {
            restrict: 'E',
            replace: false,
            template: '<div id="measure-info"/>',
            require: '^olMap',
            scope: {
                name:           '@',
                drawControl:    '='
            },
            link: function(scope, element, attrs, ctrl) {
                var olScope = ctrl.getOpenlayersScope();
                var wgs84Sphere = new ol.Sphere(6378137);
                var olLayer;
                var draw;       // Draw interaction
                var sketch;     // Currently drawn feature
                var tooltipOverlays = [];


                olScope.getMap().then(function(map) {

                    // Clean up when the layer is destroyed
                    scope.$on('$destroy', function() {
                        if (angular.isDefined(olLayer)) {
                            map.removeLayer(olLayer);
                        }
                        if (angular.isDefined(draw)) {
                            map.removeInteraction(draw);
                        }
                        angular.forEach(tooltipOverlays, function (overlay) {
                            map.removeOverlay(overlay);
                        })
                    });


                    /**
                     * Format length output.
                     * @param {ol.geom.LineString} line The line.
                     * @return {string} The formatted length.
                     */
                    var formatLength = function(line) {
                        var coordinates = line.getCoordinates();
                        var length = 0;
                        for (var i = 0, ii = coordinates.length - 1; i < ii; ++i) {
                            var c1 = ol.proj.transform(coordinates[i], MapService.featureProjection(), MapService.dataProjection());
                            var c2 = ol.proj.transform(coordinates[i + 1], MapService.featureProjection(), MapService.dataProjection());
                            length += wgs84Sphere.haversineDistance(c1, c2);
                        }
                        var output;
                        output = numeral(length).format('0,0')  + ' m<br>' +
                            numeral(length / 1000.0).format('0.00') +  ' km<br>' +
                            numeral(length / 1852.0).format('0.00') +  ' nm';
                        return output;
                    };


                    /** ************************ **/
                    /** Draw interaction         **/
                    /** ************************ **/

                    var source = new ol.source.Vector();

                    draw = new ol.interaction.Draw({
                        source: source,
                        type: /** @type {ol.geom.GeometryType} */ ('LineString'),
                        style: new ol.style.Style({
                            fill: new ol.style.Fill({
                                color: 'rgba(255, 255, 255, 0.2)'
                            }),
                            stroke: new ol.style.Stroke({
                                color: 'rgba(0, 0, 0, 0.5)',
                                lineDash: [10, 10],
                                width: 2
                            }),
                            image: new ol.style.Circle({
                                radius: 5,
                                stroke: new ol.style.Stroke({
                                    color: 'rgba(0, 0, 0, 0.7)'
                                }),
                                fill: new ol.style.Fill({
                                    color: 'rgba(255, 255, 255, 0.2)'
                                })
                            })
                        })
                    });
                    draw.setActive(false);
                    map.addInteraction(draw);


                    /** ************************ **/
                    /** Layer                    **/
                    /** ************************ **/


                    // Construct the layer
                    olLayer = new ol.layer.Vector({
                        source: source,
                        style: new ol.style.Style({
                            fill: new ol.style.Fill({
                                color: 'rgba(255, 255, 255, 0.2)'
                            }),
                            stroke: new ol.style.Stroke({
                                color: '#ffcc33',
                                width: 2
                            }),
                            image: new ol.style.Circle({
                                radius: 7,
                                fill: new ol.style.Fill({
                                    color: '#ffcc33'
                                })
                            })
                        })
                    });

                    olLayer = MapService.initLayer(olLayer, scope.name, false, false);
                    map.addLayer(olLayer);


                    /***************************/
                    /** Tooltips              **/
                    /***************************/

                    /**
                     * The measure tooltip element.
                     * @type {Element}
                     */
                    var measureTooltipElement;


                    /**
                     * Overlay to show the measurement.
                     * @type {ol.Overlay}
                     */
                    var measureTooltip;


                    /**
                     * Cleans up the layer
                     */
                    function clearLayer() {
                        olLayer.getSource().clear();
                        angular.forEach(tooltipOverlays, function (overlay) {
                            map.removeOverlay(overlay);
                        });
                        tooltipOverlays.length = 0;
                    }


                    /**
                     * Creates a new measure tooltip
                     */
                    function createMeasureTooltip() {
                        if (measureTooltipElement) {
                            measureTooltipElement.parentNode.removeChild(measureTooltipElement);
                        }
                        measureTooltipElement = document.createElement('div');
                        measureTooltipElement.className = 'measure-tooltip measure-tooltip-measure';
                        measureTooltip = new ol.Overlay({
                            element: measureTooltipElement,
                            offset: [0, -15],
                            positioning: 'bottom-center'
                        });
                        map.addOverlay(measureTooltip);
                        tooltipOverlays.push(measureTooltip);
                    }


                    var listener;
                    draw.on('drawstart',
                        function(evt) {
                            // set sketch
                            sketch = evt.feature;

                            /** @type {ol.Coordinate|undefined} */
                            var tooltipCoord = evt.coordinate;

                            listener = sketch.getGeometry().on('change', function(evt) {
                                var geom = evt.target;
                                tooltipCoord = geom.getLastCoordinate();
                                measureTooltipElement.innerHTML = formatLength(geom);
                                measureTooltip.setPosition(tooltipCoord);
                            });
                        }, this);


                    draw.on('drawend',
                        function() {
                            measureTooltipElement.className = 'measure-tooltip measure-tooltip-static';
                            measureTooltip.setOffset([0, -7]);
                            // unset sketch
                            sketch = null;
                            // unset tooltip so that a new one can be created
                            measureTooltipElement = null;
                            createMeasureTooltip();
                            ol.Observable.unByKey(listener);
                        }, this);


                    // Check if the 'measure' draw-control is selected
                    scope.$watch("drawControl", function (drawControl) {
                        var measureSelected = drawControl === 'measure';
                        olLayer.setVisible(measureSelected);
                        draw.setActive(measureSelected);
                        clearLayer();
                        if (measureSelected) {
                            createMeasureTooltip();
                        }
                    }, true);

                });

            }
        };
    }]);

