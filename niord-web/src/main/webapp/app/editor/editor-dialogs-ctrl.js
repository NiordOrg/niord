
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
 * The Message Editor dialogs controllers
 */
angular.module('niord.editor')

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


    /*******************************************************************
     * Controller that handles the message Thumbnail dialog
     *******************************************************************/
    .controller('MessageComparisonDialogCtrl', ['$scope', '$timeout', 'growl', 'MessageService', 'message',
        function ($scope, $timeout, growl, MessageService, message) {
            'use strict';

            $scope.message = message;
            $scope.compareMessage = undefined;
            $scope.messageDiff = '';
            $scope.selectedHistory = [ ];

            /** Initialize the list of messages to compare **/
            $scope.init = function () {
                $scope.compareMessage = undefined;
                $scope.selectedHistory.length = 0;
                $scope.selectedHistory.push({ snapshot: angular.toJson(message) });
            };
            $scope.init();

            $scope.reference = {
                messageId : undefined
            };
            $scope.$watch("reference.messageId", function (messageId) {
                // Reset the message history
                $scope.init();

                // Fetch the message to compare with
                if (messageId && messageId.length > 0) {
                    MessageService.editableDetails(messageId)
                        .success(function (compareMessage) {
                            $scope.compareMessage = compareMessage;
                            if (compareMessage) {
                                // Add on position 0
                                $scope.selectedHistory.unshift({ snapshot: angular.toJson(compareMessage) });
                                $timeout($scope.compareHtml);
                            }
                        })
                        .error(function (data, status) {
                            growl.error("Error loading message (code: " + status + ")", {ttl: 5000})
                        })
                }
            }, true);


            /** Compares the HTML of the two messages **/
            $scope.compareHtml = function () {
                if ($scope.compareMessage == undefined) {
                    $scope.messageDiff = '';
                } else {
                    var msg1 = $('#message1').html();
                    var msg2 = $('#message2').html();
                    $scope.messageDiff = htmldiff(msg1, msg2);
                    $timeout(function () {
                        // Disable all links
                        $('#message-diff').find('*').removeAttr('href ng-click');
                    });
                }
            }
        }])



    /*******************************************************************
     * Controller that allows the user to format selected features as text
     *******************************************************************/
    .controller('FormatMessageLocationsDialogCtrl', ['$scope', '$rootScope', '$window', 'growl',
            'MessageService', 'featureCollection', 'lang',
        function ($scope, $rootScope, $window, growl,
              MessageService, featureCollection, lang) {
            'use strict';

            $scope.wmsLayerEnabled = $rootScope.wmsLayerEnabled;
            $scope.openSeaMapLayerEnabled = $rootScope.openSeaMapLayerEnabled;
            $scope.featureCollection = angular.copy(featureCollection);
            $scope.lang = lang;
            $scope.selectedFeatures = {
                type: 'FeatureCollection',
                features: [ ]
            };
            $scope.data = {
                result: ''
            };
            $scope.params = {
                template: 'list',
                format: 'dec'
            };
            $scope.formats = [
                { name : 'Decimal', value: 'dec' },
                { name : 'Seconds', value: 'sec' },
                { name : 'Navtex', value: 'navtex' }
            ];


            // Restore previous parameter settings
            if ($window.localStorage['formatLocationSettings']) {
                try {
                    angular.copy(angular.fromJson($window.localStorage['formatLocationSettings']), $scope.params);
                } catch (error) {
                }
            }

            /** Called when the feature selection changes */
            $scope.featureSelectionChanged = function () {
                $scope.selectedFeatures.features = $.grep($scope.featureCollection.features, function (feature) {
                    return feature.selected;
                });


                // Compute the result
                $scope.data.result = '';
                if ($scope.selectedFeatures.features.length > 0) {
                    MessageService.formatMessageGeometry($scope.selectedFeatures, $scope.lang, $scope.params.template, $scope.params.format)
                        .success(function (result) {
                            $scope.data.result = result;
                        })
                        .error (function (data, status) {
                            growl.error("Error formatting locations (code: " + status + ")", { ttl: 5000 })
                        });
                }
            };


            /** Called to select all or none of the features */
            $scope.selectFeatures = function (select) {
                angular.forEach($scope.featureCollection.features, function (feature) {
                    feature.selected = select;
                });
                $scope.featureSelectionChanged();
            };
            $scope.$watch("params", $scope.featureSelectionChanged, true);


            /** Called when Insert is clicked **/
            $scope.insert = function () {
                $window.localStorage['formatLocationSettings'] = angular.toJson($scope.params);
                $scope.$close($scope.data.result);
            };


            // Initial selection
            $scope.selectFeatures($scope.featureCollection.features.length == 1);
        }])

;

