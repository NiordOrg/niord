
/**
 * The Message Editor controller
 */
angular.module('niord.editor')

    /**
     * Main message editor controller
     */
    .controller('EditorCtrl', ['$scope', '$rootScope', '$stateParams', '$state', '$http', '$window', '$uibModal', 'growl',
            'MessageService', 'LangService', 'MapService', 'UploadFileService',
        function ($scope, $rootScope, $stateParams, $state, $http, $window, $uibModal, growl,
                  MessageService, LangService, MapService, UploadFileService) {
            'use strict';

            $scope.message = {
                status: 'DRAFT',
                descs: [],
                geometry: {
                    type: 'FeatureCollection',
                    features: []
                }
            };
            $scope.initId = $stateParams.id || '';

            $scope.editMode = {
                type: false,
                orig_info: false,
                id: false,
                title: false,
                references: false,
                time: false,
                areas: false,
                categories: false,
                positions: false,
                charts: false
            };

            $scope.messageSeries = [];

            /*****************************/
            /** Initialize the editor   **/
            /*****************************/

            // Used to ensure that description entities have various field
            function initDescField(desc) {
                desc.title = '';
                desc.description = '';
            }


            /** Called during initialization when the message has been loaded or instantiated */
            $scope.initMessage = function () {
                var msg = $scope.message;

                // Ensure that localized desc fields are defined for all languages
                LangService.checkDescs(msg, initDescField, undefined, $rootScope.modelLanguages);

                // Instantiate the feature collection from the message geometry
                if (!msg.geometry) {
                    msg.geometry = {
                        type: 'FeatureCollection',
                        features: []
                    }
                }
                $scope.serializeCoordinates();

                // Determine the message series for the current domain and message mainType
                $scope.messageSeries.length = 0;
                if ($rootScope.domain && $rootScope.domain.messageSeries) {
                    angular.forEach($rootScope.domain.messageSeries, function (series) {
                        if (series.mainType == msg.mainType) {
                            if (msg.messageSeries && msg.messageSeries.seriesId == series.seriesId) {
                                $scope.messageSeries.push(msg.messageSeries);
                            } else {
                                $scope.messageSeries.push(series);
                            }
                        }
                    });
                }
            };


            /** Initialize the message editor */
            $scope.init = function () {
                // 1) The message ID may be specified
                if ($scope.initId) {
                    MessageService.details($scope.initId)
                        .success(function (message) {
                            $scope.message = message;
                            $scope.initMessage();
                        });

                } else {
                    // 2) The editor may be based on a template message, say, from the AtoN selection page
                    if ($state.includes('editor.template')) {
                        angular.copy($rootScope.templateMessage, $scope.message);
                    }
                    $scope.initMessage();
                }
            };


            /*****************************/
            /** Editor functionality    **/
            /*****************************/


            /** Returns if the current user in the current domain can create messages of the given mainType **/
            $scope.canCreateMessage = function (mainType) {
                return $rootScope.supportsMainType(mainType) && $rootScope.hasRole('editor');
            };


            /** Create a message of the given mainType and AtoN selection **/
            $scope.createMessage = function (mainType) {
                if (!$scope.canCreateMessage(mainType)) {
                    return;
                }

                $scope.message = {
                    mainType: mainType,
                    status: 'DRAFT',
                    descs: [],
                    geometry: {
                        type: 'FeatureCollection',
                        features: []
                    }
                };
                $scope.initMessage();
            };


            /** Returns if the given message field (group) is valid **/
            $scope.fieldValid = function(fieldId) {
                var msg = $scope.message;
                switch (fieldId) {
                    case 'type':
                        return msg.mainType && msg.type;
                    case 'id':
                        return msg.messageSeries !== undefined;
                    case 'time':
                        return msg.dateIntervals && msg.dateIntervals.length > 0;
                    case 'areas':
                        return msg.areas && msg.areas.length > 0;
                }
                return true;
            };


            /** Called when a message reference is clicked **/
            $scope.referenceClicked = function(messageId) {
                MessageService.detailsDialog(messageId);
            };


            // Use for charts selection
            $scope.charts = [];
            $scope.refreshCharts = function(name) {
                if (!name || name.length == 0) {
                    return [];
                }
                return $http.get(
                    '/rest/charts/search?name=' + encodeURIComponent(name) + '&lang=' + $rootScope.language + '&limit=10'
                ).then(function(response) {
                    $scope.charts = response.data;
                });
            };


            /** Computes the charts intersecting with the current message geometry **/
            $scope.computeCharts = function () {
                if ($scope.message.geometry.features.length > 0) {
                    MessageService.intersectingCharts($scope.message.geometry)
                        .success(function (charts) {
                            // Prune the returned chart data
                            $scope.message.charts = charts.map(function (chart) {
                                return {
                                    chartNumber: chart.chartNumber,
                                    internationalNumber: chart.internationalNumber
                                }
                            });
                        });
                }
            };


            // Use for area selection
            $scope.areas = [];
            $scope.refreshAreas = function(name) {
                if (!name || name.length == 0) {
                    return [];
                }
                return $http.get(
                    '/rest/areas/search?name=' + encodeURIComponent(name) +
                    '&domain=true'+
                    '&lang=' + $rootScope.language +
                    '&limit=10'
                ).then(function(response) {
                    $scope.areas = response.data;
                });
            };


            /** Copies the locations from the selected area to the message **/
            $scope.copyAreaLocations = function() {
                var msg = $scope.message;
                if (msg.areas) {
                    var areaIds = msg.areas.map(function (a) { return a.id;  }).join(",");
                    $http.get('/rest/areas/search/' + areaIds)
                        .success(function (areas) {
                            angular.forEach(areas, function (area) {
                                if (area.geometry) {
                                    var feature = {
                                        type: 'Feature',
                                        geometry: area.geometry,
                                        properties: { areaId: area.id }
                                    };
                                    angular.forEach(area.descs, function (desc) {
                                        feature.properties['name:' + desc.lang] = desc.name;
                                    });
                                    msg.geometry.features.push(feature);
                                    $scope.geometrySaved(msg.geometry);
                                }
                            });
                        });
                }
            };

            // Use for category selection
            $scope.categories = [];
            $scope.refreshCategories = function(name) {
                if (!name || name.length == 0) {
                    return [];
                }
                return $http.get(
                    '/rest/categories/search?name=' + encodeURIComponent(name) +
                    '&domain=true' +
                    '&lang=' + $rootScope.language +
                    '&limit=10'
                ).then(function(response) {
                    $scope.categories = response.data;
                });
            };


            // Recursively formats the names of the parent lineage for areas and categories
            $scope.formatParents = function(child) {
                var txt = undefined;
                if (child) {
                    txt = (child.descs && child.descs.length > 0) ? child.descs[0].name : 'N/A';
                    if (child.parent) {
                        txt = $scope.formatParents(child.parent) + " - " + txt;
                    }
                }
                return txt;
            };


            $scope.showCoordinates = false;
            $scope.toggleShowCoordinates = function () {
                $scope.showCoordinates = !$scope.showCoordinates;
            };


            /** Serializes the message coordinates **/
            $scope.featureCoordinates = [];
            $scope.serializeCoordinates = function () {
                // Compute on-demand
                var msg = $scope.message;
                $scope.featureCoordinates.length = 0;
                if (msg.geometry.features.length > 0) {
                    var index = 1;
                    angular.forEach(msg.geometry.features, function (feature) {
                        var coords = [];
                        MapService.serializeCoordinates(feature, coords);
                        if (coords.length > 0) {
                            var name = feature.properties ? feature.properties['name:' + $rootScope.language] : undefined;
                            $scope.featureCoordinates.push({
                                coords: coords,
                                startIndex: index,
                                name: name
                            });
                            index += coords.length;
                        }
                    });
                }
                return $scope.featureCoordinates;
            };

            /** called when the message geometry has been changed **/
            $scope.geometrySaved = function () {
                $scope.serializeCoordinates();
            };

            
            /** Returns if the current page matches the given page url **/
            $scope.onPage = function (page) {
                return $state.includes(page);
            };

            /*****************************/
            /** Action menu functions   **/
            /*****************************/


            /** Expands or collapses all field editors **/
            $scope.expandCollapseFields = function (expand) {
                angular.forEach($scope.editMode, function (value, key) {
                    if (value !== expand) {
                        $scope.editMode[key] = expand;
                    }
                });
            };


            /** Opens the message print dialog */
            $scope.pdf = function () {
                if ($scope.message.id) {
                    MessageService.messagePrintDialog(1).result
                        .then($scope.generatePdf);
                }
            };


            /** Download the PDF for the current message */
            $scope.generatePdf = function (printSettings) {
                MessageService.pdfTicket()
                    .success(function (ticket) {

                        var params = 'lang=' + $rootScope.language + '&ticket=' + ticket;

                        if (printSettings && printSettings.pageOrientation) {
                            params += '&pageOrientation=' + printSettings.pageOrientation;
                        }
                        if (printSettings && printSettings.pageSize) {
                            params += '&pageSize=' + printSettings.pageSize;
                        }

                        $window.location = '/rest/messages/message/' + $scope.message.id + '.pdf?' + params;
                    });
            };


            /*****************************/
            /** Thumbnails handling     **/
            /*****************************/


            $scope.openThumbnail = function () {
                window.open('/rest/message-map-image/' + $stateParams.id + '.png');
            };


            /** Opens the message thumbnail dialog */
            $scope.messageThumbnailDialog = function () {
                $uibModal.open({
                    controller: "MessageThumbnailDialogCtrl",
                    templateUrl: "/app/editor/message-thumbnail-dialog.html",
                    size: 'sm',
                    resolve: {
                        message: function () { return $scope.message; }
                    }
                }).result.then(function (image) {
                    if (image && $scope.message.id) {
                        MessageService.changeMessageMapImage($scope.message.id, image)
                            .success(function () {
                                growl.info("Updated message thumbnail", { ttl: 3000 })
                            });
                    }
                });
            };


            /** Opens the upload-charts dialog **/
            $scope.uploadMessageThumbnailDialog = function () {
                if ($scope.message.id) {
                    UploadFileService.showUploadFileDialog(
                        'Upload thumbnail image',
                        '/rest/message-map-image/' + $scope.message.id,
                        'png,jpg,jpeg,gif');
                }
            };


            /** Clears the current message thumbnail **/
            $scope.clearMessageThumbnail = function () {
                if ($scope.message.id) {
                    MessageService.deleteMessageMapImage($scope.message.id)
                        .success(function () {
                            growl.info("Deleted message thumbnail", { ttl: 3000 })
                        });
                }
            };


            /*****************************/
            /** Bootstrap editor        **/
            /*****************************/

            $scope.init();

        }])



    /*******************************************************************
     * EditorCtrl sub-controller that handles message status changes.
     *******************************************************************/
    .controller('EditorStatusCtrl', ['$scope', '$rootScope', 'growl', 'MessageService', 'DialogService',
        function ($scope, $rootScope, growl, MessageService, DialogService) {
            'use strict';

            $scope.reloadMessage = function (msg) {
                // Call parent controller init() method
                $scope.init();
                growl.info(msg, {ttl: 3000});
            };


            /** Publish the message **/
            $scope.publish = function () {

                // First check that the message is valid
                if ($scope.message.status != 'VERIFIED') {
                    growl.error("Only verified draft messages can be published", {ttl: 5000});
                    return;
                }

                // TODO: MANY more checks

                DialogService.showConfirmDialog(
                    "Publish Message?", "Publish Message?")
                    .then(function() {
                        MessageService.updateMessageStatus($scope.message, 'PUBLISHED')
                            .success(function() { $scope.reloadMessage("Message published"); })
                            .error(function(err) {
                                growl.error("Publishing failed\n" + err, {ttl: 5000});
                            });
                    });
            };


            /** Delete the draft message **/
            $scope.delete = function () {
                if ($scope.message.status != 'DRAFT' && $scope.message.status != 'VERIFIED') {
                    growl.error("Only draft and verified messages can be deleted", {ttl: 5000});
                    return;
                }

                DialogService.showConfirmDialog(
                    "Delete draft?", "Delete draft?")
                    .then(function() {
                        MessageService.updateMessageStatus($scope.message, 'DELETED')
                            .success(function() { $scope.reloadMessage("Message deleted"); })
                            .error(function(err) {
                                growl.error("Deletion failed\n" + err, {ttl: 5000});
                            });
                    });
            };


            /** Copy the message **/
            $scope.copy = function () {
                DialogService.showConfirmDialog(
                    "Copy Message?", "Copy Message?")
                    .then(function() {
                        $rootScope.go('/editor/edit/copy/' + $scope.message.id + "/REFERENCE");
                    });
            };


            /** Cancel the message **/
            $scope.cancel = function () {
                if ($scope.message.status != 'PUBLISHED') {
                    growl.error("Only published messages can be cancelled", {ttl: 5000});
                    return;
                }

                var modalOptions = {
                    closeButtonText: 'Cancel',
                    actionButtonText: 'Confirm Cancellation',
                    headerText: 'Cancel Message',
                    cancelOptions: { createCancelMessage: true },
                    templateUrl: "cancelMessage.html"
                };

                DialogService.showDialog({}, modalOptions)
                    .then(function () {
                        MessageService.updateMessageStatus($scope.message, 'CANCELLED')
                            .success(function() {
                                if (modalOptions.cancelOptions.createCancelMessage) {
                                    $rootScope.go('/editor/edit/copy/' + $scope.message.id + "/CANCELLATION");
                                } else {
                                    $scope.reloadMessage("Message cancelled");
                                }
                            })
                            .error(function(err) {
                                growl.error("Cancellation failed\n" + err, {ttl: 5000});
                            });
                    });
            };


        }])



    /*******************************************************************
     * EditorCtrl sub-controller that handles message history.
     *******************************************************************/
    .controller('EditorHistoryCtrl', ['$scope', '$rootScope', '$timeout', 'MessageService',
        function ($scope, $rootScope, $timeout, MessageService) {
            'use strict';

            $scope.messageHistory = [];
            $scope.selectedHistory = [];

            /** Loads the message history **/
            $scope.loadHistory = function () {
                if ($scope.message.id) {
                    MessageService.messageHistory($scope.message.id)
                        .success(function (history) {
                            $scope.messageHistory.length = 0;
                            angular.forEach(history, function (hist) {
                                hist.selected = false;
                                $scope.messageHistory.push(hist);
                            })
                        });
                }
            };

            // Load the message history (after the message has been loaded)
            $timeout($scope.loadHistory, 200);


            /** updates the history selection **/
            $scope.updateSelection = function () {
                $scope.selectedHistory.length = 0;
                angular.forEach($scope.messageHistory, function (hist) {
                    if (hist.selected) {
                        $scope.selectedHistory.unshift(hist);
                    }
                });
            }

        }])



    /*******************************************************************
     * Controller that handles the message Thumbnail dialog
     *******************************************************************/
    .controller('MessageThumbnailDialogCtrl', ['$scope', '$rootScope', 'message',
        function ($scope, $rootScope, message) {
            'use strict';

            $scope.messageList = [ message ];
            $scope.switcherLayers = [];
            $scope.showStdOSMLayer = $rootScope.osmSourceUrl && $rootScope.osmSourceUrl.length > 0;
            $scope.mapImageSize = $rootScope.mapImageSize || 256;

            /** Takes a thumbnail */
            $scope.thumbnail = function () {
                // Broadcast a take-thumbnail event to the message-thumbnail-layer.
                $scope.$broadcast('take-thumbnail', {});
            };


            /** Called when the thumbnail has been generated **/
            $scope.thumbnailGenerated = function (image) {
                $scope.$close(image);
            };


            $scope.updateVisibility = function (l) {
                l.layer.setVisible(l.visible)
            };

        }])

        ;

