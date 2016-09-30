
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
    .controller('MessageComparisonDialogCtrl', ['$scope', '$timeout', 'growl', 'MessageService', 'messageId1', 'messageId2',
        function ($scope, $timeout, growl, MessageService, messageId1, messageId2) {
            'use strict';

            $scope.message1 = undefined;
            $scope.message2 = undefined;

            // Contains a visual diff of the two message html representations
            $scope.messageDiff = '';

            // Contains the JSON data of message1 and message2 used for data comparison
            $scope.messageData = [ ];

            /** Initialize the list of messages to compare **/
            $scope.initData = function () {
                $scope.messageData.length = 0;
                if ($scope.message2) {
                    $scope.messageData.push({ snapshot: angular.toJson($scope.message2) });
                }
                if ($scope.message1) {
                    $scope.messageData.push({ snapshot: angular.toJson($scope.message1) });
                }

                $scope.messageDiff = '';
                if ($scope.message1 != undefined && $scope.message2 != undefined) {
                    $timeout(function () {
                        var msg1 = $('#message1').html();
                        var msg2 = $('#message2').html();
                        $scope.messageDiff = htmldiff(msg1, msg2);
                        $timeout(function () {
                            // Disable all links
                            $('#message-diff').find('*').removeAttr('href ng-click');
                        });
                    })
                }
            };

            $scope.reference1 = {
                messageId : messageId1
            };
            $scope.reference2 = {
                messageId : messageId2
            };

            $scope.$watch("reference1.messageId", function (messageId) {
                // Reset the message history
                $scope.message1 = undefined;
                $scope.initData();

                // Fetch the message to compare with
                if (messageId && messageId.length > 0) {
                    MessageService.details(messageId)
                        .success(function (message) {
                            $scope.message1 = message;
                            $scope.initData();
                        })
                        .error(function (data, status) {
                            growl.error("Error loading message (code: " + status + ")", {ttl: 5000})
                        })
                }
            }, true);

            $scope.$watch("reference2.messageId", function (messageId) {
                // Reset the message history
                $scope.message2 = undefined;
                $scope.initData();

                // Fetch the message to compare with
                if (messageId && messageId.length > 0) {
                    MessageService.details(messageId)
                        .success(function (message) {
                            $scope.message2 = message;
                            $scope.initData();
                        })
                        .error(function (data, status) {
                            growl.error("Error loading message (code: " + status + ")", {ttl: 5000})
                        })
                }
            }, true);

        }])



    /*******************************************************************
     * Controller that allows the user to format selected features as text
     *******************************************************************/
    .controller('FormatMessageLocationsDialogCtrl', ['$scope', '$rootScope', '$window', 'growl',
            'MessageService', 'message', 'partIndex', 'lang',
        function ($scope, $rootScope, $window, growl,
              MessageService, message, partIndex, lang) {
            'use strict';

            $scope.wmsLayerEnabled = $rootScope.wmsLayerEnabled;
            $scope.openSeaMapLayerEnabled = $rootScope.openSeaMapLayerEnabled;

            $scope.message = message;
            $scope.partIndexData = { partIndex: partIndex };
            $scope.lang = lang;

            $scope.featureCollection = {
                type: 'FeatureCollection',
                features: []
            };
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


            /** Called whenever the message part selection changes **/
            $scope.messagePartSelectionChanged = function (updateSelection) {
                var fc = message.parts[$scope.partIndexData.partIndex].geometry;
                $scope.featureCollection.features = angular.copy(fc.features);

                // Remove all buffered features
                $scope.featureCollection.features = $scope.featureCollection.features.filter(function (feature) {
                    return feature.properties['parentFeatureIds'] === undefined;
                });

                // Initial selection
                if ($scope.featureCollection.features.length == 1) {
                    $scope.featureCollection.features[0].selected = true;
                }

                // Update the current feature selection
                if (updateSelection) {
                    $scope.featureSelectionChanged();
                }
            };
            $scope.messagePartSelectionChanged(false);


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
        }])


    /*******************************************************************
     * Controller that allows the user to format selected time intervals as text
     *******************************************************************/
    .controller('FormatMessageTimeDialogCtrl', ['$scope', '$rootScope', '$window', 'growl',
        'DateIntervalService', 'dateIntervals', 'lang',
        function ($scope, $rootScope, $window, growl,
                  DateIntervalService, dateIntervals, lang) {
            'use strict';

            $scope.dateIntervals = angular.copy(dateIntervals);
            $scope.lang = lang;
            $scope.data = {
                result: ''
            };

            /** Called when the time selection changes */
            $scope.timeSelectionChanged = function () {

                // Compute the result
                $scope.data.result = '';

                if ($scope.dateIntervals.length == 0) {
                    $scope.data.result = DateIntervalService.translateDateInterval($scope.lang, null);
                } else {
                    angular.forEach($scope.dateIntervals, function (di) {
                        if (di.selected) {
                            $scope.data.result += DateIntervalService.translateDateInterval($scope.lang, di) + '<br>';
                        }
                    });
                }
            };


            /** Called to select all or none of the date intervals */
            $scope.selectTimeIntervals = function (select) {
                angular.forEach($scope.dateIntervals, function (di) {
                    di.selected = select;
                });
                $scope.timeSelectionChanged();
            };


            /** Called when Insert is clicked **/
            $scope.insert = function () {
                $scope.$close($scope.data.result);
            };


            // Initial selection
            $scope.selectTimeIntervals($scope.dateIntervals.length == 1);
        }])
;

