/*
 * Copyright 2017 Danish Maritime Authority.
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
 * The admin controllers.
 */
angular.module('niord.admin')


    /**
     * ********************************************************************************
     * MailsAdminCtrl
     * ********************************************************************************
     * Scheduled Mails Admin Controller
     * Controller for the Admin Scheduled Mails page
     */
    .controller('MailsAdminCtrl', ['$scope', 'growl', 'AdminMailsService',
        function ($scope, growl, AdminMailsService) {
            'use strict';

            $scope.params = {
                recipient: ''
            };

            $scope.searchResult = {
                data: [],
                size: 0,
                total: 0
            };


            /** Searches the scheduled mails */
            $scope.search = function() {
                AdminMailsService
                    .search($scope.params)
                    .success(function (searchResult) {
                        $scope.searchResult = searchResult;
                    });
            };

            $scope.$watch("params", $scope.search, true);

        }]);
