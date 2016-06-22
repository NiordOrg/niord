/**
 * Message list directives.
 */
angular.module('niord.messages')

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

                scope.updateAreas = function(areas) {
                    var result = '';
                    if (areas && areas.length > 0) {
                        for (var area = areas[0]; area; area = area.parent) {
                            var desc = LangService.desc(area);
                            var areaName = (desc) ? desc.name : '';
                            result = areaName + ((result.length > 0 && areaName.length > 0) ? divider : '') + result;
                        }
                    }
                    element.html(result);
                };

                scope.$watchCollection("renderAreas", scope.updateAreas);
            }
        };
    }])


    /****************************************************************
     * Prints the message date interval
     ****************************************************************/
    .directive('renderMessageDates', ['$rootScope', function ($rootScope) {
        return {
            restrict: 'E',
            scope: {
                msg: "="
            },
            link: function(scope, element) {
                // First check for a textual time description
                var time = '';
                var desc = scope.msg.descs[0];
                if (desc && desc.time) {
                    time = desc.time;
                } else if (scope.msg.dateIntervals) {
                    var lang = $rootScope.language;
                    for (var x = 0; x < scope.msg.dateIntervals.length; x++) {
                        var from = moment(scope.msg.dateIntervals[x].fromDate);
                        time += from.locale(lang).format("lll");
                        if (scope.msg.dateIntervals[x].toDate) {
                            var to = moment(scope.msg.dateIntervals[x].toDate);
                            var fromDate = from.locale(lang).format("ll");
                            var toDate = to.locale(lang).format("ll");
                            var toDateTime = to.locale(lang).format("lll");
                            if (fromDate == toDate) {
                                // Same dates
                                time += " - " + toDateTime.replace(toDate, '');
                            } else {
                                time += " - " + toDateTime;
                            }
                        }
                        time += ' ' + from.format('z');
                        time += '<br/>';
                    }
                }
                element.html(time);
            }
        };
    }])


    /****************************************************************
     * The message-attachment directive renders an attachment
     ****************************************************************/
    .directive('messageAttachment', [function () {
        return {
            restrict: 'E',
            templateUrl: '/app/messages/message-attachment.html',
            replace: true,
            scope: {
                message : "=",
                attachment: "=",
                size: "@",
                clickable: "@",
                deleteAttachment: '&deleteAttachment'
            },
            link: function(scope, element, attrs) {

                var params;
                if (scope.message.repoPath) {
                    // The message is being edited, and the attachment will be in a temporary repo folder
                    params = '?repoPath=' + encodeURIComponent(scope.message.repoPath);
                } else {
                    // usual repo location
                    params = '?messageId=' + scope.message.id;
                }

                var fileName = encodeURIComponent(scope.attachment.fileName);
                scope.thumbnailUrl = "/rest/messages/attachments/thumb/" + fileName + params + "&size=" + scope.size;
                scope.fileUrl = "/rest/messages/attachments/file/" + fileName + params;
                scope.imageClass = "attachment-image size-" + scope.size;
                scope.deletable = attrs.deleteAttachment !== undefined;

                scope.deleteAttachment = function() {
                    if (scope.deletable) {
                        scope.deleteAttachment({ attachment: scope.attachment })
                    }
                }
            }
        };
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
                                top: (pixel[1] + 15) + 'px',
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
                showDetailsMenu: "@",
                showDetails:    "&"
            },
            link: function(scope, element, attrs) {
                scope.language = $rootScope.language;
                scope.format = scope.format || 'list';

                scope.featureCoordinates = [];

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
                }
            }
        };
    }])


    /****************************************************************
     * Adds a message details drop-down menu
     ****************************************************************/
    .directive('messageDetailsMenu', ['$rootScope', '$window', 'growl', 'MessageService',
        function ($rootScope, $window, growl, MessageService) {
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
                link: function(scope, element, attrs) {

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

                    
                    // Navigate to the message editor page
                    scope.edit = function() {
                        if (scope.dismissAction) {
                            scope.dismissAction();
                        }
                    };
                }
            }
        }]);

