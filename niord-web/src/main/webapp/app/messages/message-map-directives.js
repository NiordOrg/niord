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
 * Message list directives.
 */
angular.module('niord.messages')


    /****************************************************************
     * The map-message-list-layer directive supports drawing a list of messages on a map layer
     ****************************************************************/
    .directive('mapMessageListLayer', ['$rootScope', 'MapService', 'LangService', 'MessageService',
        function ($rootScope, MapService, LangService, MessageService) {
        return {
            restrict: 'E',
            replace: false,
            templateUrl: '/app/messages/map-message-list-layer.html',
            require: '^olMap',
            scope: {
                name: '@',
                visible: '=',
                layerSwitcher: '=',
                messageList: '=',
                selection: '=',
                showOutline: '@',
                showGeneral: '@',
                fitExtent: '@',
                maxZoom: '@'
            },
            link: function(scope, element, attrs, ctrl) {
                var olScope = ctrl.getOpenlayersScope();
                var olLayer;
                var maxZoom = scope.maxZoom ? parseInt(scope.maxZoom) : 10;

                scope.generalMessages = []; // Messages with no geometry
                scope.language = $rootScope.language;

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

                    var outlineStyle = new ol.style.Style({
                        fill: new ol.style.Fill({ color: 'rgba(255, 0, 255, 0.2)' }),
                        stroke: new ol.style.Stroke({ color: '#8B008B', width: 1 }),
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

                    var nwStyle = new ol.style.Style({
                        fill: new ol.style.Fill({ color: 'rgba(255, 0, 255, 0.2)' }),
                        stroke: new ol.style.Stroke({ color: '#8B008B', width: 1 }),
                        image: new ol.style.Icon({
                            anchor: [0.5, 0.5],
                            scale: 0.3,
                            src: '/img/nw.png'
                        })
                    });

                    var nmStyle = new ol.style.Style({
                        fill: new ol.style.Fill({ color: 'rgba(255, 0, 255, 0.2)' }),
                        stroke: new ol.style.Stroke({ color: '#8B008B', width: 1 }),
                        image: new ol.style.Icon({
                            anchor: [0.5, 0.5],
                            scale: 0.3,
                            src: '/img/nm.png'
                        })
                    });

                    var bufferedStyle = new ol.style.Style({
                        fill: new ol.style.Fill({
                            color: 'rgba(100, 50, 100, 0.2)'
                        }),
                        stroke: new ol.style.Stroke({
                            color: 'rgba(100, 50, 100, 0.6)',
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
                            var featureStyle;
                            if (scope.showOutline == 'true') {
                                featureStyle = outlineStyle;
                            } else if (feature.get('parentFeatureIds')) {
                                featureStyle = bufferedStyle;
                            } else {
                                var message = feature.get('message');
                                featureStyle = message.mainType == 'NW' ? nwStyle : nmStyle;
                            }
                            return [ featureStyle ];
                        }
                    });
                    olLayer = MapService.initLayer(olLayer, scope.name, scope.visible, scope.layerSwitcher);
                    map.addLayer(olLayer);


                    /***************************/
                    /** Message List Handling **/
                    /***************************/

                    scope.updateLayerFromMessageList = function () {
                        olLayer.getSource().clear();
                        scope.generalMessages.length = 0;
                        if (scope.messageList && scope.messageList.length > 0) {
                            angular.forEach(scope.messageList, function (message) {
                                var features = MessageService.getMessageFeatures(message);
                                if (features.length > 0) {
                                    angular.forEach(features, function (gjFeature) {
                                        var olFeature = MapService.gjToOlFeature(gjFeature);
                                        olFeature.set('message', {
                                            id : message.id,
                                            mainType: message.mainType,
                                            shortId : message.shortId,
                                            descs : message.descs
                                        });
                                        if (scope.showOutline == 'true') {
                                            var point = MapService.getGeometryCenter(olFeature.getGeometry());
                                            if (point) {
                                                olFeature.setGeometry(new ol.geom.Point(point));
                                            }
                                        }
                                        olLayer.getSource().addFeature(olFeature);
                                    });
                                } else if (scope.showGeneral == 'true') {
                                    scope.generalMessages.push(message);
                                }
                            });
                            if (scope.fitExtent == 'true') {
                                map.getView().fit(olLayer.getSource().getExtent(), map.getSize(), {
                                    padding: [5, 5, 5, 5],
                                    maxZoom: maxZoom
                                });
                            }
                        }
                    };

                    scope.$watchCollection("messageList", scope.updateLayerFromMessageList, true);


                    /***************************/
                    /** Tooltips              **/
                    /***************************/


                    // Returns the list of messages for the given pixel
                    scope.getMessagesForPixel = function (pixel) {
                        var messageIds = {};
                        var messages = [];
                        map.forEachFeatureAtPixel(pixel, function(feature, layer) {
                            var msg = feature.get('message');
                            if (layer  == olLayer && msg && messageIds[msg.id] === undefined) {
                                messages.push(msg);
                                messageIds[msg.id] = msg.id;
                            }
                        });
                        return messages;
                    };


                    // Prepare the tooltip
                    var info = $('#msg-info');
                    info.tooltip({
                        animation: false,
                        trigger: 'manual',
                        html: true,
                        placement: 'bottom'
                    });


                    // Composes the tooltip for a specific message
                    function composeTooltip(message) {
                        var html = '';

                        // Get the message description
                        if (message.shortId) {
                            html += '<div>' + message.shortId + '</div>'
                        }
                        var desc = LangService.desc(message);
                        if (desc && desc.title) {
                            html += '<div><small>' + desc.title + '</small></div>'
                        }

                        if (html.length > 0) {
                            html = '<div class="message-tooltip">' + html + '</div>';
                        }

                        return html;
                    }


                    // Show tooltip info
                    var updateMsgTooltip = function(pixel) {
                        var messages = scope.getMessagesForPixel(pixel);

                        // Build the html to display in the tooltip
                        var html = '';
                        for (var m = 0; m < messages.length && m < 3; m++) {
                            html += composeTooltip(messages[m]);
                        }


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

                    /***************************/
                    /** Details dialog        **/
                    /***************************/


                    // Open the message details dialog
                    scope.showMessageInfo = function (message, messages) {
                        MessageService.detailsDialog(message.id, messages, scope.selection);
                    };


                    // Show AtoN info dialog when an AtoN is clicked
                    map.on('click', function(evt) {
                        var messages = scope.getMessagesForPixel(map.getEventPixel(evt.originalEvent));
                        if (messages.length >= 1) {
                            info.tooltip('hide');
                            scope.showMessageInfo(messages[0], messages);
                            scope.$$phase || scope.$apply();
                        }
                    });


                });

            }
        };
    }])


    /****************************************************************
     * The map-message-details directive supports drawing a single message on a map layer
     ****************************************************************/
    .directive('mapMessageDetailsLayer', ['$rootScope', 'MapService', 'MessageService',
        function ($rootScope, MapService, MessageService) {
        return {
            restrict: 'E',
            replace: false,
            template: '<div id="msg-info"/>',
            require: '^olMap',
            scope: {
                name:               '@',
                visible:            '=',
                layerSwitcher:      '=',
                message:            '=?',
                featureCollection:  '=?',
                showOutline:        '@',
                fitExtent:          '@',
                maxZoom:            '@'
            },
            link: function(scope, element, attrs, ctrl) {
                var olScope = ctrl.getOpenlayersScope();
                var olLayer;
                var maxZoom = scope.maxZoom ? parseInt(scope.maxZoom) : 12;


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

                    var outlineStyle = new ol.style.Style({
                        fill: new ol.style.Fill({ color: 'rgba(255, 0, 255, 0.2)' }),
                        stroke: new ol.style.Stroke({ color: '#8B008B', width: 1 }),
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

                    var messageStyle = new ol.style.Style({
                        fill: new ol.style.Fill({ color: 'rgba(255, 0, 255, 0.2)' }),
                        stroke: new ol.style.Stroke({ color: '#8B008B', width: 1 }),
                        image: new ol.style.Circle({
                            radius: 4,
                            fill: new ol.style.Fill({
                                color: 'rgba(255, 0, 255, 0.2)'
                            }),
                            stroke: new ol.style.Stroke({color: 'darkmagenta', width: 1})
                        })
                    });

                    var bufferedStyle = new ol.style.Style({
                        fill: new ol.style.Fill({
                            color: 'rgba(100, 50, 100, 0.2)'
                        }),
                        stroke: new ol.style.Stroke({
                            color: 'rgba(100, 50, 100, 0.6)',
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
                            var featureStyle;
                            if (scope.showOutline == 'true') {
                                featureStyle = outlineStyle;
                            } else if (feature.get('parentFeatureIds')) {
                                featureStyle = bufferedStyle;
                            } else {
                                if (scope.message) {
                                    featureStyle = messageStyle;
                                } else {
                                    featureStyle = outlineStyle;
                                }
                            }
                            return [ featureStyle ];
                        }
                    });
                    olLayer = MapService.initLayer(olLayer, scope.name, scope.visible, scope.layerSwitcher);
                    map.addLayer(olLayer);


                    /***************************/
                    /** Message List Handling **/
                    /***************************/

                    scope.updateLayerFromMessage = function () {
                        olLayer.getSource().clear();

                        // Directive either initialized with "message" or "featureCollection"
                        var features = undefined;
                        if (scope.featureCollection) {
                            features = scope.featureCollection.features;
                        } else if (scope.message) {
                            features = MessageService.getMessageFeatures(scope.message);
                        }

                        if (features && features.length > 0) {

                            angular.forEach(features, function (gjFeature) {
                                var olFeature = MapService.gjToOlFeature(gjFeature);
                                if (scope.showOutline == 'true') {
                                    var point = MapService.getGeometryCenter(olFeature.getGeometry());
                                    if (point) {
                                        olFeature.setGeometry(new ol.geom.Point(point));
                                    }
                                }
                                olLayer.getSource().addFeature(olFeature);
                            });
                        }

                        if (scope.fitExtent == 'true' && olLayer.getSource().getFeatures().length > 0) {
                            map.getView().fit(olLayer.getSource().getExtent(), map.getSize(), {
                                padding: [20, 20, 20, 20],
                                maxZoom: maxZoom
                            });
                        }
                    };

                    if (scope.message) {
                        scope.$watch("message", scope.updateLayerFromMessage, true);
                    } else if (scope.featureCollection) {
                        scope.$watch("featureCollection", scope.updateLayerFromMessage, true);
                    }


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
    }])


    /****************************************************************
     * The map-message-labels-layer directive supports drawing geometry
     * labels on a map layer
     ****************************************************************/
    .directive('mapMessageLabelsLayer', ['$rootScope', 'MapService', 'MessageService',
        function ($rootScope, MapService, MessageService) {
        return {
            restrict: 'E',
            replace: false,
            require: '^olMap',
            scope: {
                name:               '@',
                visible:            '=',
                layerSwitcher:      '=',
                message:            '=?',
                featureCollection:  '=?'
            },
            link: function(scope, element, attrs, ctrl) {
                var olScope = ctrl.getOpenlayersScope();
                var olLayer;

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


                    /** Creates a features style that displays the index of the coordinate **/
                    scope.styleForFeatureCoordIndex = function (feature, index, coord) {
                        return new ol.style.Style({
                            text: new ol.style.Text({
                                textAlign: 'center',
                                font: '9px Arial',
                                text: '' + index,
                                fill: new ol.style.Fill({color: 'white'}),
                                offsetX: 0,
                                offsetY: 0
                            }),
                            image: new ol.style.Circle({
                                radius: 8,
                                fill: new ol.style.Fill({
                                    color: 'darkmagenta'
                                }),
                                stroke: new ol.style.Stroke({color: 'white', width: 2.0})
                            }),
                            geometry: function() {
                                return new ol.geom.Point(coord);
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
                                offsetY: 14
                            }),
                            geometry: function() {
                                return new ol.geom.Point(coord);
                            }
                        });
                    };

                    /***************************/
                    /** Message List Handling **/
                    /***************************/

                    scope.updateLayerFromMessage = function () {
                        olLayer.getSource().clear();

                        // Directive either initialized with "message" or "featureCollection"
                        var features = undefined;
                        if (scope.featureCollection) {
                            features = scope.featureCollection.features;
                        } else if (scope.message) {
                            features = MessageService.getMessageFeatures(scope.message);
                        }

                        if (features && features.length > 0) {

                            var coordIndex = 1;
                            angular.forEach(features, function (gjFeature) {

                                var olFeature = MapService.gjToOlFeature(gjFeature);
                                var styles = [];

                                // Create a label for the feature
                                var name = gjFeature.properties
                                    ? gjFeature.properties['name:' + $rootScope.language]
                                    : undefined;

                                if (name) {
                                    styles.push(scope.styleForFeatureName(
                                        olFeature,
                                        name));
                                }

                                // Create labels for the "readable" coordinates
                                var coords = [];
                                MapService.serializeReadableCoordinates(gjFeature, coords);
                                for (var x = 0; x < coords.length; x++) {

                                    var c = MapService.fromLonLat([ coords[x].lon, coords[x].lat ]);

                                    styles.push(scope.styleForFeatureCoordIndex(
                                        olFeature,
                                        coordIndex,
                                        c));

                                    if (coords[x].name) {
                                        styles.push(scope.styleForFeatureCoordName(
                                            olFeature,
                                            coords[x].name,
                                            c));
                                    }
                                    coordIndex++;
                                }

                                if (styles.length > 0) {
                                    olFeature.setStyle(styles);
                                    olLayer.getSource().addFeature(olFeature);
                                }
                            });
                        }
                    };

                    if (scope.message) {
                        scope.$watch("message", scope.updateLayerFromMessage, true);
                    } else if (scope.featureCollection) {
                        scope.$watch("featureCollection", scope.updateLayerFromMessage, true);
                    }

                });

            }
        };
    }])
;

