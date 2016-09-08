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


    /********************************
     * Renders a badge with the message
     * ID and a colour that signals the
     * current status
     ********************************/
    .directive('messageIdBadge', ['LangService', function (LangService) {
        'use strict';

        return {
            restrict: 'E',
            template: '<span class="label label-message-id" ng-style="style">{{shortId}}</span>',
            scope: {
                msg:       "=",
                showBlank: "="
            },
            link: function(scope) {

                /** Assigns a color based on the status **/
                function statusColor(status) {
                    switch (status) {
                        case 'PUBLISHED':
                            return "#559955";
                        case 'IMPORTED':
                        case 'DRAFT':
                        case 'VERIFIED':
                            return "#5555CC";
                        case 'DELETED':
                            return "#333333";
                        case 'EXPIRED':
                        case 'CANCELLED':
                            return "#999999";
                        default:
                            return "#999999"
                    }

                }

                /** Updates the label based on the current status and short ID **/
                function updateIdLabel() {
                    var msg = scope.msg;
                    var status = msg && msg.status ? msg.status : 'DRAFT';
                    var color = statusColor(status);
                    scope.style = {};

                    scope.shortId = '';
                    if (msg && msg.shortId) {
                        scope.shortId = msg.shortId;
                        scope.style['background-color'] = color;
                    } else if (scope.showBlank) {
                        scope.shortId = msg.type ? LangService.translate('msg.type.' + msg.type) + ' ' : '';
                        scope.shortId = scope.shortId + (msg.mainType ? msg.mainType : '');
                        scope.style['background-color'] = 'white';
                        scope.style['color'] = color;
                        scope.style['border'] = '1px solid ' + color;
                    }
                }

                scope.$watch('[msg.shortId, msg.status, msg.mainType, msg.type]', updateIdLabel, true);
            }
        }
    }])

    /****************************************************************
     * Replaces the content of the element with the area description
     ****************************************************************/
    .directive('renderAreas', ['LangService', function (LangService) {
        return {
            restrict: 'A',
            scope: {
                renderAreas: "=",
                areaDivider: "@"
            },
            link: function(scope, element, attrs) {
                var divider = (attrs.areaDivider) ? attrs.areaDivider : " - ";

                /** Prepends the prefix to the result **/
                function prepend(prefix, result) {
                    return prefix
                        + ((result.length > 0 && prefix.length > 0) ? divider : '')
                        + result;
                }

                scope.updateAreas = function(areas) {
                    var result = '';
                    if (areas && areas.length > 0) {
                        for (var area = areas[0]; area; area = area.parent) {
                            if (area.id == -999999) {
                                // Special "General" area used for messages without an assigned area
                                result = prepend(LangService.translate('msg.area.general'), result);
                            } else {
                                var desc = LangService.desc(area);
                                var areaName = (desc) ? desc.name : '';
                                result = prepend(areaName, result);
                            }
                        }
                    }
                    element.html(result);
                };

                scope.$watchCollection("renderAreas", scope.updateAreas);
            }
        };
    }])


    /****************************************************************
     * Replaces the content of the element with the chart list
     ****************************************************************/
    .directive('renderCharts', [function () {
        return {
            restrict: 'A',
            scope: {
                renderCharts: "="
            },
            link: function(scope, element) {
                scope.updateCharts = function(charts) {
                    var result = '';
                    if (charts && charts.length > 0) {
                        for (var x = 0; x < charts.length; x++) {
                            var chart = charts[x];
                            if (x > 0) {
                                result += ', ';
                            }
                            result += chart.chartNumber;
                            if (chart.internationalNumber) {
                                result += ' (INT ' + chart.internationalNumber + ')';
                            }
                        }
                    }
                    element.html(result);
                };

                scope.$watchCollection("renderCharts", scope.updateCharts);
            }
        };
    }])


    /****************************************************************
     * Prints the message date interval
     ****************************************************************/
    .directive('renderMessageDates', ['$rootScope', 'DateIntervalService', function ($rootScope, DateIntervalService) {
        return {
            restrict: 'E',
            scope: {
                msg: "="
            },
            link: function(scope, element) {

                scope.updateTime = function () {
                    var lang = $rootScope.language;
                    var time = '';
                    var desc = scope.msg && scope.msg.descs ? scope.msg.descs[0] : null;
                    // First check for a textual time description
                    if (desc && desc.time) {
                        time = desc.time.replace(/\n/g, "<br/>");
                    } else if (scope.msg.dateIntervals && scope.msg.dateIntervals.length > 0) {
                        for (var x = 0; x < scope.msg.dateIntervals.length; x++) {
                            time += DateIntervalService.translateDateInterval(lang, scope.msg.dateIntervals[x]) + "<br/>";
                        }
                    }
                    element.html(time);
                };

                scope.$watch("msg", scope.updateTime);
            }
        };
    }])


    /****************************************************************
     * The message-attachment directive renders an attachment
     ****************************************************************/
    .directive('messageAttachment', ['MessageService', function (MessageService) {
        return {
            restrict: 'E',
            templateUrl: '/app/messages/message-attachment.html',
            replace: true,
            scope: {
                message : "=",
                attachment: "=",
                size: "@",
                imageType: "@",
                labelType: "@",
                attachmentClicked: '&'
            },
            link: function(scope, element, attrs) {

                scope.imageType = scope.imageType || 'thumbnail';
                scope.labelType = scope.labelType || 'file-name';

                var filePath = MessageService.attachmentRepoPath(scope.message, scope.attachment);
                scope.thumbnailUrl = "/rest/repo/thumb/" + filePath + "?size=" + scope.size;
                scope.fileUrl = "/rest/repo/file/" + filePath;


                scope.imageClass = "attachment-image";
                if (scope.imageType == "thumbnail") {
                    scope.imageClass += " size-" + scope.size;
                }
                if (scope.attachment.type && scope.attachment.type.toLowerCase().indexOf("image") == 0) {
                    scope.imageClass += " attachment-image-shadow";
                }
                
                scope.sourceStyle = {};
                if (scope.imageType == "source") {
                    scope.sourceType = (scope.attachment.type && scope.attachment.type.startsWith('video'))
                        ? "video"
                        : 'image';
                    if (scope.attachment.width && scope.attachment.height) {
                        scope.sourceStyle = { width: scope.attachment.width, height: scope.attachment.height };
                    } else if (scope.attachment.width) {
                        scope.sourceStyle = { width: scope.attachment.width };
                    } else if (scope.attachment.height) {
                        scope.sourceStyle = { height: scope.attachment.height };
                    }
                    scope.sourceStyle['max-width'] = '100%';
                }
                

                scope.handleClick = attrs.attachmentClicked !== undefined;
                scope.tooltip = scope.labelType != 'caption' && scope.attachment.descs && scope.attachment.descs.length > 0
                        ? scope.attachment.descs[0].caption
                        : '';

                scope.click = function() {
                    if (scope.handleClick) {
                        scope.attachmentClicked({ attachment: scope.attachment })
                    }
                }
            }
        };
    }])



    /********************************
     * Defines a message id field, e.g
     * used for references.
     ********************************/
    .directive('messageIdField', [ '$http', '$rootScope', function ($http, $rootScope) {
        'use strict';

        return {
            restrict: 'E',
            templateUrl: '/app/messages/message-id-field.html',
            replace: false,
            scope: {
                reference:  "=",
                minLength:  "="
            },
            link: function(scope) {

                scope.minLength = scope.minLength | 3;

                // Use for message id selection
                scope.messageIds = [];
                scope.refreshMessageIds = function (text) {
                    if (!text || text.length < scope.minLength) {
                        return [];
                    }
                    return $http.get(
                        '/rest/messages/search-message-ids?txt=' + encodeURIComponent(text) +
                        '&lang=' + $rootScope.language
                    ).then(function(response) {
                        scope.messageIds = response.data;
                    });
                };

            }
        }
    }])


    /****************************************************************
     * The message-tags-field directive supports selecting either a
     * single tag or a list of tags. For single-tag selection use
     * tagData.tag and for multi-tag selection use tagData.tags.
     * Use "init-ids" to initialize the tags using a list of tag ids.
     ****************************************************************/
    .directive('messageTagsField', ['$http', 'MessageService', function($http, MessageService) {
        return {
            restrict: 'E',
            replace: true,
            templateUrl: '/app/messages/message-tags-field.html',
            scope: {
                tagData:    "=",
                initIds:    "=",
                multiple:   "="
            },
            link: function(scope) {

                scope.tagData = scope.tagData || {};
                scope.multiple = scope.multiple || false;
                if (scope.multiple && !scope.tagData.tags) {
                    scope.tagData.tags = [];
                }


                // init-ids can be used to instantiate the field from a list of tags IDs
                scope.$watch("initIds", function (initIds) {
                    if (initIds && initIds.length > 0) {
                        $http.get('/rest/tags/tag/' + initIds.join()).then(function(response) {
                            angular.forEach(response.data, function (tag) {
                                if (scope.multiple) {
                                    scope.tagData.tags.push(tag);
                                } else {
                                    scope.tagData.tag = tag;
                                }
                            });
                        });
                    }
                }, true);


                /** Refreshes the tags search result */
                scope.searchResult = [];
                scope.refreshTags = function(name) {
                    if (!name || name.length == 0) {
                        return [];
                    }
                    return $http.get(
                        '/rest/tags/search?name=' + encodeURIComponent(name) + '&limit=10'
                    ).then(function(response) {
                        scope.searchResult = response.data;
                    });
                };


                /** Opens the tags dialog */
                scope.openTagsDialog = function () {
                    MessageService.messageTagsDialog().result
                        .then(function (tag) {
                            if (tag && scope.multiple) {
                                scope.tagData.tags.push(tag)
                            } else if (tag) {
                                scope.tagData.tag = tag;
                            }
                        });
                };


                /** Removes the current tag selection */
                scope.removeTag = function () {
                    if (scope.multiple) {
                        scope.tagData.tags.length = 0;
                    } else {
                        scope.tagData.tag = undefined;
                    }
                };
            }
        }
    }])


    /****************************************************************
     * The message-series-field directive supports selecting either a
     * single message series or a list of message series.
     * Use "init-ids" to initialize the message series using a list of
     * message series ids.
     ****************************************************************/
    .directive('messageSeriesField', ['$rootScope', '$http', function($rootScope, $http) {
        return {
            restrict: 'E',
            replace: true,
            templateUrl: '/app/messages/message-series-field.html',
            scope: {
                seriesData:     "=",
                initIds:        "=",
                domain:         "=",
                multiple:       "="
            },
            link: function(scope) {

                scope.seriesData = scope.seriesData || {};

                // Message series search parameters
                scope.multiple = scope.multiple || false;
                scope.domain = scope.domain || false;

                if (scope.multiple && !scope.seriesData.messageSeries) {
                    scope.seriesData.messageSeries = [];
                }

                // init-ids can be used to instantiate the field from a list of message series IDs
                scope.$watch("initIds", function (initIds) {
                    if (initIds && initIds.length > 0) {
                        $http.get('/rest/message-series/search/' + initIds.join() + '?lang=' + $rootScope.language + '&limit=20')
                            .then(function(response) {
                                angular.forEach(response.data, function (series) {
                                    if (scope.multiple) {
                                        scope.seriesData.messageSeries.push(series);
                                    } else {
                                        scope.seriesData.messageSeries = series;
                                    }
                                });
                            });
                    }
                }, true);


                /** Refreshes the message series search result */
                scope.searchResult = [];
                scope.refreshMessageSeries = function(name) {
                    if (!name || name.length == 0) {
                        return [];
                    }
                    return $http.get(
                        '/rest/message-series/search?name=' + encodeURIComponent(name) +
                        '&domain=' + scope.domain +
                        '&lang=' + $rootScope.language +
                        '&limit=10'
                    ).then(function(response) {
                        scope.searchResult = response.data;
                    });
                };


                /** Removes the current message series selection */
                scope.removeMessageSeries = function () {
                    if (scope.multiple) {
                        scope.seriesData.messageSeries.length = 0;
                    } else {
                        scope.seriesData.messageSeries = undefined;
                    }
                };
            }
        }
    }])


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
                                if (message.geometry && message.geometry.features.length > 0) {

                                    angular.forEach(message.geometry.features, function (gjFeature) {
                                        var olFeature = MapService.gjToOlFeature(gjFeature);
                                        olFeature.set('message', message);
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
                        }
                    });


                });

            }
        };
    }])


    /****************************************************************
     * The map-message-details directive supports drawing a single message on a map layer
     ****************************************************************/
    .directive('mapMessageDetailsLayer', ['$rootScope', 'MapService', function ($rootScope, MapService) {
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
                                if (scope.message) {
                                    featureStyle = scope.message.mainType == 'NW' ? nwStyle : nmStyle;
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
                        var featureCollection = undefined;
                        if (scope.featureCollection) {
                            featureCollection = scope.featureCollection;
                        } else if (scope.message) {
                            featureCollection = scope.message.geometry;
                        }

                        if (featureCollection.features.length > 0) {

                            angular.forEach(featureCollection.features, function (gjFeature) {
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
     * Binds a click event that will open the message details dialog
     ****************************************************************/
    .directive('messageDetailsLink', ['MessageService',
        function (MessageService) {
            'use strict';

            return {
                restrict: 'A',
                scope: {
                    messageDetailsLink: "=",
                    messageList: "=",
                    selection: "=",
                    disabled: "=?"
                },
                link: function(scope, element) {

                    if (!scope.disabled) {
                        element.addClass('clickable');
                        element.bind('click', function() {
                            MessageService.detailsDialog(scope.messageDetailsLink, scope.messageList, scope.selection);
                        });
                    }
                }
            };
        }])


    /****************************************************************
     * Binds a click event that will open the message details dialog
     ****************************************************************/
    .directive('loadMoreMessages', [
        function () {
            'use strict';

            return {
                restrict: 'A',
                templateUrl: '/app/messages/load-more-messages.html',
                replace: false,
                scope: {
                    loadMoreMessages:    "=",
                    totalMessageNo: "=",
                    maxSize:        "=",
                    loadMore:       "&",
                    class:          "@"
                },
                link: function(scope) {

                    scope.messageList = scope.loadMoreMessages;

                    scope.fromMessageNo = function () {
                        return numeral(scope.messageList.length).format("0,0");
                    };

                    scope.toMessageNo = function () {
                        return numeral(Math.min(scope.messageList.length + scope.maxSize, scope.totalMessageNo)).format("0,0");
                    };

                    scope.total = function () {
                        return numeral(scope.totalMessageNo).format("0,0");
                    };

                    /** Called to load next batch of messages **/
                    scope.loadMoreMessages = function () {
                        scope.loadMore();
                    }
                }
            };
        }])


    /********************************
     * Renders the message details
     ********************************/
    .directive('renderMessageDetails', [ '$rootScope', 'MapService', 'MessageService',
        function ($rootScope, MapService, MessageService) {
        'use strict';

        return {
            restrict: 'A',
            templateUrl: '/app/messages/render-message-details.html',
            replace: false,
            scope: {
                msg:            "=",
                messageList:    "=",
                selection:      "=",
                format:         "@",
                showDetailsMenu:"@",
                showDetails:    "&",
                compact:        "="
            },
            link: function(scope, element, attrs) {
                scope.language = $rootScope.language;
                scope.format = scope.format || 'list';
                scope.featureCoordinates = [];
                scope.attachmentsAbove = [];
                scope.attachmentsBelow = [];
                scope.showLocations = scope.compact || false;
                scope.showAttachments = scope.compact || false;

                // Serializes the coordinates and caches the results
                scope.serializeCoordinates = function () {
                    // Compute on-demand
                    if (scope.featureCoordinates.length == 0 && scope.msg.geometry && scope.msg.geometry.features) {
                        var index = 1;
                        angular.forEach(scope.msg.geometry.features, function (feature) {
                            var coords = [];
                            MapService.serializeCoordinates(feature, coords);
                            if (coords.length > 0) {
                                var name = feature.properties ? feature.properties['name:' + $rootScope.language] : undefined;
                                scope.featureCoordinates.push({
                                    coords: coords,
                                    startIndex : index,
                                    name: name
                                });
                                index += coords.length;
                            }
                        });
                    }
                    return scope.featureCoordinates;
                };


                // Returns if the given message is selected or not
                scope.isSelected = function () {
                    return scope.selection.get(scope.msg.id) !== undefined;
                };


                // Toggle the selection state of the message
                scope.toggleSelectMessage = function () {
                    if (scope.isSelected()) {
                        scope.selection.remove(scope.msg.id);
                    } else if (scope.msg) {
                        scope.selection.put(scope.msg.id, angular.copy(scope.msg));
                    }
                };


                /** Called when a message reference is clicked **/
                scope.referenceClicked = function(messageId) {
                    if (attrs.showDetails) {
                        scope.showDetails({messageId: messageId});
                    } else {
                        MessageService.detailsDialog(messageId, scope.messageList);
                    }
                };


                /** Called whenever the message changes **/
                scope.initMessage = function () {
                    scope.featureCoordinates.length = 0;
                    scope.attachmentsAbove.length = 0;
                    scope.attachmentsBelow.length = 0;

                    // Extract the attachments that will displayed above and below the message data
                    if (scope.msg.attachments) {
                        scope.attachmentsAbove = $.grep(scope.msg.attachments, function (att) {
                            return att.display == 'ABOVE';
                        });
                        scope.attachmentsBelow = $.grep(scope.msg.attachments, function (att) {
                            return att.display == 'BELOW';
                        });
                    }
                };


                // Sets whether to show the locations or not
                scope.setShowLocations = function (value) {
                    scope.showLocations = value;
                };


                // Sets whether to show the attachments or not
                scope.setShowAttachments = function (value) {
                    scope.showAttachments = value;
                };

                scope.$watch("msg", scope.initMessage);
            }
        };
    }])


    /****************************************************************
     * Adds a message details drop-down menu
     ****************************************************************/
    .directive('messageDetailsMenu', ['$rootScope', '$window', '$state', 'growl', 'MessageService', 'DialogService',
        function ($rootScope, $window, $state, growl, MessageService, DialogService) {
            'use strict';

            return {
                restrict: 'E',
                templateUrl: '/app/messages/message-details-menu.html',
                scope: {
                    messageId:      "=",     // NB: We supply both of "messageId" and "msg" because
                    msg:            "=",     // the former may be invalid and the latter may be undefined.
                    messages:       "=",
                    style:          "@",
                    size:           "@",
                    dismissAction:  "&"
                },
                link: function(scope, element) {

                    if (scope.style) {
                        element.attr('style', scope.style);
                    }

                    if (scope.size) {
                        $(element[0]).find('button').addClass("btn-" + scope.size);
                    }

                    scope.hasRole = $rootScope.hasRole;
                    scope.isLoggedIn = $rootScope.isLoggedIn;
                    scope.messageTags = [];


                    /** Called when the dropdown is about to be displayed **/
                    scope.menuClicked = function() {
                        if (scope.isLoggedIn) {
                            var tag = MessageService.getLastMessageTagSelection();
                            scope.lastSelectedMessageTag = (tag) ? tag.name : undefined;

                            MessageService.tagsForMessage(scope.messageId)
                                .success(function (messageTags) {
                                    scope.messageTags = messageTags;

                                    // If the current message is already associated with the last selected tag,
                                    // Do not provide a link to add it again.
                                    for (var x = 0; x < messageTags.length; x++) {
                                        if (tag && tag.tagId == messageTags[x].tagId) {
                                            scope.lastSelectedMessageTag = undefined;
                                        }
                                    }
                                })
                        }
                    };


                    /** Adds the current message to the given tag **/
                    scope.addToTag = function (tag) {
                        if (tag) {
                            MessageService.addMessagesToTag(tag, [ scope.messageId ])
                                .success(function () {
                                    growl.info("Added message to " + tag.name, { ttl: 3000 })
                                })
                        }
                    };


                    /** Adds the current message to the tag selected via the Message Tag dialog */
                    scope.addToTagDialog = function () {
                        MessageService.messageTagsDialog().result
                            .then(scope.addToTag);
                    };


                    /** Adds the current message to the last selected tag */
                    scope.addToLastSelectedTag = function () {
                        scope.addToTag(MessageService.getLastMessageTagSelection());
                    };


                    /** Removes the current message from the given tag */
                    scope.removeFromTag = function (tag) {
                        MessageService.removeMessagesFromTag(tag, [ scope.messageId ])
                            .success(function () {
                                growl.info("Removed message from " + tag.name, { ttl: 3000 })
                            })
                    };

                    
                    /** Opens the message print dialog */
                    scope.pdf = function () {
                        MessageService.printMessage(scope.messageId);
                    };


                    /** Returns if the user can edit the current message */
                    scope.canEdit = function () {
                        return scope.hasRole('editor') &&
                                scope.msg && scope.msg.messageSeries &&
                                $rootScope.domain && $rootScope.domain.messageSeries &&
                                $.grep($rootScope.domain.messageSeries, function (ms) {
                                    return ms.seriesId == scope.msg.messageSeries.seriesId;
                                }).length > 0;
                    };

                    
                    /** Navigate to the message editor page **/
                    scope.edit = function() {
                        if (scope.dismissAction) {
                            scope.dismissAction();
                        }
                    };

                    /** Copies the message **/
                    scope.copy = function () {
                        DialogService.showConfirmDialog(
                            "Copy Message?", "Copy Message?")
                            .then(function() {
                                // Navigate to the message editor page
                                $state.go(
                                    'editor.copy',
                                    { id: scope.messageId,  referenceType : 'REFERENCE' },
                                    { reload: true }
                                );
                                if (scope.dismissAction) {
                                    scope.dismissAction();
                                }
                            });
                    };
                }
            }
        }]);

