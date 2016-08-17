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
 * The home controller
 */
angular.module('niord.home')
    .controller('HomeCtrl', ['$scope', '$timeout', '$stateParams', 'MessageService',
        function ($scope, $timeout, $stateParams, MessageService) {
            'use strict';

            $scope.messageList = [];

            $scope.init = function () {

                // Load the published messages
                MessageService.publicMessages()
                    .success(function (messages) {
                        $scope.messageList.length = 0;
                        for (var x = 0; x < messages.length; x++) {
                            $scope.messageList.push(messages[x]);
                        }
                        $scope.totalMessageNo = messages.length;
                    });

                // If specified in the URL, show the given message details
                if ($stateParams.messageId) {
                    $timeout(function() { MessageService.detailsDialog($stateParams.messageId) });
                }
            };

        }]);
