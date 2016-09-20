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
 * The Message Schedule controller
 */
angular.module('niord.schedule')

    /**
     * Main message schedule controller
     */
    .controller('ScheduleCtrl', ['$scope', '$rootScope', 'growl', 'MessageService',
        function ($scope, $rootScope, growl, MessageService) {
            'use strict';

            $scope.messageList = [];
            $scope.date = moment();
            $scope.state = {
                text: {
                    enabled: true,
                    query: ''
                },
                area: {
                    enabled: true,
                    areas: []
                }
            };
            $scope.domain = $rootScope.domain;


            /** Refreshes the list of published messages **/
            $scope.loadMessages = function () {
                var params = 'sortBy=AREA&sortOrder=ASC';

                var s = $scope.state;
                if (s.text.enabled) {
                    params += '&query=' + encodeURIComponent(s.text.query);
                }
                if (s.area.enabled) {
                    angular.forEach(s.area.areas, function (area) {
                        params += '&area=' + area.id;
                    })
                }

                MessageService.search(params)
                    .success(function (result) {
                        $scope.messageList = result.data;
                        $scope.checkGroupByArea(2);

                        // Fetch the schedule for the messages
                        $scope.loadSchedule();
                    });
            };

            // Monitor changes to the state
            $scope.$watch("state", $scope.loadMessages, true);


            /** Loads the schedule for the current date and list of messages **/
            $scope.loadSchedule = function () {
                var messageIds = [];
                angular.forEach($scope.messageList, function (message) {
                    message.editing = false;
                    message.schedule = [];
                    messageIds.push(message.id);
                });

                // TODO: Load schedule

            };

            // Monitor changes to the date
            $scope.$watch("date", $scope.loadSchedule, true);


            /** Enter edit mode for the given message **/
            $scope.edit = function (message) {
                message.editing = true;
            };


            /** Scans through the search result and marks all messages that should display an area head line **/
            $scope.checkGroupByArea = function (maxLevels) {
                maxLevels = maxLevels || 2;
                var lastAreaId = undefined;
                if ($scope.messageList && $scope.messageList.length > 0) {
                    for (var m = 0; m < $scope.messageList.length; m++) {
                        var msg = $scope.messageList[m];
                        if (msg.areas && msg.areas.length > 0) {
                            var msgArea = msg.areas[0];
                            var areas = [];
                            for (var area = msgArea; area !== undefined; area = area.parent) {
                                areas.unshift(area);
                            }
                            if (areas.length > 0) {
                                area = areas[Math.min(areas.length - 1, maxLevels - 1)];
                                if (!lastAreaId || area.id != lastAreaId) {
                                    lastAreaId = area.id;
                                    msg.areaHeading = area;
                                }
                            }
                        }
                    }
                }
            };
        }]);

